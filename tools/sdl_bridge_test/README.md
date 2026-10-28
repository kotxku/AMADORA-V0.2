Desktop check of the SDL3 bridge (`JavaApp/src_lwjgl341/org/lwjgl/sdl`). It queues input the way the GLFW callbacks do and reads it back through LWJGL's SDL structs, the way Minecraft 26.3 does.

Needs desktop LWJGL 3.4.1 from Maven (`lwjgl`, `lwjgl-glfw`, `lwjgl-sdl`, and `lwjgl` natives for your OS), plus `lwjgl-opengl` and `lwjgl-vulkan` to compile. Compile the bridge sources against the classes the JavaApp build produces in `build/lwjgl341_classes`, then run `org.lwjgl.sdl.BridgeTest` with the desktop jars.

`ModApiTest` checks what Fabric mods reach beyond vanilla: properties, hints, window and mouse queries, the macros, refused subsystems, and the generated "not available on iOS" defaults. Compile and run it the same way.
