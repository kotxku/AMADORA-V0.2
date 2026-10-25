/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

public final class SDLLog {
    private SDLLog() {}

    public static void SDL_SetLogOutputFunction(SDL_LogOutputFunctionI callback, long userdata) {}

    public static void SDL_SetLogPriorities(int priority) {}
}
