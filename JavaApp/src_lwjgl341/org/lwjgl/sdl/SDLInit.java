/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLInit {
    private SDLInit() {}

    public static boolean SDL_Init(int flags) {
        AmadoraSDL.init();
        return true;
    }

    public static void SDL_Quit() {}

    public static boolean SDL_SetAppMetadataProperty(CharSequence name, CharSequence value) {
        return true;
    }
}
