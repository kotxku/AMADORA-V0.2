/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Property groups are plain Java maps; nothing on
 * iOS reads them, but Iris and Sodium create, fill and query them (window properties, dialog options).
 */
package org.lwjgl.sdl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class SDLProperties {
    private SDLProperties() {}

    private static final int TYPE_INVALID = 0, TYPE_POINTER = 1, TYPE_STRING = 2, TYPE_NUMBER = 3, TYPE_FLOAT = 4, TYPE_BOOLEAN = 5;
    private static final Map<Integer, Map<String, Object>> groups = new ConcurrentHashMap<>();
    private static final AtomicInteger nextId = new AtomicInteger(1);
    private static int global;

    // Values by type: Long pointer, String, long[1] number, Float, Boolean
    private static boolean set(int props, CharSequence name, Object value) {
        Map<String, Object> g = groups.get(props);
        if (g == null || name == null) return false;
        if (value == null) g.remove(name.toString());
        else g.put(name.toString(), value);
        return true;
    }

    private static Object get(int props, CharSequence name) {
        Map<String, Object> g = groups.get(props);
        return g == null || name == null ? null : g.get(name.toString());
    }

    public static synchronized int SDL_GetGlobalProperties() {
        if (global == 0) global = SDL_CreateProperties();
        return global;
    }

    public static int SDL_CreateProperties() {
        int id = nextId.getAndIncrement();
        groups.put(id, new ConcurrentHashMap<>());
        return id;
    }

    public static boolean SDL_CopyProperties(int src, int dst) {
        Map<String, Object> from = groups.get(src), to = groups.get(dst);
        if (from == null || to == null) return false;
        to.putAll(from);
        return true;
    }

    public static boolean SDL_LockProperties(int props) { return groups.containsKey(props); }
    public static void SDL_UnlockProperties(int props) {}

    public static boolean SDL_SetPointerProperty(int props, CharSequence name, long value) {
        return set(props, name, value == 0 ? null : Long.valueOf(value));
    }

    public static boolean SDL_SetStringProperty(int props, CharSequence name, CharSequence value) {
        return set(props, name, value == null ? null : value.toString());
    }

    public static boolean SDL_SetNumberProperty(int props, CharSequence name, long value) {
        return set(props, name, new long[]{value});
    }

    public static boolean SDL_SetFloatProperty(int props, CharSequence name, float value) {
        return set(props, name, value);
    }

    public static boolean SDL_SetBooleanProperty(int props, CharSequence name, boolean value) {
        return set(props, name, value);
    }

    public static boolean SDL_HasProperty(int props, CharSequence name) {
        return get(props, name) != null;
    }

    public static int SDL_GetPropertyType(int props, CharSequence name) {
        Object v = get(props, name);
        return v instanceof Long ? TYPE_POINTER : v instanceof String ? TYPE_STRING : v instanceof long[] ? TYPE_NUMBER
            : v instanceof Float ? TYPE_FLOAT : v instanceof Boolean ? TYPE_BOOLEAN : TYPE_INVALID;
    }

    public static long SDL_GetPointerProperty(int props, CharSequence name, long defaultValue) {
        Object v = get(props, name);
        return v instanceof Long ? (Long) v : defaultValue;
    }

    public static String SDL_GetStringProperty(int props, CharSequence name, CharSequence defaultValue) {
        Object v = get(props, name);
        if (v instanceof String) return (String) v;
        if (v instanceof long[]) return Long.toString(((long[]) v)[0]);
        if (v instanceof Float || v instanceof Boolean) return v.toString();
        return defaultValue == null ? null : defaultValue.toString();
    }

    public static long SDL_GetNumberProperty(int props, CharSequence name, long defaultValue) {
        Object v = get(props, name);
        if (v instanceof long[]) return ((long[]) v)[0];
        if (v instanceof Float) return (long) (float) (Float) v;
        if (v instanceof Boolean) return (Boolean) v ? 1 : 0;
        return defaultValue;
    }

    public static float SDL_GetFloatProperty(int props, CharSequence name, float defaultValue) {
        Object v = get(props, name);
        if (v instanceof Float) return (Float) v;
        if (v instanceof long[]) return ((long[]) v)[0];
        return defaultValue;
    }

    public static boolean SDL_GetBooleanProperty(int props, CharSequence name, boolean defaultValue) {
        Object v = get(props, name);
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof long[]) return ((long[]) v)[0] != 0;
        if (v instanceof Float) return (Float) v != 0f;
        return defaultValue;
    }

    public static boolean SDL_ClearProperty(int props, CharSequence name) {
        return set(props, name, null);
    }

    public static void SDL_DestroyProperties(int props) {
        if (props != global) groups.remove(props);
    }

    // ---- GENERATED by tools/sdl_bridge_gen/gen_sdl_fallbacks.py: do not edit below ----
    public static final int SDL_PROPERTY_TYPE_INVALID = 0;
    public static final int SDL_PROPERTY_TYPE_POINTER = 1;
    public static final int SDL_PROPERTY_TYPE_STRING = 2;
    public static final int SDL_PROPERTY_TYPE_NUMBER = 3;
    public static final int SDL_PROPERTY_TYPE_FLOAT = 4;
    public static final int SDL_PROPERTY_TYPE_BOOLEAN = 5;
    public static final java.lang.String SDL_PROP_NAME_STRING = "SDL.name";

    public static boolean nSDL_SetPointerPropertyWithCleanup(int p0, long p1, long p2, long p3, long p4) { AmadoraSDL.unsupported("nSDL_SetPointerPropertyWithCleanup"); return false; }
    public static boolean SDL_SetPointerPropertyWithCleanup(int p0, java.nio.ByteBuffer p1, long p2, org.lwjgl.sdl.SDL_CleanupPropertyCallbackI p3, long p4) { AmadoraSDL.unsupported("SDL_SetPointerPropertyWithCleanup"); return false; }
    public static boolean SDL_SetPointerPropertyWithCleanup(int p0, java.lang.CharSequence p1, long p2, org.lwjgl.sdl.SDL_CleanupPropertyCallbackI p3, long p4) { AmadoraSDL.unsupported("SDL_SetPointerPropertyWithCleanup"); return false; }
    public static boolean nSDL_SetPointerProperty(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_SetPointerProperty"); return false; }
    public static boolean SDL_SetPointerProperty(int p0, java.nio.ByteBuffer p1, long p2) { AmadoraSDL.unsupported("SDL_SetPointerProperty"); return false; }
    public static boolean nSDL_SetStringProperty(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_SetStringProperty"); return false; }
    public static boolean SDL_SetStringProperty(int p0, java.nio.ByteBuffer p1, java.nio.ByteBuffer p2) { AmadoraSDL.unsupported("SDL_SetStringProperty"); return false; }
    public static boolean nSDL_SetNumberProperty(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_SetNumberProperty"); return false; }
    public static boolean SDL_SetNumberProperty(int p0, java.nio.ByteBuffer p1, long p2) { AmadoraSDL.unsupported("SDL_SetNumberProperty"); return false; }
    public static boolean nSDL_SetFloatProperty(int p0, long p1, float p2) { AmadoraSDL.unsupported("nSDL_SetFloatProperty"); return false; }
    public static boolean SDL_SetFloatProperty(int p0, java.nio.ByteBuffer p1, float p2) { AmadoraSDL.unsupported("SDL_SetFloatProperty"); return false; }
    public static boolean nSDL_SetBooleanProperty(int p0, long p1, boolean p2) { AmadoraSDL.unsupported("nSDL_SetBooleanProperty"); return false; }
    public static boolean SDL_SetBooleanProperty(int p0, java.nio.ByteBuffer p1, boolean p2) { AmadoraSDL.unsupported("SDL_SetBooleanProperty"); return false; }
    public static boolean nSDL_HasProperty(int p0, long p1) { AmadoraSDL.unsupported("nSDL_HasProperty"); return false; }
    public static boolean SDL_HasProperty(int p0, java.nio.ByteBuffer p1) { AmadoraSDL.unsupported("SDL_HasProperty"); return false; }
    public static int nSDL_GetPropertyType(int p0, long p1) { AmadoraSDL.unsupported("nSDL_GetPropertyType"); return 0; }
    public static int SDL_GetPropertyType(int p0, java.nio.ByteBuffer p1) { AmadoraSDL.unsupported("SDL_GetPropertyType"); return 0; }
    public static long nSDL_GetPointerProperty(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetPointerProperty"); return 0L; }
    public static long SDL_GetPointerProperty(int p0, java.nio.ByteBuffer p1, long p2) { AmadoraSDL.unsupported("SDL_GetPointerProperty"); return 0L; }
    public static long nSDL_GetStringProperty(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetStringProperty"); return 0L; }
    public static java.lang.String SDL_GetStringProperty(int p0, java.nio.ByteBuffer p1, java.nio.ByteBuffer p2) { AmadoraSDL.unsupported("SDL_GetStringProperty"); return null; }
    public static long nSDL_GetNumberProperty(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetNumberProperty"); return 0L; }
    public static long SDL_GetNumberProperty(int p0, java.nio.ByteBuffer p1, long p2) { AmadoraSDL.unsupported("SDL_GetNumberProperty"); return 0L; }
    public static float nSDL_GetFloatProperty(int p0, long p1, float p2) { AmadoraSDL.unsupported("nSDL_GetFloatProperty"); return 0f; }
    public static float SDL_GetFloatProperty(int p0, java.nio.ByteBuffer p1, float p2) { AmadoraSDL.unsupported("SDL_GetFloatProperty"); return 0f; }
    public static boolean nSDL_GetBooleanProperty(int p0, long p1, boolean p2) { AmadoraSDL.unsupported("nSDL_GetBooleanProperty"); return false; }
    public static boolean SDL_GetBooleanProperty(int p0, java.nio.ByteBuffer p1, boolean p2) { AmadoraSDL.unsupported("SDL_GetBooleanProperty"); return false; }
    public static boolean nSDL_ClearProperty(int p0, long p1) { AmadoraSDL.unsupported("nSDL_ClearProperty"); return false; }
    public static boolean SDL_ClearProperty(int p0, java.nio.ByteBuffer p1) { AmadoraSDL.unsupported("SDL_ClearProperty"); return false; }
    public static boolean nSDL_EnumerateProperties(int p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_EnumerateProperties"); return false; }
    public static boolean SDL_EnumerateProperties(int p0, org.lwjgl.sdl.SDL_EnumeratePropertiesCallbackI p1, long p2) { AmadoraSDL.unsupported("SDL_EnumerateProperties"); return false; }
    // ---- END GENERATED ----
}
