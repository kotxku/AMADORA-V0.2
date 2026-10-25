/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.IntBuffer;

public final class SDLMessageBox {
    private SDLMessageBox() {}

    // No native dialogs from the game thread on iOS; the text goes to the log
    public static boolean SDL_ShowMessageBox(SDL_MessageBoxData data, IntBuffer buttonid) {
        System.err.println("[SDL message box] " + data.titleString() + ": " + data.messageString());
        AmydoraSDL.setError("Message boxes aren't supported on iOS");
        return false;
    }

    public static boolean SDL_ShowSimpleMessageBox(int flags, CharSequence title, CharSequence message, long window) {
        System.err.println("[SDL message box] " + title + ": " + message);
        AmydoraSDL.setError("Message boxes aren't supported on iOS");
        return false;
    }
}
