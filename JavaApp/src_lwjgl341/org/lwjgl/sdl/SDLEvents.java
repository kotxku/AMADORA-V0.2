/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLEvents {
    private SDLEvents() {}

    public static void SDL_PumpEvents() {
        AmadoraSDL.pump();
    }

    public static boolean SDL_PollEvent(SDL_Event event) {
        return AmadoraSDL.poll(event);
    }

    public static void SDL_FlushEvents(int minType, int maxType) {
        AmadoraSDL.flush(minType, maxType);
    }

    public static long SDL_GetWindowFromEvent(SDL_Event event) {
        return AmadoraSDL.eventWindow();
    }
}
