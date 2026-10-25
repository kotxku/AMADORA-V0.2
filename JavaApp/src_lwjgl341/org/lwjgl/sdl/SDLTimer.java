/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLTimer {
    private SDLTimer() {}

    public static long SDL_GetTicksNS() {
        return System.nanoTime() - AmadoraSDL.startNanos;
    }
}
