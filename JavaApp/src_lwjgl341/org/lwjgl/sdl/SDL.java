/*
 * AMYDORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmydoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.ByteBuffer;

import org.lwjgl.system.SharedLibrary;

import static org.lwjgl.system.MemoryUtil.NULL;

public final class SDL {
    private SDL() {}

    // There's no libSDL3 on iOS; Minecraft only loads this to check that SDL is available
    private static final SharedLibrary LIBRARY = new SharedLibrary() {
        @Override public String getName() { return "SDL3 (AMYDORA iOS bridge)"; }
        @Override public String getPath() { return null; }
        @Override public long getFunctionAddress(ByteBuffer functionName) { return NULL; }
        @Override public long address() { return NULL; }
        @Override public void free() {}
    };

    public static SharedLibrary getLibrary() {
        return LIBRARY;
    }
}
