/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLMisc {
    private SDLMisc() {}

    public static boolean SDL_OpenURL(CharSequence url) {
        try {
            // Amethyst hooks process spawning and opens "open <url>" in iOS
            Runtime.getRuntime().exec(new String[]{"open", url.toString()});
            return true;
        } catch (Exception e) {
            AmydoraSDL.setError(e.toString());
            return false;
        }
    }
}
