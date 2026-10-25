/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLError {
    private SDLError() {}

    public static String SDL_GetError() {
        return AmadoraSDL.error;
    }
}
