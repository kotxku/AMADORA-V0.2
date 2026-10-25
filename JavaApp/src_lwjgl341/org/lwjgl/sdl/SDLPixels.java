/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLPixels {
    private SDLPixels() {}

    public static SDL_PixelFormatDetails SDL_GetPixelFormatDetails(int format) {
        return AmadoraSDL.formatDetails();
    }
}
