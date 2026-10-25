/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLPlatform {
    private SDLPlatform() {}

    public static String SDL_GetPlatform() {
        return "iOS";
    }
}
