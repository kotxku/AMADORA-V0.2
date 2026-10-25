package org.lwjgl.sdl;

import java.lang.reflect.Method;

// Desktop check of the AMADORA SDL bridge: queue events the way the GLFW callbacks do, read them back with LWJGL's SDL structs like Minecraft does.
public class BridgeTest {
    static Method push;

    static AmadoraSDL.Event push(int type, int i1, int i2) throws Exception {
        return (AmadoraSDL.Event) push.invoke(null, type, i1, i2);
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok  " + what);
    }

    public static void main(String[] args) throws Exception {
        push = AmadoraSDL.class.getDeclaredMethod("push", int.class, int.class, int.class);
        push.setAccessible(true);
        AmadoraSDL.windows.put(0x1000L, new AmadoraSDL.Window(1, 0x8 | 0x2)); // hidden probe window
        long game = 0x1001L;
        AmadoraSDL.windows.put(game, new AmadoraSDL.Window(2, 0x2020 | 0x2));

        int sc = Keys.scancodeForGlfwKey(87); // GLFW_KEY_W
        AmadoraSDL.Event k = push(AmadoraSDL.EVENT_KEY_DOWN, sc, Keys.keycodeForScancode(sc, (short) 0));
        k.mod = Keys.KMOD_LSHIFT;
        k.flag = true;
        AmadoraSDL.Event m = push(AmadoraSDL.EVENT_MOUSE_MOTION, 0, 0);
        m.f1 = 10; m.f2 = 20; m.f3 = 3; m.f4 = -4;
        AmadoraSDL.Event b = push(AmadoraSDL.EVENT_MOUSE_BUTTON_DOWN, 3, 0);
        b.flag = true;
        AmadoraSDL.Event w = push(AmadoraSDL.EVENT_MOUSE_WHEEL, 0, -1);
        w.f2 = -1f;
        push(AmadoraSDL.EVENT_TEXT_INPUT, 0, 0).text = "é";
        push(AmadoraSDL.EVENT_WINDOW_RESIZED, 2556, 1179);

        SDL_Event e = SDL_Event.malloc();
        check(SDLEvents.SDL_PollEvent(e) && e.type() == 0x300, "key down type");
        check(e.key().scancode() == 26 && e.key().key() == 'w', "W -> scancode 26, keycode 'w'");
        check(e.key().mod() == 1 && e.key().repeat() && e.key().down(), "mod/repeat/down");
        check(SDLEvents.SDL_GetWindowFromEvent(e) == game, "events go to the visible game window");
        check(e.key().windowID() == 2, "windowID of game window");
        check(SDLEvents.SDL_PollEvent(e) && e.type() == 0x400, "motion type");
        check(e.motion().x() == 10 && e.motion().y() == 20 && e.motion().xrel() == 3 && e.motion().yrel() == -4, "motion values");
        check(SDLEvents.SDL_PollEvent(e) && e.type() == 0x401 && e.button().button() == 3 && e.button().down(), "right button");
        check(SDLEvents.SDL_PollEvent(e) && e.type() == 0x403 && e.wheel().y() == -1f && e.wheel().integer_y() == -1, "wheel");
        check(SDLEvents.SDL_PollEvent(e) && e.type() == 0x303 && "é".equals(e.text().textString()), "UTF-8 text input");
        check(SDLEvents.SDL_PollEvent(e) && e.type() == 0x206 && e.window().data1() == 2556 && e.window().data2() == 1179, "resize");
        check(!SDLEvents.SDL_PollEvent(e), "queue empty");

        check(SDLKeyboard.SDL_GetKeyFromScancode(41, (short) 0, true) == 27, "escape keycode");
        check(SDLKeyboard.SDL_GetKeyFromScancode(Keys.scancodeForGlfwKey(340), (short) 0, true) == (225 | 1 << 30), "left shift keycode");
        check("Left Shift".equals(SDLKeyboard.SDL_GetKeyName(225 | 1 << 30)) && "W".equals(SDLKeyboard.SDL_GetKeyName('w')), "key names");
        check(Keys.scancodeForGlfwKey(290) == 58 && Keys.scancodeForGlfwKey(265) == 82 && Keys.scancodeForGlfwKey(32) == 44, "F1/Up/Space");

        java.nio.IntBuffer displays = SDLVideo.SDL_GetDisplays();
        check(displays.get(0) == 1, "display list");
        SDLStdinc.SDL_free(displays);
        java.nio.IntBuffer v = java.nio.IntBuffer.allocate(1);
        SDLVideo.SDL_GL_SetAttribute(17, 3);
        SDLVideo.SDL_GL_GetAttribute(17, v);
        check(v.get(0) == 3, "GL attribute round trip");
        check(SDL.getLibrary() != null, "SDL library stub");
        SDL_Surface s = SDLSurface.SDL_CreateSurfaceFrom(16, 32, 0, null, 64);
        check(s.w() == 16 && s.h() == 32, "icon surface size");
        SDLSurface.SDL_DestroySurface(s);
        e.close();
        System.out.println("ALL PASSED");
    }
}
