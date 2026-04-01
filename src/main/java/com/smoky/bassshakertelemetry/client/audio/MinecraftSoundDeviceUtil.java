package com.smoky.bassshakertelemetry.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.sounds.SoundManager;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Client-only helpers for interacting with Minecraft's own "game sounds" output device.
 *
 * This uses reflection so it can tolerate minor internal API changes across mappings.
 */
public final class MinecraftSoundDeviceUtil {
    private MinecraftSoundDeviceUtil() {
    }

    public static List<String> listAvailableOutputDevices() {
        List<String> fromMc = listAvailableFromMinecraft();
        if (!fromMc.isEmpty()) {
            return fromMc;
        }

        // Fallback: enumerate OpenAL devices directly.
        return listAvailableFromOpenAl();
    }

    public static String getSelectedSoundDeviceId() {
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;
        return readSoundDeviceFromOptions(options);
    }

    public static void applySelectedSoundDeviceId(String deviceIdOrEmptyForDefault) {
        Minecraft mc = Minecraft.getInstance();
        Options options = mc.options;

        writeSoundDeviceToOptions(options, deviceIdOrEmptyForDefault);
        tryInvokeNoArg(options, "save");

        SoundManager soundManager = mc.getSoundManager();
        // Try to reload the sound engine so the device switch applies immediately.
        if (!tryInvokeNoArg(soundManager, "reload")) {
            // Some versions may use different method names; best-effort only.
            tryInvokeNoArg(soundManager, "stop");
        }
    }

    private static List<String> listAvailableFromMinecraft() {
        try {
            Minecraft mc = Minecraft.getInstance();
            SoundManager soundManager = mc.getSoundManager();

            // Known / likely method names across versions.
            for (String name : List.of("getAvailableSoundDevices", "getAvailableAudioDevices", "getAvailableDevices")) {
                Object result = tryInvokeNoArgReturn(soundManager, name);
                if (result instanceof List<?> list && !list.isEmpty()) {
                    List<String> out = new ArrayList<>();
                    for (Object o : list) {
                        if (o instanceof String s && !s.isBlank()) {
                            out.add(s);
                        }
                    }
                    return dedupePreserveOrder(out);
                }
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    private static List<String> listAvailableFromOpenAl() {
        try {
            String devices = null;

            // Prefer the "all devices" extension if present.
            if (ALC10.alcIsExtensionPresent(0L, "ALC_ENUMERATE_ALL_EXT")) {
                devices = ALC10.alcGetString(0L, ALC11.ALC_ALL_DEVICES_SPECIFIER);
            }

            if (devices == null && ALC10.alcIsExtensionPresent(0L, "ALC_ENUMERATION_EXT")) {
                devices = ALC10.alcGetString(0L, ALC10.ALC_DEVICE_SPECIFIER);
            }

            if (devices == null) {
                // Last-ditch: may return only the default device.
                devices = ALC10.alcGetString(0L, ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);
            }

            if (devices == null || devices.isBlank()) {
                return List.of();
            }

            List<String> out = new ArrayList<>();
            for (String part : devices.split("\\0")) {
                if (part != null) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        out.add(trimmed);
                    }
                }
            }

            return dedupePreserveOrder(out);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static String readSoundDeviceFromOptions(Options options) {
        if (options == null) return null;

        Object fieldValue = readFieldValue(options, List.of("soundDevice", "soundDeviceOption"));
        if (fieldValue == null) return null;

        if (fieldValue instanceof String s) {
            return s;
        }

        // Most modern MC versions use OptionInstance<String>.
        Object maybeValue = tryInvokeNoArgReturn(fieldValue, "get");
        if (maybeValue instanceof String s) {
            return s;
        }

        Object valueField = readFieldValue(fieldValue, List.of("value"));
        if (valueField instanceof String s) {
            return s;
        }

        return null;
    }

    private static void writeSoundDeviceToOptions(Options options, String deviceIdOrEmptyForDefault) {
        if (options == null) return;
        String normalized = Objects.requireNonNullElse(deviceIdOrEmptyForDefault, "");

        Field f = findField(options.getClass(), List.of("soundDevice", "soundDeviceOption"));
        if (f == null) return;

        try {
            f.setAccessible(true);
            Object current = f.get(options);

            if (current instanceof String) {
                f.set(options, normalized);
                return;
            }

            if (current == null) {
                return;
            }

            // OptionInstance<String>: try set/setValue.
            if (!tryInvokeOneArg(current, "set", normalized)) {
                tryInvokeOneArg(current, "setValue", normalized);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object readFieldValue(Object target, List<String> fieldNames) {
        if (target == null) return null;
        Field f = findField(target.getClass(), fieldNames);
        if (f == null) return null;
        try {
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> clazz, List<String> candidates) {
        for (String name : candidates) {
            try {
                Field f = clazz.getDeclaredField(name);
                return f;
            } catch (Throwable ignored) {
            }
        }
        // Also try public field lookup.
        for (String name : candidates) {
            try {
                Field f = clazz.getField(name);
                return f;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static boolean tryInvokeNoArg(Object target, String methodName) {
        return tryInvokeNoArgReturn(target, methodName) != null;
    }

    private static Object tryInvokeNoArgReturn(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean tryInvokeOneArg(Object target, String methodName, Object arg) {
        if (target == null || methodName == null) return false;
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!Objects.equals(m.getName(), methodName)) continue;
                if (m.getParameterCount() != 1) continue;
                m.setAccessible(true);
                m.invoke(target, arg);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static List<String> dedupePreserveOrder(List<String> in) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String s : in) {
            if (s != null) {
                String t = s.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        return List.copyOf(set);
    }
}
