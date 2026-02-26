package com.smoky.bassshakertelemetry.client.ui.neon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.smoky.bassshakertelemetry.config.BstConfig;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Best-effort UI bundle auto-updater.
 *
 * - Default ON.
 * - Fetches the router repo's latest GitHub Release JSON.
 * - Downloads a named release asset zip and extracts into config/bassshakertelemetry/ui_bundle_remote/.
 * - Schedules a main-thread NeonStyle reload when done.
 */
public final class UiBundleAutoUpdater {
    private static final Logger LOGGER = LogManager.getLogger("bassshakertelemetry");
    private static final Gson GSON = new Gson();
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static final String META_FILE = "bundle_meta.json";

    private UiBundleAutoUpdater() {
    }

    public static void startIfEnabled() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        BstConfig.Data cfg = BstConfig.get();
        if (cfg == null || !cfg.uiBundleAutoUpdateEnabled) {
            return;
        }

        String repo = (cfg.uiBundleAutoUpdateRepo == null) ? "" : cfg.uiBundleAutoUpdateRepo.trim();
        String assetName = (cfg.uiBundleAutoUpdateAssetName == null) ? "" : cfg.uiBundleAutoUpdateAssetName.trim();
        if (repo.isEmpty() || assetName.isEmpty()) return;

        int timeoutMs = cfg.uiBundleUpdateTimeoutMs;
        CompletableFuture.runAsync(() -> checkAndUpdateFromGitHubReleases(repo, assetName, timeoutMs));
    }

    private static void checkAndUpdateFromGitHubReleases(String repo, String assetName, int timeoutMs) {
        try {
            Path remoteRoot = NeonStyle.diskRemoteRoot();
            if (remoteRoot == null) {
                return;
            }

            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            GitHubReleaseAsset latest = fetchLatestReleaseAsset(http, repo, assetName, timeoutMs);
            if (latest == null) return;

            long localReleaseId = readLocalReleaseId(remoteRoot.resolve(META_FILE));
            if (latest.releaseId <= localReleaseId) return;

            Files.createDirectories(remoteRoot);

            Path tempZip = Files.createTempFile("bst_ui_bundle_", ".zip");
            try {
                downloadToFile(http, latest.downloadUrl, timeoutMs, tempZip);
                applyZipBundle(remoteRoot, tempZip, latest.releaseId, latest.tagName, latest.publishedAtUtc, latest.assetUpdatedAtUtc);
            } finally {
                try {
                    Files.deleteIfExists(tempZip);
                } catch (IOException ignored) {
                }
            }

            // Schedule a reload on the main thread so any texture registration happens safely.
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(NeonStyle::reloadFromDiskBundleIfPresent);
            }
        } catch (Throwable t) {
            LOGGER.debug("[BST] UI bundle auto-update failed: {}", t.toString());
        }
    }

    private static void applyZipBundle(Path remoteRoot, Path zipPath, long releaseId, String tagName, String publishedAtUtc, String assetUpdatedAtUtc) throws IOException {
        Path parent = Objects.requireNonNullElse(remoteRoot.getParent(), remoteRoot);
        Path staging = parent.resolve(remoteRoot.getFileName().toString() + "_staging");

        deleteRecursive(staging);
        Files.createDirectories(staging);

        unzipInto(zipPath, staging);

        Path bundleRoot = detectBundleRoot(staging);
        if (bundleRoot == null) {
            deleteRecursive(staging);
            throw new IOException("Downloaded bundle missing neon_style.json");
        }

        // Swap bundleRoot -> remoteRoot.
        deleteRecursive(remoteRoot);
        Files.createDirectories(parent);
        Files.move(bundleRoot, remoteRoot, StandardCopyOption.REPLACE_EXISTING);

        // Clean up staging container (in case bundleRoot was nested).
        deleteRecursive(staging);

        // Write metadata.
        JsonObject meta = new JsonObject();
        meta.addProperty("release_id", releaseId);
        if (tagName != null) meta.addProperty("tag", tagName);
        if (publishedAtUtc != null) meta.addProperty("published_at_utc", publishedAtUtc);
        if (assetUpdatedAtUtc != null) meta.addProperty("asset_updated_at_utc", assetUpdatedAtUtc);
        meta.addProperty("downloaded_at_utc", Instant.now().toString());
        Files.writeString(remoteRoot.resolve(META_FILE), GSON.toJson(meta), StandardCharsets.UTF_8);

        LOGGER.info("[BST] UI bundle remote updated from GitHub release id={} tag={}", releaseId, tagName);
    }

    private static Path detectBundleRoot(Path staging) {
        // Expected layout: <root>/assets/bassshakertelemetry/neon/neon_style.json
        if (staging == null) return null;
        if (Files.isRegularFile(expectedStylePath(staging))) {
            return staging;
        }

        // Common case: zip wraps everything in one top-level directory.
        try (var stream = Files.list(staging)) {
            var dirs = stream.filter(Files::isDirectory).toList();
            if (dirs.size() == 1) {
                Path only = dirs.get(0);
                if (Files.isRegularFile(expectedStylePath(only))) {
                    return only;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static Path expectedStylePath(Path bundleRoot) {
        return bundleRoot.resolve("assets")
                .resolve("bassshakertelemetry")
                .resolve("neon")
                .resolve("neon_style.json");
    }

    private static JsonObject fetchJson(HttpClient http, String url, int timeoutMs) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", "bassshakertelemetry")
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() / 100 != 2) {
                return null;
            }
            return GSON.fromJson(resp.body(), JsonObject.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record GitHubReleaseAsset(long releaseId, String tagName, String publishedAtUtc, String assetUpdatedAtUtc, String downloadUrl) {
    }

    private static GitHubReleaseAsset fetchLatestReleaseAsset(HttpClient http, String repo, String assetName, int timeoutMs) {
        try {
            String[] parts = repo.split("/", 2);
            if (parts.length != 2) return null;
            String owner = parts[0].trim();
            String name = parts[1].trim();
            if (owner.isEmpty() || name.isEmpty()) return null;

            String api = "https://api.github.com/repos/" + owner + "/" + name + "/releases/latest";
            JsonObject release = fetchJson(http, api, timeoutMs);
            if (release == null) return null;

            long releaseId = getLong(release, "id", -1);
            if (releaseId < 0) return null;

            String tag = getString(release, "tag_name");
            String publishedAt = getString(release, "published_at");

            if (!release.has("assets") || !release.get("assets").isJsonArray()) {
                return null;
            }

            for (JsonElement el : release.getAsJsonArray("assets")) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject asset = el.getAsJsonObject();
                String n = getString(asset, "name");
                if (n == null) continue;
                if (!n.equals(assetName)) continue;

                String url = getString(asset, "browser_download_url");
                if (url == null || url.isBlank()) return null;
                String updatedAt = getString(asset, "updated_at");
                return new GitHubReleaseAsset(releaseId, tag, publishedAt, updatedAt, url);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void downloadToFile(HttpClient http, String url, int timeoutMs, Path out) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", "bassshakertelemetry")
                .GET()
                .build();

        HttpResponse<Path> resp = http.send(req, HttpResponse.BodyHandlers.ofFile(out));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Download failed: HTTP " + resp.statusCode());
        }
    }

    private static void unzipInto(Path zipPath, Path destDir) throws IOException {
        try (InputStream is = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }

                String name = e.getName();
                if (name == null || name.isBlank()) {
                    continue;
                }
                // Normalize separators and protect against Zip Slip.
                name = name.replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.contains("..\\")) {
                    continue;
                }

                Path outPath = destDir.resolve(name).normalize();
                if (!outPath.startsWith(destDir)) {
                    continue;
                }

                Files.createDirectories(Objects.requireNonNull(outPath.getParent()));
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static long readLocalReleaseId(Path metaPath) {
        try {
            if (!Files.isRegularFile(metaPath)) {
                return -1;
            }
            String json = Files.readString(metaPath, StandardCharsets.UTF_8);
            JsonObject meta = GSON.fromJson(json, JsonObject.class);
            if (meta == null) {
                return -1;
            }
            if (meta.has("release_id")) {
                return getLong(meta, "release_id", -1);
            }
            // legacy
            return getInt(meta, "version", -1);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String getString(JsonObject obj, String key) {
        try {
            if (obj == null || key == null) return null;
            var e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return null;
            return e.getAsString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        try {
            if (obj == null || key == null) return fallback;
            var e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return fallback;
            return e.getAsInt();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        try {
            if (obj == null || key == null) return fallback;
            var e = obj.get(key);
            if (e == null || !e.isJsonPrimitive()) return fallback;
            return e.getAsLong();
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
