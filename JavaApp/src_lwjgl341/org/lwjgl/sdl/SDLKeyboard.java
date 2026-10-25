/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.ByteBuffer;

public final class SDLKeyboard {
    private SDLKeyboard() {}

    public static ByteBuffer SDL_GetKeyboardState() {
        return AmydoraSDL.keyboardState;
    }

    public static short SDL_GetModState() {
        return AmydoraSDL.modState;
    }

    public static int SDL_GetKeyFromScancode(int scancode, short modstate, boolean keyEvent) {
        return Keys.keycodeForScancode(scancode, modstate);
    }

    public static String SDL_GetKeyName(int key) {
        return Keys.nameForKeycode(key);
    }

    // Text comes from Amethyst's on-screen or hardware keyboard; this only gates text events like SDL does
    public static boolean SDL_StartTextInput(long window) {
        AmydoraSDL.textInput = true;
        return true;
    }

    public static boolean SDL_StopTextInput(long window) {
        AmydoraSDL.textInput = false;
        return true;
    }

    public static boolean SDL_SetTextInputArea(long window, SDL_Rect.Buffer rect, int cursor) {
        return true;
    }

    public static boolean SDL_ClearComposition(long window) {
        return true;
    }
}
