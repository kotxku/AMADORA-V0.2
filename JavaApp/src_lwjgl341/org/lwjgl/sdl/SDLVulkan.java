/*
 * AMADORA: SDL3 on top of Amethyst's iOS GLFW layer, see AmadoraSDL. Only the functions Minecraft 26.3 calls.
 */
package org.lwjgl.sdl;

import java.nio.LongBuffer;

import org.lwjgl.PointerBuffer;
import org.lwjgl.vulkan.VkAllocationCallbacks;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

public final class SDLVulkan {
    private SDLVulkan() {}

    // Minecraft falls back to OpenGL when Vulkan isn't available; on iOS rendering goes through Amethyst's GL renderers
    public static boolean SDL_Vulkan_LoadLibrary(CharSequence path) {
        AmadoraSDL.setError("Vulkan isn't supported by AMADORA on iOS");
        return false;
    }

    public static void SDL_Vulkan_UnloadLibrary() {}

    public static long SDL_Vulkan_GetVkGetInstanceProcAddr() {
        return 0;
    }

    public static PointerBuffer SDL_Vulkan_GetInstanceExtensions() {
        return null;
    }

    public static boolean SDL_Vulkan_CreateSurface(long window, VkInstance instance, VkAllocationCallbacks allocator, LongBuffer surface) {
        AmadoraSDL.setError("Vulkan isn't supported by AMADORA on iOS");
        return false;
    }

    public static boolean SDL_Vulkan_GetPresentationSupport(VkInstance instance, VkPhysicalDevice physicalDevice, int queueFamilyIndex) {
        return false;
    }
}
