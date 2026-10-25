/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLError {
    private SDLError() {}

    public static String SDL_GetError() {
        return AmydoraSDL.error;
    }
}
