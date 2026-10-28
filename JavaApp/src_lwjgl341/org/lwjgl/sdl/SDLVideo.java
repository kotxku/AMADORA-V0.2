/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.FunctionProvider;

import static org.lwjgl.system.MemoryUtil.*;

public final class SDLVideo {
    private SDLVideo() {}

    static final int GL_CONTEXT_MAJOR_VERSION = 17, GL_CONTEXT_MINOR_VERSION = 18;

    // SDL's window position macros (LWJGL computes the fields at class load, so the generator can't copy them)
    public static final int SDL_WINDOWPOS_UNDEFINED = 0x1FFF0000, SDL_WINDOWPOS_CENTERED = 0x2FFF0000;
    public static int SDL_WINDOWPOS_UNDEFINED_DISPLAY(int display) { return 0x1FFF0000 | display; }
    public static int SDL_WINDOWPOS_CENTERED_DISPLAY(int display) { return 0x2FFF0000 | display; }
    public static boolean SDL_WINDOWPOS_ISUNDEFINED(int pos) { return (pos & 0xFFFF0000) == 0x1FFF0000; }
    public static boolean SDL_WINDOWPOS_ISCENTERED(int pos) { return (pos & 0xFFFF0000) == 0x2FFF0000; }

    // --- Windows: every SDL window shares the one iOS surface

    public static long SDL_CreateWindow(CharSequence title, int w, int h, long flags) {
        return AmadoraSDL.createWindow(flags);
    }

    public static void SDL_DestroyWindow(long window) {
        AmadoraSDL.destroyWindow(window);
    }

    public static long SDL_GetWindowFlags(long window) {
        AmadoraSDL.Window w = AmadoraSDL.windows.get(window);
        return w == null ? 0 : w.flags | AmadoraSDL.WINDOW_INPUT_FOCUS | AmadoraSDL.WINDOW_MOUSE_FOCUS;
    }

    public static boolean SDL_SetWindowFullscreen(long window, boolean fullscreen) {
        AmadoraSDL.Window w = AmadoraSDL.windows.get(window);
        if (w != null) w.flags = fullscreen ? w.flags | AmadoraSDL.WINDOW_FULLSCREEN : w.flags & ~AmadoraSDL.WINDOW_FULLSCREEN;
        return true;
    }

    public static boolean SDL_SetWindowFullscreenMode(long window, SDL_DisplayMode mode) {
        return true;
    }

    public static SDL_DisplayMode SDL_GetWindowFullscreenMode(long window) {
        return null; // borderless desktop fullscreen
    }

    public static int SDL_GetWindowID(long window) {
        AmadoraSDL.Window w = AmadoraSDL.windows.get(window);
        return w == null ? 0 : w.id;
    }

    public static long SDL_GetWindowFromID(int id) {
        return AmadoraSDL.windowForId(id);
    }

    public static boolean SDL_GetWindowSize(long window, IntBuffer w, IntBuffer h) {
        return SDL_GetWindowSizeInPixels(window, w, h);
    }

    public static float SDL_GetWindowDisplayScale(long window) { return 1f; }
    public static float SDL_GetDisplayContentScale(int displayID) { return 1f; }

    // Empty except what a caller stores: iOS has no window handle a mod could use (Sodium only reads Win32/X11 ones)
    public static int SDL_GetWindowProperties(long window) {
        AmadoraSDL.Window w = AmadoraSDL.windows.get(window);
        if (w == null) return 0;
        if (w.properties == 0) w.properties = SDLProperties.SDL_CreateProperties();
        return w.properties;
    }

    // The iOS surface is always shown, focused and full screen; these just succeed
    public static boolean SDL_ShowWindow(long window) { return true; }
    public static boolean SDL_HideWindow(long window) { return true; }
    public static boolean SDL_RaiseWindow(long window) { return true; }
    public static boolean SDL_MaximizeWindow(long window) { return true; }
    public static boolean SDL_MinimizeWindow(long window) { return true; }
    public static boolean SDL_SetWindowResizable(long window, boolean resizable) { return true; }
    public static boolean SDL_SetWindowAlwaysOnTop(long window, boolean onTop) { return true; }
    public static boolean SDL_SetWindowFocusable(long window, boolean focusable) { return true; }
    public static boolean SDL_SetWindowKeyboardGrab(long window, boolean grabbed) { return true; }
    public static boolean SDL_DisableScreenSaver() { return true; }
    public static boolean SDL_EnableScreenSaver() { return true; }

    public static int SDL_GetNumVideoDrivers() { return 1; }
    public static String SDL_GetVideoDriver(int index) { return index == 0 ? SDL_GetCurrentVideoDriver() : null; }

    public static boolean SDL_SetWindowBordered(long window, boolean bordered) { return true; }
    public static boolean SDL_SetWindowIcon(long window, SDL_Surface icon) { return true; }
    public static boolean SDL_SetWindowMaximumSize(long window, int w, int h) { return true; }
    public static boolean SDL_SetWindowMinimumSize(long window, int w, int h) { return true; }
    public static boolean SDL_SetWindowMouseGrab(long window, boolean grabbed) { return true; }
    public static boolean SDL_SetWindowPosition(long window, int x, int y) { return true; }
    public static boolean SDL_SetWindowSize(long window, int w, int h) { return true; }
    public static boolean SDL_SetWindowTitle(long window, CharSequence title) { return true; }
    public static boolean SDL_RestoreWindow(long window) { return true; }
    public static boolean SDL_SyncWindow(long window) { return true; }

    public static boolean SDL_GetWindowPosition(long window, IntBuffer x, IntBuffer y) {
        if (x != null) x.put(x.position(), 0);
        if (y != null) y.put(y.position(), 0);
        return true;
    }

    public static boolean SDL_GetWindowSizeInPixels(long window, IntBuffer w, IntBuffer h) {
        int[] size = AmadoraSDL.framebufferSize();
        if (w != null) w.put(w.position(), size[0]);
        if (h != null) h.put(h.position(), size[1]);
        return true;
    }

    // Amethyst already reports the size in pixels, so window coordinates and pixels are the same
    public static float SDL_GetWindowPixelDensity(long window) {
        return 1f;
    }

    public static String SDL_GetCurrentVideoDriver() {
        return "uikit (AMADORA)";
    }

    // --- Displays: the iOS screen is the only one

    public static int SDL_GetPrimaryDisplay() {
        return AmadoraSDL.DISPLAY_ID;
    }

    public static IntBuffer SDL_GetDisplays() {
        IntBuffer displays = memAllocInt(1);
        displays.put(0, AmadoraSDL.DISPLAY_ID);
        return displays;
    }

    public static String SDL_GetDisplayName(int displayID) {
        return "iOS Display";
    }

    public static int SDL_GetDisplayForWindow(long window) {
        return AmadoraSDL.DISPLAY_ID;
    }

    public static boolean SDL_GetDisplayBounds(int displayID, SDL_Rect rect) {
        int[] size = AmadoraSDL.framebufferSize();
        long a = rect.address();
        memPutInt(a + SDL_Rect.X, 0);
        memPutInt(a + SDL_Rect.Y, 0);
        memPutInt(a + SDL_Rect.W, size[0]);
        memPutInt(a + SDL_Rect.H, size[1]);
        return true;
    }

    public static SDL_DisplayMode SDL_GetCurrentDisplayMode(int displayID) {
        return AmadoraSDL.displayMode();
    }

    public static SDL_DisplayMode SDL_GetDesktopDisplayMode(int displayID) {
        return AmadoraSDL.displayMode();
    }

    public static PointerBuffer SDL_GetFullscreenDisplayModes(int displayID) {
        PointerBuffer modes = memAllocPointer(1);
        modes.put(0, AmadoraSDL.displayMode().address());
        return modes;
    }

    public static boolean SDL_GetClosestFullscreenDisplayMode(int displayID, int w, int h, float refreshRate, boolean includeHighDensityModes, SDL_DisplayMode closest) {
        AmadoraSDL.fillDisplayMode(closest.address());
        return true;
    }

    // --- OpenGL, through Amethyst's renderer (the library LWJGL's GL class already loaded)

    public static boolean SDL_GL_LoadLibrary(CharSequence path) {
        return true;
    }

    public static void SDL_GL_UnloadLibrary() {}

    public static long SDL_GL_GetProcAddress(CharSequence proc) {
        FunctionProvider provider = GL.getFunctionProvider();
        return provider == null ? NULL : provider.getFunctionAddress(proc);
    }

    public static boolean SDL_GL_SetAttribute(int attr, int value) {
        AmadoraSDL.glAttributes.put(attr, value);
        return true;
    }

    public static boolean SDL_GL_GetAttribute(int attr, IntBuffer value) {
        Integer stored = AmadoraSDL.glAttributes.get(attr);
        int result = stored != null ? stored : attr == GL_CONTEXT_MAJOR_VERSION || attr == GL_CONTEXT_MINOR_VERSION ? 3 : 0;
        value.put(value.position(), result);
        return true;
    }

    public static long SDL_GL_CreateContext(long window) {
        return AmadoraSDL.createContext();
    }

    public static boolean SDL_GL_DestroyContext(long context) {
        return true;
    }

    public static boolean SDL_GL_MakeCurrent(long window, long context) {
        GLFW.glfwMakeContextCurrent(context);
        return true;
    }

    public static boolean SDL_GL_SetSwapInterval(int interval) {
        GLFW.glfwSwapInterval(interval);
        AmadoraSDL.swapInterval = interval;
        return true;
    }

    public static boolean SDL_GL_GetSwapInterval(IntBuffer interval) {
        interval.put(interval.position(), AmadoraSDL.swapInterval);
        return true;
    }

    public static long SDL_GL_GetCurrentContext() {
        return AmadoraSDL.context;
    }

    public static long SDL_GL_GetCurrentWindow() {
        return AmadoraSDL.context == 0 ? 0 : AmadoraSDL.eventWindow();
    }

    public static boolean SDL_GL_SwapWindow(long window) {
        if (AmadoraSDL.context != 0) GLFW.glfwSwapBuffers(AmadoraSDL.context);
        return true;
    }

    // ---- GENERATED by tools/sdl_bridge_gen/gen_sdl_fallbacks.py: do not edit below ----
    public static final java.lang.String SDL_PROP_GLOBAL_VIDEO_WAYLAND_WL_DISPLAY_POINTER = "SDL.video.wayland.wl_display";
    public static final int SDL_SYSTEM_THEME_UNKNOWN = 0;
    public static final int SDL_SYSTEM_THEME_LIGHT = 1;
    public static final int SDL_SYSTEM_THEME_DARK = 2;
    public static final int SDL_ORIENTATION_UNKNOWN = 0;
    public static final int SDL_ORIENTATION_LANDSCAPE = 1;
    public static final int SDL_ORIENTATION_LANDSCAPE_FLIPPED = 2;
    public static final int SDL_ORIENTATION_PORTRAIT = 3;
    public static final int SDL_ORIENTATION_PORTRAIT_FLIPPED = 4;
    public static final long SDL_WINDOW_FULLSCREEN = 1L;
    public static final long SDL_WINDOW_OPENGL = 2L;
    public static final long SDL_WINDOW_OCCLUDED = 4L;
    public static final long SDL_WINDOW_HIDDEN = 8L;
    public static final long SDL_WINDOW_BORDERLESS = 16L;
    public static final long SDL_WINDOW_RESIZABLE = 32L;
    public static final long SDL_WINDOW_MINIMIZED = 64L;
    public static final long SDL_WINDOW_MAXIMIZED = 128L;
    public static final long SDL_WINDOW_MOUSE_GRABBED = 256L;
    public static final long SDL_WINDOW_INPUT_FOCUS = 512L;
    public static final long SDL_WINDOW_MOUSE_FOCUS = 1024L;
    public static final long SDL_WINDOW_EXTERNAL = 2048L;
    public static final long SDL_WINDOW_MODAL = 4096L;
    public static final long SDL_WINDOW_HIGH_PIXEL_DENSITY = 8192L;
    public static final long SDL_WINDOW_MOUSE_CAPTURE = 16384L;
    public static final long SDL_WINDOW_MOUSE_RELATIVE_MODE = 32768L;
    public static final long SDL_WINDOW_ALWAYS_ON_TOP = 65536L;
    public static final long SDL_WINDOW_UTILITY = 131072L;
    public static final long SDL_WINDOW_TOOLTIP = 262144L;
    public static final long SDL_WINDOW_POPUP_MENU = 524288L;
    public static final long SDL_WINDOW_KEYBOARD_GRABBED = 1048576L;
    public static final long SDL_WINDOW_FILL_DOCUMENT = 2097152L;
    public static final long SDL_WINDOW_VULKAN = 268435456L;
    public static final long SDL_WINDOW_METAL = 536870912L;
    public static final long SDL_WINDOW_TRANSPARENT = 1073741824L;
    public static final long SDL_WINDOW_NOT_FOCUSABLE = 2147483648L;
    public static final int SDL_WINDOWPOS_UNDEFINED_MASK = 536805376;
    public static final int SDL_WINDOWPOS_CENTERED_MASK = 805240832;
    public static final int SDL_FLASH_CANCEL = 0;
    public static final int SDL_FLASH_BRIEFLY = 1;
    public static final int SDL_FLASH_UNTIL_FOCUSED = 2;
    public static final int SDL_PROGRESS_STATE_INVALID = -1;
    public static final int SDL_PROGRESS_STATE_NONE = 0;
    public static final int SDL_PROGRESS_STATE_INDETERMINATE = 1;
    public static final int SDL_PROGRESS_STATE_NORMAL = 2;
    public static final int SDL_PROGRESS_STATE_PAUSED = 3;
    public static final int SDL_PROGRESS_STATE_ERROR = 4;
    public static final int SDL_GL_RED_SIZE = 0;
    public static final int SDL_GL_GREEN_SIZE = 1;
    public static final int SDL_GL_BLUE_SIZE = 2;
    public static final int SDL_GL_ALPHA_SIZE = 3;
    public static final int SDL_GL_BUFFER_SIZE = 4;
    public static final int SDL_GL_DOUBLEBUFFER = 5;
    public static final int SDL_GL_DEPTH_SIZE = 6;
    public static final int SDL_GL_STENCIL_SIZE = 7;
    public static final int SDL_GL_ACCUM_RED_SIZE = 8;
    public static final int SDL_GL_ACCUM_GREEN_SIZE = 9;
    public static final int SDL_GL_ACCUM_BLUE_SIZE = 10;
    public static final int SDL_GL_ACCUM_ALPHA_SIZE = 11;
    public static final int SDL_GL_STEREO = 12;
    public static final int SDL_GL_MULTISAMPLEBUFFERS = 13;
    public static final int SDL_GL_MULTISAMPLESAMPLES = 14;
    public static final int SDL_GL_ACCELERATED_VISUAL = 15;
    public static final int SDL_GL_RETAINED_BACKING = 16;
    public static final int SDL_GL_CONTEXT_MAJOR_VERSION = 17;
    public static final int SDL_GL_CONTEXT_MINOR_VERSION = 18;
    public static final int SDL_GL_CONTEXT_FLAGS = 19;
    public static final int SDL_GL_CONTEXT_PROFILE_MASK = 20;
    public static final int SDL_GL_SHARE_WITH_CURRENT_CONTEXT = 21;
    public static final int SDL_GL_FRAMEBUFFER_SRGB_CAPABLE = 22;
    public static final int SDL_GL_CONTEXT_RELEASE_BEHAVIOR = 23;
    public static final int SDL_GL_CONTEXT_RESET_NOTIFICATION = 24;
    public static final int SDL_GL_CONTEXT_NO_ERROR = 25;
    public static final int SDL_GL_FLOATBUFFERS = 26;
    public static final int SDL_GL_EGL_PLATFORM = 27;
    public static final int SDL_GL_CONTEXT_PROFILE_CORE = 1;
    public static final int SDL_GL_CONTEXT_PROFILE_COMPATIBILITY = 2;
    public static final int SDL_GL_CONTEXT_PROFILE_ES = 4;
    public static final int SDL_GL_CONTEXT_DEBUG_FLAG = 1;
    public static final int SDL_GL_CONTEXT_FORWARD_COMPATIBLE_FLAG = 2;
    public static final int SDL_GL_CONTEXT_ROBUST_ACCESS_FLAG = 4;
    public static final int SDL_GL_CONTEXT_RESET_ISOLATION_FLAG = 8;
    public static final int SDL_GL_CONTEXT_RELEASE_BEHAVIOR_NONE = 0;
    public static final int SDL_GL_CONTEXT_RELEASE_BEHAVIOR_FLUSH = 1;
    public static final int SDL_GL_CONTEXT_RESET_NO_NOTIFICATION = 0;
    public static final int SDL_GL_CONTEXT_RESET_LOSE_CONTEXT = 1;
    public static final java.lang.String SDL_PROP_DISPLAY_HDR_ENABLED_BOOLEAN = "SDL.display.HDR_enabled";
    public static final java.lang.String SDL_PROP_DISPLAY_KMSDRM_PANEL_ORIENTATION_NUMBER = "SDL.display.KMSDRM.panel_orientation";
    public static final java.lang.String SDL_PROP_DISPLAY_WAYLAND_WL_OUTPUT_POINTER = "SDL.display.wayland.wl_output";
    public static final java.lang.String SDL_PROP_DISPLAY_WINDOWS_HMONITOR_POINTER = "SDL.display.windows.hmonitor";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_ALWAYS_ON_TOP_BOOLEAN = "SDL.window.create.always_on_top";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_BORDERLESS_BOOLEAN = "SDL.window.create.borderless";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_CONSTRAIN_POPUP_BOOLEAN = "SDL.window.create.constrain_popup";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_FOCUSABLE_BOOLEAN = "SDL.window.create.focusable";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_EXTERNAL_GRAPHICS_CONTEXT_BOOLEAN = "SDL.window.create.external_graphics_context";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_FLAGS_NUMBER = "SDL.window.create.flags";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_FULLSCREEN_BOOLEAN = "SDL.window.create.fullscreen";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER = "SDL.window.create.height";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_HIDDEN_BOOLEAN = "SDL.window.create.hidden";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_HIGH_PIXEL_DENSITY_BOOLEAN = "SDL.window.create.high_pixel_density";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_MAXIMIZED_BOOLEAN = "SDL.window.create.maximized";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_MENU_BOOLEAN = "SDL.window.create.menu";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_METAL_BOOLEAN = "SDL.window.create.metal";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_MINIMIZED_BOOLEAN = "SDL.window.create.minimized";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_MODAL_BOOLEAN = "SDL.window.create.modal";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_MOUSE_GRABBED_BOOLEAN = "SDL.window.create.mouse_grabbed";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_OPENGL_BOOLEAN = "SDL.window.create.opengl";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_PARENT_POINTER = "SDL.window.create.parent";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_RESIZABLE_BOOLEAN = "SDL.window.create.resizable";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_TITLE_STRING = "SDL.window.create.title";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_TRANSPARENT_BOOLEAN = "SDL.window.create.transparent";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_TOOLTIP_BOOLEAN = "SDL.window.create.tooltip";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_UTILITY_BOOLEAN = "SDL.window.create.utility";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_VULKAN_BOOLEAN = "SDL.window.create.vulkan";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER = "SDL.window.create.width";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_X_NUMBER = "SDL.window.create.x";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_Y_NUMBER = "SDL.window.create.y";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_COCOA_WINDOW_POINTER = "SDL.window.create.cocoa.window";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_COCOA_VIEW_POINTER = "SDL.window.create.cocoa.view";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WINDOWSCENE_POINTER = "SDL.window.create.uikit.windowscene";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WAYLAND_SURFACE_ROLE_CUSTOM_BOOLEAN = "SDL.window.create.wayland.surface_role_custom";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WAYLAND_CREATE_EGL_WINDOW_BOOLEAN = "SDL.window.create.wayland.create_egl_window";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WAYLAND_WL_SURFACE_POINTER = "SDL.window.create.wayland.wl_surface";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WIN32_HWND_POINTER = "SDL.window.create.win32.hwnd";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_WIN32_PIXEL_FORMAT_HWND_POINTER = "SDL.window.create.win32.pixel_format_hwnd";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_X11_WINDOW_NUMBER = "SDL.window.create.x11.window";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_EMSCRIPTEN_CANVAS_ID_STRING = "SDL.window.create.emscripten.canvas_id";
    public static final java.lang.String SDL_PROP_WINDOW_CREATE_EMSCRIPTEN_KEYBOARD_ELEMENT_STRING = "SDL.window.create.emscripten.keyboard_element";
    public static final java.lang.String SDL_PROP_WINDOW_SHAPE_POINTER = "SDL.window.shape";
    public static final java.lang.String SDL_PROP_WINDOW_HDR_ENABLED_BOOLEAN = "SDL.window.HDR_enabled";
    public static final java.lang.String SDL_PROP_WINDOW_SDR_WHITE_LEVEL_FLOAT = "SDL.window.SDR_white_level";
    public static final java.lang.String SDL_PROP_WINDOW_HDR_HEADROOM_FLOAT = "SDL.window.HDR_headroom";
    public static final java.lang.String SDL_PROP_WINDOW_ANDROID_WINDOW_POINTER = "SDL.window.android.window";
    public static final java.lang.String SDL_PROP_WINDOW_ANDROID_SURFACE_POINTER = "SDL.window.android.surface";
    public static final java.lang.String SDL_PROP_WINDOW_UIKIT_WINDOW_POINTER = "SDL.window.uikit.window";
    public static final java.lang.String SDL_PROP_WINDOW_UIKIT_METAL_VIEW_TAG_NUMBER = "SDL.window.uikit.metal_view_tag";
    public static final java.lang.String SDL_PROP_WINDOW_UIKIT_OPENGL_FRAMEBUFFER_NUMBER = "SDL.window.uikit.opengl.framebuffer";
    public static final java.lang.String SDL_PROP_WINDOW_UIKIT_OPENGL_RENDERBUFFER_NUMBER = "SDL.window.uikit.opengl.renderbuffer";
    public static final java.lang.String SDL_PROP_WINDOW_UIKIT_OPENGL_RESOLVE_FRAMEBUFFER_NUMBER = "SDL.window.uikit.opengl.resolve_framebuffer";
    public static final java.lang.String SDL_PROP_WINDOW_KMSDRM_DEVICE_INDEX_NUMBER = "SDL.window.kmsdrm.dev_index";
    public static final java.lang.String SDL_PROP_WINDOW_KMSDRM_DRM_FD_NUMBER = "SDL.window.kmsdrm.drm_fd";
    public static final java.lang.String SDL_PROP_WINDOW_KMSDRM_GBM_DEVICE_POINTER = "SDL.window.kmsdrm.gbm_dev";
    public static final java.lang.String SDL_PROP_WINDOW_COCOA_WINDOW_POINTER = "SDL.window.cocoa.window";
    public static final java.lang.String SDL_PROP_WINDOW_COCOA_METAL_VIEW_TAG_NUMBER = "SDL.window.cocoa.metal_view_tag";
    public static final java.lang.String SDL_PROP_WINDOW_OPENVR_OVERLAY_ID_NUMBER = "SDL.window.openvr.overlay_id";
    public static final java.lang.String SDL_PROP_WINDOW_VIVANTE_DISPLAY_POINTER = "SDL.window.vivante.display";
    public static final java.lang.String SDL_PROP_WINDOW_VIVANTE_WINDOW_POINTER = "SDL.window.vivante.window";
    public static final java.lang.String SDL_PROP_WINDOW_VIVANTE_SURFACE_POINTER = "SDL.window.vivante.surface";
    public static final java.lang.String SDL_PROP_WINDOW_WIN32_HWND_POINTER = "SDL.window.win32.hwnd";
    public static final java.lang.String SDL_PROP_WINDOW_WIN32_HDC_POINTER = "SDL.window.win32.hdc";
    public static final java.lang.String SDL_PROP_WINDOW_WIN32_INSTANCE_POINTER = "SDL.window.win32.instance";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_DISPLAY_POINTER = "SDL.window.wayland.display";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_SURFACE_POINTER = "SDL.window.wayland.surface";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_VIEWPORT_POINTER = "SDL.window.wayland.viewport";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_EGL_WINDOW_POINTER = "SDL.window.wayland.egl_window";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_XDG_SURFACE_POINTER = "SDL.window.wayland.xdg_surface";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_XDG_TOPLEVEL_POINTER = "SDL.window.wayland.xdg_toplevel";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_XDG_TOPLEVEL_EXPORT_HANDLE_STRING = "SDL.window.wayland.xdg_toplevel_export_handle";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_XDG_POPUP_POINTER = "SDL.window.wayland.xdg_popup";
    public static final java.lang.String SDL_PROP_WINDOW_WAYLAND_XDG_POSITIONER_POINTER = "SDL.window.wayland.xdg_positioner";
    public static final java.lang.String SDL_PROP_WINDOW_X11_DISPLAY_POINTER = "SDL.window.x11.display";
    public static final java.lang.String SDL_PROP_WINDOW_X11_SCREEN_NUMBER = "SDL.window.x11.screen";
    public static final java.lang.String SDL_PROP_WINDOW_X11_WINDOW_NUMBER = "SDL.window.x11.window";
    public static final java.lang.String SDL_PROP_WINDOW_EMSCRIPTEN_CANVAS_ID_STRING = "SDL.window.emscripten.canvas_id";
    public static final java.lang.String SDL_PROP_WINDOW_EMSCRIPTEN_KEYBOARD_ELEMENT_STRING = "SDL.window.emscripten.keyboard_element";
    public static final int SDL_WINDOW_SURFACE_VSYNC_DISABLED = 0;
    public static final int SDL_WINDOW_SURFACE_VSYNC_ADAPTIVE = -1;
    public static final int SDL_HITTEST_NORMAL = 0;
    public static final int SDL_HITTEST_DRAGGABLE = 1;
    public static final int SDL_HITTEST_RESIZE_TOPLEFT = 2;
    public static final int SDL_HITTEST_RESIZE_TOP = 3;
    public static final int SDL_HITTEST_RESIZE_TOPRIGHT = 4;
    public static final int SDL_HITTEST_RESIZE_RIGHT = 5;
    public static final int SDL_HITTEST_RESIZE_BOTTOMRIGHT = 6;
    public static final int SDL_HITTEST_RESIZE_BOTTOM = 7;
    public static final int SDL_HITTEST_RESIZE_BOTTOMLEFT = 8;
    public static final int SDL_HITTEST_RESIZE_LEFT = 9;

    public static long nSDL_GetVideoDriver(int p0) { AmadoraSDL.unsupported("nSDL_GetVideoDriver"); return 0L; }
    public static long nSDL_GetCurrentVideoDriver() { AmadoraSDL.unsupported("nSDL_GetCurrentVideoDriver"); return 0L; }
    public static int SDL_GetSystemTheme() { AmadoraSDL.unsupported("SDL_GetSystemTheme"); return 0; }
    public static long nSDL_GetDisplays(long p0) { AmadoraSDL.unsupported("nSDL_GetDisplays"); return 0L; }
    public static int SDL_GetDisplayProperties(int p0) { AmadoraSDL.unsupported("SDL_GetDisplayProperties"); return 0; }
    public static long nSDL_GetDisplayName(int p0) { AmadoraSDL.unsupported("nSDL_GetDisplayName"); return 0L; }
    public static boolean nSDL_GetDisplayBounds(int p0, long p1) { AmadoraSDL.unsupported("nSDL_GetDisplayBounds"); return false; }
    public static boolean nSDL_GetDisplayUsableBounds(int p0, long p1) { AmadoraSDL.unsupported("nSDL_GetDisplayUsableBounds"); return false; }
    public static boolean SDL_GetDisplayUsableBounds(int p0, org.lwjgl.sdl.SDL_Rect p1) { AmadoraSDL.unsupported("SDL_GetDisplayUsableBounds"); return false; }
    public static int SDL_GetNaturalDisplayOrientation(int p0) { AmadoraSDL.unsupported("SDL_GetNaturalDisplayOrientation"); return 0; }
    public static int SDL_GetCurrentDisplayOrientation(int p0) { AmadoraSDL.unsupported("SDL_GetCurrentDisplayOrientation"); return 0; }
    public static long nSDL_GetFullscreenDisplayModes(int p0, long p1) { AmadoraSDL.unsupported("nSDL_GetFullscreenDisplayModes"); return 0L; }
    public static boolean nSDL_GetClosestFullscreenDisplayMode(int p0, int p1, int p2, float p3, boolean p4, long p5) { AmadoraSDL.unsupported("nSDL_GetClosestFullscreenDisplayMode"); return false; }
    public static long nSDL_GetDesktopDisplayMode(int p0) { AmadoraSDL.unsupported("nSDL_GetDesktopDisplayMode"); return 0L; }
    public static long nSDL_GetCurrentDisplayMode(int p0) { AmadoraSDL.unsupported("nSDL_GetCurrentDisplayMode"); return 0L; }
    public static int nSDL_GetDisplayForPoint(long p0) { AmadoraSDL.unsupported("nSDL_GetDisplayForPoint"); return 0; }
    public static int SDL_GetDisplayForPoint(org.lwjgl.sdl.SDL_Point p0) { AmadoraSDL.unsupported("SDL_GetDisplayForPoint"); return 0; }
    public static int nSDL_GetDisplayForRect(long p0) { AmadoraSDL.unsupported("nSDL_GetDisplayForRect"); return 0; }
    public static int SDL_GetDisplayForRect(org.lwjgl.sdl.SDL_Rect p0) { AmadoraSDL.unsupported("SDL_GetDisplayForRect"); return 0; }
    public static boolean nSDL_SetWindowFullscreenMode(long p0, long p1) { AmadoraSDL.unsupported("nSDL_SetWindowFullscreenMode"); return false; }
    public static long nSDL_GetWindowFullscreenMode(long p0) { AmadoraSDL.unsupported("nSDL_GetWindowFullscreenMode"); return 0L; }
    public static long nSDL_GetWindowICCProfile(long p0, long p1) { AmadoraSDL.unsupported("nSDL_GetWindowICCProfile"); return 0L; }
    public static java.nio.ByteBuffer SDL_GetWindowICCProfile(long p0) { AmadoraSDL.unsupported("SDL_GetWindowICCProfile"); return null; }
    public static int SDL_GetWindowPixelFormat(long p0) { AmadoraSDL.unsupported("SDL_GetWindowPixelFormat"); return 0; }
    public static long nSDL_GetWindows(long p0) { AmadoraSDL.unsupported("nSDL_GetWindows"); return 0L; }
    public static org.lwjgl.PointerBuffer SDL_GetWindows() { AmadoraSDL.unsupported("SDL_GetWindows"); return null; }
    public static long nSDL_CreateWindow(long p0, int p1, int p2, long p3) { AmadoraSDL.unsupported("nSDL_CreateWindow"); return 0L; }
    public static long SDL_CreateWindow(java.nio.ByteBuffer p0, int p1, int p2, long p3) { AmadoraSDL.unsupported("SDL_CreateWindow"); return 0L; }
    public static long SDL_CreatePopupWindow(long p0, int p1, int p2, int p3, int p4, long p5) { AmadoraSDL.unsupported("SDL_CreatePopupWindow"); return 0L; }
    public static long SDL_CreateWindowWithProperties(int p0) { AmadoraSDL.unsupported("SDL_CreateWindowWithProperties"); return 0L; }
    public static long SDL_GetWindowParent(long p0) { AmadoraSDL.unsupported("SDL_GetWindowParent"); return 0L; }
    public static boolean nSDL_SetWindowTitle(long p0, long p1) { AmadoraSDL.unsupported("nSDL_SetWindowTitle"); return false; }
    public static boolean SDL_SetWindowTitle(long p0, java.nio.ByteBuffer p1) { AmadoraSDL.unsupported("SDL_SetWindowTitle"); return false; }
    public static long nSDL_GetWindowTitle(long p0) { AmadoraSDL.unsupported("nSDL_GetWindowTitle"); return 0L; }
    public static java.lang.String SDL_GetWindowTitle(long p0) { AmadoraSDL.unsupported("SDL_GetWindowTitle"); return null; }
    public static boolean nSDL_SetWindowIcon(long p0, long p1) { AmadoraSDL.unsupported("nSDL_SetWindowIcon"); return false; }
    public static boolean nSDL_GetWindowPosition(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetWindowPosition"); return false; }
    public static boolean nSDL_GetWindowSize(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetWindowSize"); return false; }
    public static boolean nSDL_GetWindowSafeArea(long p0, long p1) { AmadoraSDL.unsupported("nSDL_GetWindowSafeArea"); return false; }
    public static boolean SDL_GetWindowSafeArea(long p0, org.lwjgl.sdl.SDL_Rect p1) { AmadoraSDL.unsupported("SDL_GetWindowSafeArea"); return false; }
    public static boolean SDL_SetWindowAspectRatio(long p0, float p1, float p2) { AmadoraSDL.unsupported("SDL_SetWindowAspectRatio"); return false; }
    public static boolean nSDL_GetWindowAspectRatio(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetWindowAspectRatio"); return false; }
    public static boolean SDL_GetWindowAspectRatio(long p0, java.nio.FloatBuffer p1, java.nio.FloatBuffer p2) { AmadoraSDL.unsupported("SDL_GetWindowAspectRatio"); return false; }
    public static boolean nSDL_GetWindowBordersSize(long p0, long p1, long p2, long p3, long p4) { AmadoraSDL.unsupported("nSDL_GetWindowBordersSize"); return false; }
    public static boolean SDL_GetWindowBordersSize(long p0, java.nio.IntBuffer p1, java.nio.IntBuffer p2, java.nio.IntBuffer p3, java.nio.IntBuffer p4) { AmadoraSDL.unsupported("SDL_GetWindowBordersSize"); return false; }
    public static boolean nSDL_GetWindowSizeInPixels(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetWindowSizeInPixels"); return false; }
    public static boolean nSDL_GetWindowMinimumSize(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetWindowMinimumSize"); return false; }
    public static boolean SDL_GetWindowMinimumSize(long p0, java.nio.IntBuffer p1, java.nio.IntBuffer p2) { AmadoraSDL.unsupported("SDL_GetWindowMinimumSize"); return false; }
    public static boolean nSDL_GetWindowMaximumSize(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_GetWindowMaximumSize"); return false; }
    public static boolean SDL_GetWindowMaximumSize(long p0, java.nio.IntBuffer p1, java.nio.IntBuffer p2) { AmadoraSDL.unsupported("SDL_GetWindowMaximumSize"); return false; }
    public static boolean SDL_SetWindowFillDocument(long p0, boolean p1) { AmadoraSDL.unsupported("SDL_SetWindowFillDocument"); return false; }
    public static boolean SDL_WindowHasSurface(long p0) { AmadoraSDL.unsupported("SDL_WindowHasSurface"); return false; }
    public static long nSDL_GetWindowSurface(long p0) { AmadoraSDL.unsupported("nSDL_GetWindowSurface"); return 0L; }
    public static org.lwjgl.sdl.SDL_Surface SDL_GetWindowSurface(long p0) { AmadoraSDL.unsupported("SDL_GetWindowSurface"); return null; }
    public static boolean SDL_SetWindowSurfaceVSync(long p0, int p1) { AmadoraSDL.unsupported("SDL_SetWindowSurfaceVSync"); return false; }
    public static boolean nSDL_GetWindowSurfaceVSync(long p0, long p1) { AmadoraSDL.unsupported("nSDL_GetWindowSurfaceVSync"); return false; }
    public static boolean SDL_GetWindowSurfaceVSync(long p0, java.nio.IntBuffer p1) { AmadoraSDL.unsupported("SDL_GetWindowSurfaceVSync"); return false; }
    public static boolean SDL_UpdateWindowSurface(long p0) { AmadoraSDL.unsupported("SDL_UpdateWindowSurface"); return false; }
    public static boolean nSDL_UpdateWindowSurfaceRects(long p0, long p1, int p2) { AmadoraSDL.unsupported("nSDL_UpdateWindowSurfaceRects"); return false; }
    public static boolean SDL_UpdateWindowSurfaceRects(long p0, org.lwjgl.sdl.SDL_Rect.Buffer p1) { AmadoraSDL.unsupported("SDL_UpdateWindowSurfaceRects"); return false; }
    public static boolean SDL_DestroyWindowSurface(long p0) { AmadoraSDL.unsupported("SDL_DestroyWindowSurface"); return false; }
    public static boolean SDL_GetWindowKeyboardGrab(long p0) { AmadoraSDL.unsupported("SDL_GetWindowKeyboardGrab"); return false; }
    public static boolean SDL_GetWindowMouseGrab(long p0) { AmadoraSDL.unsupported("SDL_GetWindowMouseGrab"); return false; }
    public static long SDL_GetGrabbedWindow() { AmadoraSDL.unsupported("SDL_GetGrabbedWindow"); return 0L; }
    public static boolean nSDL_SetWindowMouseRect(long p0, long p1) { AmadoraSDL.unsupported("nSDL_SetWindowMouseRect"); return false; }
    public static boolean SDL_SetWindowMouseRect(long p0, org.lwjgl.sdl.SDL_Rect p1) { AmadoraSDL.unsupported("SDL_SetWindowMouseRect"); return false; }
    public static long nSDL_GetWindowMouseRect(long p0) { AmadoraSDL.unsupported("nSDL_GetWindowMouseRect"); return 0L; }
    public static org.lwjgl.sdl.SDL_Rect SDL_GetWindowMouseRect(long p0) { AmadoraSDL.unsupported("SDL_GetWindowMouseRect"); return null; }
    public static boolean SDL_SetWindowOpacity(long p0, float p1) { AmadoraSDL.unsupported("SDL_SetWindowOpacity"); return false; }
    public static float SDL_GetWindowOpacity(long p0) { AmadoraSDL.unsupported("SDL_GetWindowOpacity"); return 0f; }
    public static boolean SDL_SetWindowParent(long p0, long p1) { AmadoraSDL.unsupported("SDL_SetWindowParent"); return false; }
    public static boolean SDL_SetWindowModal(long p0, boolean p1) { AmadoraSDL.unsupported("SDL_SetWindowModal"); return false; }
    public static boolean SDL_ShowWindowSystemMenu(long p0, int p1, int p2) { AmadoraSDL.unsupported("SDL_ShowWindowSystemMenu"); return false; }
    public static boolean nSDL_SetWindowHitTest(long p0, long p1, long p2) { AmadoraSDL.unsupported("nSDL_SetWindowHitTest"); return false; }
    public static boolean SDL_SetWindowHitTest(long p0, org.lwjgl.sdl.SDL_HitTestI p1, long p2) { AmadoraSDL.unsupported("SDL_SetWindowHitTest"); return false; }
    public static boolean nSDL_SetWindowShape(long p0, long p1) { AmadoraSDL.unsupported("nSDL_SetWindowShape"); return false; }
    public static boolean SDL_SetWindowShape(long p0, org.lwjgl.sdl.SDL_Surface p1) { AmadoraSDL.unsupported("SDL_SetWindowShape"); return false; }
    public static boolean SDL_FlashWindow(long p0, int p1) { AmadoraSDL.unsupported("SDL_FlashWindow"); return false; }
    public static boolean SDL_SetWindowProgressState(long p0, int p1) { AmadoraSDL.unsupported("SDL_SetWindowProgressState"); return false; }
    public static int SDL_GetWindowProgressState(long p0) { AmadoraSDL.unsupported("SDL_GetWindowProgressState"); return 0; }
    public static boolean SDL_SetWindowProgressValue(long p0, float p1) { AmadoraSDL.unsupported("SDL_SetWindowProgressValue"); return false; }
    public static float SDL_GetWindowProgressValue(long p0) { AmadoraSDL.unsupported("SDL_GetWindowProgressValue"); return 0f; }
    public static boolean SDL_ScreenSaverEnabled() { AmadoraSDL.unsupported("SDL_ScreenSaverEnabled"); return false; }
    public static boolean nSDL_GL_LoadLibrary(long p0) { AmadoraSDL.unsupported("nSDL_GL_LoadLibrary"); return false; }
    public static boolean SDL_GL_LoadLibrary(java.nio.ByteBuffer p0) { AmadoraSDL.unsupported("SDL_GL_LoadLibrary"); return false; }
    public static long nSDL_GL_GetProcAddress(long p0) { AmadoraSDL.unsupported("nSDL_GL_GetProcAddress"); return 0L; }
    public static long SDL_GL_GetProcAddress(java.nio.ByteBuffer p0) { AmadoraSDL.unsupported("SDL_GL_GetProcAddress"); return 0L; }
    public static long nSDL_EGL_GetProcAddress(long p0) { AmadoraSDL.unsupported("nSDL_EGL_GetProcAddress"); return 0L; }
    public static long SDL_EGL_GetProcAddress(java.nio.ByteBuffer p0) { AmadoraSDL.unsupported("SDL_EGL_GetProcAddress"); return 0L; }
    public static long SDL_EGL_GetProcAddress(java.lang.CharSequence p0) { AmadoraSDL.unsupported("SDL_EGL_GetProcAddress"); return 0L; }
    public static boolean nSDL_GL_ExtensionSupported(long p0) { AmadoraSDL.unsupported("nSDL_GL_ExtensionSupported"); return false; }
    public static boolean SDL_GL_ExtensionSupported(java.nio.ByteBuffer p0) { AmadoraSDL.unsupported("SDL_GL_ExtensionSupported"); return false; }
    public static boolean SDL_GL_ExtensionSupported(java.lang.CharSequence p0) { AmadoraSDL.unsupported("SDL_GL_ExtensionSupported"); return false; }
    public static void SDL_GL_ResetAttributes() { AmadoraSDL.unsupported("SDL_GL_ResetAttributes"); }
    public static boolean nSDL_GL_GetAttribute(int p0, long p1) { AmadoraSDL.unsupported("nSDL_GL_GetAttribute"); return false; }
    public static long SDL_EGL_GetCurrentDisplay() { AmadoraSDL.unsupported("SDL_EGL_GetCurrentDisplay"); return 0L; }
    public static long SDL_EGL_GetCurrentConfig() { AmadoraSDL.unsupported("SDL_EGL_GetCurrentConfig"); return 0L; }
    public static long SDL_EGL_GetWindowSurface(long p0) { AmadoraSDL.unsupported("SDL_EGL_GetWindowSurface"); return 0L; }
    public static void nSDL_EGL_SetAttributeCallbacks(long p0, long p1, long p2, long p3) { AmadoraSDL.unsupported("nSDL_EGL_SetAttributeCallbacks"); }
    public static void SDL_EGL_SetAttributeCallbacks(org.lwjgl.sdl.SDL_EGLAttribArrayCallbackI p0, org.lwjgl.sdl.SDL_EGLIntArrayCallbackI p1, org.lwjgl.sdl.SDL_EGLIntArrayCallbackI p2, long p3) { AmadoraSDL.unsupported("SDL_EGL_SetAttributeCallbacks"); }
    public static boolean nSDL_GL_GetSwapInterval(long p0) { AmadoraSDL.unsupported("nSDL_GL_GetSwapInterval"); return false; }
    // ---- END GENERATED ----
}
