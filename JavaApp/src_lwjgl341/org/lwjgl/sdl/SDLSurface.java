/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryUtil.*;

public final class SDLSurface {
    private SDLSurface() {}

    // Only used for window icons, which iOS doesn't have
    public static SDL_Surface SDL_CreateSurfaceFrom(int width, int height, int format, ByteBuffer pixels, int pitch) {
        SDL_Surface surface = SDL_Surface.calloc();
        long a = surface.address();
        memPutInt(a + SDL_Surface.FORMAT, format);
        memPutInt(a + SDL_Surface.W, width);
        memPutInt(a + SDL_Surface.H, height);
        memPutInt(a + SDL_Surface.PITCH, pitch);
        memPutAddress(a + SDL_Surface.PIXELS, pixels == null ? NULL : memAddress(pixels));
        return surface;
    }

    public static void SDL_DestroySurface(SDL_Surface surface) {
        if (surface != null) surface.free();
    }

    public static boolean SDL_AddSurfaceAlternateImage(SDL_Surface surface, SDL_Surface image) {
        return true;
    }
}
