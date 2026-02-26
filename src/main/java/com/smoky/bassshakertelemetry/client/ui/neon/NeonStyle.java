package com.smoky.bassshakertelemetry.client.ui.neon;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class NeonStyle {
    private static final Gson GSON = new Gson();
    private static final ResourceLocation DEFAULT_STYLE = new ResourceLocation("bassshakertelemetry", "neon/neon_style.json");
    private static final ResourceLocation DEFAULT_ANIMATION = new ResourceLocation("bassshakertelemetry", "neon/neon_animation.json");

    public enum BundleSource {
        BUILT_IN,
        DISK_OVERRIDE,
        DISK_REMOTE
    }

    // Disk bundle roots (under the Minecraft instance config/ folder).
    // - override: user/exported bundle (manual)
    // - remote: auto-downloaded bundle (optional)
    private static final String DISK_BUNDLE_DIR_OVERRIDE = "bassshakertelemetry/ui_bundle";
    private static final String DISK_BUNDLE_DIR_REMOTE = "bassshakertelemetry/ui_bundle_remote";
    private static final Set<ResourceLocation> DISK_REGISTERED_TEXTURES = new HashSet<>();

    private static volatile NeonStyle instance = defaults();
    private static volatile BundleSource activeBundleSource = BundleSource.BUILT_IN;
    private static volatile Path activeDiskBundleRoot = null;

    public final int background;
    public final int panel;
    public final int text;
    public final int textDim;

    public final int primary;
    public final int primaryHover;
    public final int primaryPressed;
    public final int accent;
    public final int danger;

    public final int sliderTrack;
    public final int sliderFill;

    public final int toggleOn;
    public final int toggleOff;

    public final int radiusPx;
    public final int paddingPx;

    public final NeonAnimationTokens anim;

    // Optional: icon arrows for NeonCycleButton. If null, widgets render procedural arrows.
    public final ResourceLocation cycleArrowLeft;
    public final ResourceLocation cycleArrowRight;
    public final int cycleArrowSizePx;

    private NeonStyle(
            int background,
            int panel,
            int text,
            int textDim,
            int primary,
            int primaryHover,
            int primaryPressed,
            int accent,
            int danger,
            int sliderTrack,
            int sliderFill,
            int toggleOn,
            int toggleOff,
            int radiusPx,
            int paddingPx,
            NeonAnimationTokens anim,
            ResourceLocation cycleArrowLeft,
            ResourceLocation cycleArrowRight,
            int cycleArrowSizePx
    ) {
        this.background = background;
        this.panel = panel;
        this.text = text;
        this.textDim = textDim;
        this.primary = primary;
        this.primaryHover = primaryHover;
        this.primaryPressed = primaryPressed;
        this.accent = accent;
        this.danger = danger;
        this.sliderTrack = sliderTrack;
        this.sliderFill = sliderFill;
        this.toggleOn = toggleOn;
        this.toggleOff = toggleOff;
        this.radiusPx = radiusPx;
        this.paddingPx = paddingPx;

        this.anim = Objects.requireNonNullElse(anim, NeonAnimationTokens.defaults());
        this.cycleArrowLeft = cycleArrowLeft;
        this.cycleArrowRight = cycleArrowRight;
        this.cycleArrowSizePx = Math.max(8, cycleArrowSizePx);
    }

    public static NeonStyle get() {
        return instance;
    }

    public static BundleSource getActiveBundleSource() {
        return activeBundleSource;
    }

    public static Path getActiveDiskBundleRootOrNull() {
        return activeDiskBundleRoot;
    }

    /**
     * Best-effort reload from the on-disk UI bundle (under config/bassshakertelemetry/ui_bundle).
     * Returns true if the bundle was found and loaded.
     */
    public static boolean reloadFromDiskBundleIfPresent() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return false;

            var override = tryLoadFromDiskBundle(mc, BundleSource.DISK_OVERRIDE, diskOverrideRoot(), true);
            if (override != null) {
                setActive(override, BundleSource.DISK_OVERRIDE, diskOverrideRoot());
                return true;
            }

            var remote = tryLoadFromDiskBundle(mc, BundleSource.DISK_REMOTE, diskRemoteRoot(), true);
            if (remote != null) {
                setActive(remote, BundleSource.DISK_REMOTE, diskRemoteRoot());
                return true;
            }

            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void initClient() {
        // Best-effort: load once at client init. If anything fails, the defaults remain.
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            ResourceManager rm = mc.getResourceManager();
            if (rm == null) return;

            // Prefer disk bundle(s) if present.
            // Order: manual override -> auto-downloaded remote -> built-in.
            var override = tryLoadFromDiskBundle(mc, BundleSource.DISK_OVERRIDE, diskOverrideRoot(), false);
            if (override != null) {
                setActive(override, BundleSource.DISK_OVERRIDE, diskOverrideRoot());
                return;
            }

            var remote = tryLoadFromDiskBundle(mc, BundleSource.DISK_REMOTE, diskRemoteRoot(), false);
            if (remote != null) {
                setActive(remote, BundleSource.DISK_REMOTE, diskRemoteRoot());
                return;
            }

            reload(rm, DEFAULT_STYLE);
        } catch (Throwable ignored) {
        }
    }

    public static Path diskOverrideRoot() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        if (configDir == null) return null;
        return configDir.resolve(DISK_BUNDLE_DIR_OVERRIDE);
    }

    public static Path diskRemoteRoot() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        if (configDir == null) return null;
        return configDir.resolve(DISK_BUNDLE_DIR_REMOTE);
    }

    private static void setActive(NeonStyle style, BundleSource source, Path diskBundleRootOrNull) {
        instance = style;
        activeBundleSource = source;
        activeDiskBundleRoot = diskBundleRootOrNull;
    }

    private static NeonStyle tryLoadFromDiskBundle(Minecraft mc, BundleSource source, Path bundleRoot, boolean forceReloadTextures) {
        try {
            if (bundleRoot == null) return null;
            Path assetsRoot = bundleRoot.resolve("assets");
            Path stylePath = assetsRoot.resolve("bassshakertelemetry").resolve("neon").resolve("neon_style.json");
            if (!Files.isRegularFile(stylePath)) return null;

            if (forceReloadTextures) {
                DISK_REGISTERED_TEXTURES.clear();
            }

            Path animPath = assetsRoot.resolve("bassshakertelemetry").resolve("neon").resolve("neon_animation.json");
            NeonAnimationTokens anim = NeonAnimationTokens.loadOrDefaultFromDisk(animPath);

            try (var reader = Files.newBufferedReader(stylePath, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) return null;
                return parseFromDiskBundle(mc, assetsRoot, root, anim, forceReloadTextures);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NeonStyle parseFromDiskBundle(Minecraft mc, Path assetsRoot, JsonObject root, NeonAnimationTokens anim, boolean forceReloadTextures) {
        NeonStyle d = defaults();

        int background = NeonColorUtil.parseColorOrDefault(getString(root, "background"), d.background);
        int panel = NeonColorUtil.parseColorOrDefault(getString(root, "panel"), d.panel);
        int primary = NeonColorUtil.parseColorOrDefault(getString(root, "primary"), d.primary);
        int primaryHover = NeonColorUtil.parseColorOrDefault(getString(root, "primary_hover"), d.primaryHover);
        int primaryPressed = NeonColorUtil.parseColorOrDefault(getString(root, "primary_pressed"), d.primaryPressed);
        int accent = NeonColorUtil.parseColorOrDefault(getString(root, "accent"), d.accent);
        int danger = NeonColorUtil.parseColorOrDefault(getString(root, "danger"), d.danger);

        int radius = getInt(root, "radius", d.radiusPx);
        int padding = getInt(root, "padding", d.paddingPx);

        int sliderTrack = d.sliderTrack;
        int sliderFill = d.sliderFill;
        JsonObject slider = getObject(root, "slider");
        if (slider != null) {
            sliderTrack = NeonColorUtil.parseColorOrDefault(getString(slider, "track"), sliderTrack);
            sliderFill = NeonColorUtil.parseColorOrDefault(getString(slider, "fill"), sliderFill);
        }

        int toggleOn = d.toggleOn;
        int toggleOff = d.toggleOff;
        JsonObject toggle = getObject(root, "toggle");
        if (toggle != null) {
            toggleOn = NeonColorUtil.parseColorOrDefault(getString(toggle, "on"), toggleOn);
            toggleOff = NeonColorUtil.parseColorOrDefault(getString(toggle, "off"), toggleOff);
        }

        ResourceLocation cycleLeft = null;
        ResourceLocation cycleRight = null;
        int cycleArrowSize = Math.max(8, rowOr(d.cycleArrowSizePx, 12));
        JsonObject cycle = getObject(root, "cycle");
        if (cycle != null) {
            cycleArrowSize = getInt(cycle, "arrow_size", cycleArrowSize);
            cycleLeft = resolveDiskTexture(mc, assetsRoot, getString(cycle, "arrow_left"), "disk_bundle/cycle_arrow_left", forceReloadTextures);
            cycleRight = resolveDiskTexture(mc, assetsRoot, getString(cycle, "arrow_right"), "disk_bundle/cycle_arrow_right", forceReloadTextures);
        }

        return new NeonStyle(
                background,
                panel,
                d.text,
                d.textDim,
                primary,
                primaryHover,
                primaryPressed,
                accent,
                danger,
                sliderTrack,
                sliderFill,
                toggleOn,
                toggleOff,
                radius,
                padding,
                anim,
                cycleLeft,
                cycleRight,
                cycleArrowSize
        );
    }

    @SuppressWarnings("null")
    private static ResourceLocation resolveDiskTexture(Minecraft mc, Path assetsRoot, String resourceLocationString, String registeredIdPath, boolean forceReload) {
        if (resourceLocationString == null) return null;
        String t = resourceLocationString.trim();
        if (t.isEmpty()) return null;

        try {
            ResourceLocation src = new ResourceLocation(t);
            Path file = assetsRoot.resolve(src.getNamespace()).resolve(src.getPath());
            if (!Files.isRegularFile(file)) return null;

            ResourceLocation id = new ResourceLocation("bassshakertelemetry", registeredIdPath);
            if (!forceReload && DISK_REGISTERED_TEXTURES.contains(id)) return id;

            try (InputStream is = Files.newInputStream(file)) {
                NativeImage img = NativeImage.read(is);
                DynamicTexture tex = new DynamicTexture(img);
                mc.getTextureManager().register(id, tex);
                DISK_REGISTERED_TEXTURES.add(id);
                return id;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void reload(ResourceManager resourceManager, ResourceLocation location) {
        Objects.requireNonNull(resourceManager);
        Objects.requireNonNull(location);

        try {
            var opt = resourceManager.getResource(location);
            if (opt.isEmpty()) {
                setActive(defaults(), BundleSource.BUILT_IN, null);
                return;
            }

            Resource res = opt.get();
            NeonAnimationTokens anim = NeonAnimationTokens.loadOrDefault(resourceManager, DEFAULT_ANIMATION);

            try (var reader = new InputStreamReader(res.open(), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null) {
                    setActive(defaults(), BundleSource.BUILT_IN, null);
                    return;
                }
                setActive(parse(resourceManager, root, anim), BundleSource.BUILT_IN, null);
            }
        } catch (Exception ignored) {
            setActive(defaults(), BundleSource.BUILT_IN, null);
        }
    }

    private static NeonStyle parse(ResourceManager resourceManager, JsonObject root, NeonAnimationTokens anim) {
        NeonStyle d = defaults();

        int background = NeonColorUtil.parseColorOrDefault(getString(root, "background"), d.background);
        int panel = NeonColorUtil.parseColorOrDefault(getString(root, "panel"), d.panel);
        int primary = NeonColorUtil.parseColorOrDefault(getString(root, "primary"), d.primary);
        int primaryHover = NeonColorUtil.parseColorOrDefault(getString(root, "primary_hover"), d.primaryHover);
        int primaryPressed = NeonColorUtil.parseColorOrDefault(getString(root, "primary_pressed"), d.primaryPressed);
        int accent = NeonColorUtil.parseColorOrDefault(getString(root, "accent"), d.accent);
        int danger = NeonColorUtil.parseColorOrDefault(getString(root, "danger"), d.danger);

        int radius = getInt(root, "radius", d.radiusPx);
        int padding = getInt(root, "padding", d.paddingPx);

        int sliderTrack = d.sliderTrack;
        int sliderFill = d.sliderFill;
        JsonObject slider = getObject(root, "slider");
        if (slider != null) {
            sliderTrack = NeonColorUtil.parseColorOrDefault(getString(slider, "track"), sliderTrack);
            sliderFill = NeonColorUtil.parseColorOrDefault(getString(slider, "fill"), sliderFill);
        }

        int toggleOn = d.toggleOn;
        int toggleOff = d.toggleOff;
        JsonObject toggle = getObject(root, "toggle");
        if (toggle != null) {
            toggleOn = NeonColorUtil.parseColorOrDefault(getString(toggle, "on"), toggleOn);
            toggleOff = NeonColorUtil.parseColorOrDefault(getString(toggle, "off"), toggleOff);
        }

        ResourceLocation cycleLeft = null;
        ResourceLocation cycleRight = null;
        int cycleArrowSize = Math.max(8, rowOr(d.cycleArrowSizePx, 12));
        JsonObject cycle = getObject(root, "cycle");
        if (cycle != null) {
            cycleArrowSize = getInt(cycle, "arrow_size", cycleArrowSize);
            cycleLeft = resolveTexture(resourceManager, getString(cycle, "arrow_left"));
            cycleRight = resolveTexture(resourceManager, getString(cycle, "arrow_right"));
        }

        return new NeonStyle(
                background,
                panel,
                d.text,
                d.textDim,
                primary,
                primaryHover,
                primaryPressed,
                accent,
                danger,
                sliderTrack,
                sliderFill,
                toggleOn,
                toggleOff,
                radius,
            padding,
            anim,
            cycleLeft,
            cycleRight,
            cycleArrowSize
        );
    }

    private static NeonStyle defaults() {
        // Dark background with cyan primary.
        return new NeonStyle(
                0xFF05070A, // background
                0xFF0B1018, // panel
                0xFFFFFFFF, // text
                0xFFB6C0D0, // textDim
                0xFF00E0FF, // primary
                0xFF33ECFF, // primaryHover
                0xFF00B0CC, // primaryPressed
                0xFFFF8800, // accent
                0xFFFF3355, // danger
                0xFF202633, // sliderTrack
                0xFF00E0FF, // sliderFill
                0xFF00E0FF, // toggleOn
                0xFF3A3F4D, // toggleOff
                4,
                6,
                NeonAnimationTokens.defaults(),
                null,
                null,
                12
        );
    }

    private static int rowOr(int v, int fallback) {
        return v <= 0 ? fallback : v;
    }

    private static ResourceLocation resolveTexture(ResourceManager resourceManager, String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        try {
            ResourceLocation rl = new ResourceLocation(t);
            // Verify existence so we don't render missing texture placeholders.
            if (resourceManager.getResource(rl).isPresent()) {
                return rl;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        try {
            return e.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            return e.getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonObject()) return null;
        try {
            return e.getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }
}
