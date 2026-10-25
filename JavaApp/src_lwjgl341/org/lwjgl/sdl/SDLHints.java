/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLHints {
    private SDLHints() {}

    public static boolean SDL_SetHint(CharSequence name, CharSequence value) {
        return true;
    }
}
