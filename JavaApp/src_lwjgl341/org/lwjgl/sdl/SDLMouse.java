/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.FloatBuffer;

import org.lwjgl.glfw.GLFW;

public final class SDLMouse {
    private SDLMouse() {}

    public static int SDL_GetMouseState(FloatBuffer x, FloatBuffer y) {
        if (x != null) x.put(x.position(), AmydoraSDL.mouseX);
        if (y != null) y.put(y.position(), AmydoraSDL.mouseY);
        return AmydoraSDL.buttonMask;
    }

    public static int SDL_GetGlobalMouseState(FloatBuffer x, FloatBuffer y) {
        return SDL_GetMouseState(x, y);
    }

    public static boolean SDL_SetWindowRelativeMouseMode(long window, boolean enabled) {
        if (AmydoraSDL.context != 0) {
            GLFW.glfwSetInputMode(AmydoraSDL.context, GLFW.GLFW_CURSOR, enabled ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL);
        }
        return true;
    }

    public static void SDL_WarpMouseInWindow(long window, float x, float y) {
        AmydoraSDL.mouseX = x;
        AmydoraSDL.mouseY = y;
        if (AmydoraSDL.context != 0) GLFW.glfwSetCursorPos(AmydoraSDL.context, x, y);
    }

    public static long SDL_CreateSystemCursor(int id) {
        return id + 1;
    }

    public static long SDL_GetDefaultCursor() {
        return 1;
    }

    public static boolean SDL_SetCursor(long cursor) {
        return true;
    }
}
