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
        return true;
    }

    public static boolean SDL_GL_SwapWindow(long window) {
        if (AmadoraSDL.context != 0) GLFW.glfwSwapBuffers(AmadoraSDL.context);
        return true;
    }
}
