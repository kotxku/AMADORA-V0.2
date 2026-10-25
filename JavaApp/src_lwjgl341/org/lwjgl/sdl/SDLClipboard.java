/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import org.lwjgl.glfw.GLFW;

public final class SDLClipboard {
    private SDLClipboard() {}

    public static String SDL_GetClipboardText() {
        String text = GLFW.glfwGetClipboardString(AmydoraSDL.context);
        return text == null ? "" : text;
    }

    public static boolean SDL_SetClipboardText(CharSequence text) {
        GLFW.glfwSetClipboardString(AmydoraSDL.context, text == null ? "" : text);
        return true;
    }
}
