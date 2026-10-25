/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.IntBuffer;

import org.lwjgl.PointerBuffer;

import static org.lwjgl.system.MemoryUtil.*;

public final class SDLStdinc {
    private SDLStdinc() {}

    // Everything this bridge hands out is allocated with LWJGL's allocator
    public static void SDL_free(IntBuffer mem) {
        if (mem != null) nmemFree(memAddress(mem));
    }

    public static void SDL_free(PointerBuffer mem) {
        if (mem != null) nmemFree(mem.address());
    }
}
