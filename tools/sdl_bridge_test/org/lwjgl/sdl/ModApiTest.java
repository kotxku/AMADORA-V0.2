package org.lwjgl.sdl;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.system.MemoryUtil.NULL;

// Desktop check of the parts of the AMADORA SDL bridge that Fabric mods reach (Sodium, Iris, sodium-extra, Essential,
// Reese's Sodium Options, controller mods): hand-written answers, macros, and the generated "not supported" defaults.
public class ModApiTest {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("ok  " + what);
    }

    public static void main(String[] args) {
        long game = 0x1001L;
        AmadoraSDL.windows.put(game, new AmadoraSDL.Window(2, 0x2020 | 0x2));

        // Properties: Iris builds dialog options, Sodium reads window properties
        int props = SDLProperties.SDL_CreateProperties();
        check(SDLProperties.SDL_SetStringProperty(props, "title", "Pick a shader pack"), "set string");
        check(SDLProperties.SDL_SetNumberProperty(props, "n", 3) && SDLProperties.SDL_GetNumberProperty(props, "n", -1) == 3, "number round trip");
        check(SDLProperties.SDL_SetPointerProperty(props, "p", 0x1234) && SDLProperties.SDL_GetPointerProperty(props, "p", 0) == 0x1234, "pointer round trip");
        check("Pick a shader pack".equals(SDLProperties.SDL_GetStringProperty(props, "title", null)), "string round trip");
        check(SDLProperties.SDL_GetPropertyType(props, "n") == 3 && SDLProperties.SDL_GetPropertyType(props, "missing") == 0, "property types");
        SDLProperties.SDL_DestroyProperties(props);
        check(!SDLProperties.SDL_HasProperty(props, "n"), "destroyed group is empty");
        int windowProps = SDLVideo.SDL_GetWindowProperties(game);
        check(windowProps != 0 && windowProps == SDLVideo.SDL_GetWindowProperties(game), "window properties are stable");
        check(SDLProperties.SDL_GetPointerProperty(windowProps, "SDL.window.win32.hwnd", NULL) == NULL, "no native window handle on iOS");

        // Window queries
        check(SDLVideo.SDL_GetWindowID(game) == 2 && SDLVideo.SDL_GetWindowFromID(2) == game, "window id round trip");
        check(SDLVideo.SDL_ShowWindow(game) && SDLVideo.SDL_GetWindowDisplayScale(game) == 1f, "show window, display scale");
        check(SDLVideo.SDL_WINDOWPOS_ISCENTERED(SDLVideo.SDL_WINDOWPOS_CENTERED_DISPLAY(1)) && SDLVideo.SDL_WINDOWPOS_UNDEFINED == 0x1FFF0000, "window position macros");
        IntBuffer interval = IntBuffer.allocate(1);
        AmadoraSDL.swapInterval = 1;
        check(SDLVideo.SDL_GL_GetSwapInterval(interval) && interval.get(0) == 1, "swap interval (sodium-extra)");

        // Mouse and keyboard
        check(SDLMouse.SDL_BUTTON_LMASK == 1 && SDLMouse.SDL_BUTTON_RMASK == 4 && SDLMouse.SDL_BUTTON_X2MASK == 16, "button masks (Reese's Sodium Options)");
        check(SDLMouse.SDL_HideCursor() && !SDLMouse.SDL_CursorVisible() && SDLMouse.SDL_ShowCursor() && SDLMouse.SDL_CursorVisible(), "cursor visibility");
        check(SDLMouse.SDL_GetMouseFocus() == game && SDLKeyboard.SDL_GetKeyboardFocus() == game, "focus is the game window");
        FloatBuffer fx = FloatBuffer.allocate(1), fy = FloatBuffer.allocate(1);
        SDLMouse.SDL_GetRelativeMouseState(fx, fy);
        check(fx.get(0) == 0f && fy.get(0) == 0f, "relative mouse state");
        ShortBuffer mods = ShortBuffer.allocate(1);
        check(SDLKeyboard.SDL_GetScancodeFromKey('w', mods) == 26 && SDLKeyboard.SDL_GetScancodeFromKey(27, null) == 41, "scancode from key (Essential)");

        // Hints and subsystems
        check(SDLHints.SDL_SetHint("SDL_MOUSE_RELATIVE_MODE_CENTER", "0") && "0".equals(SDLHints.SDL_GetHint("SDL_MOUSE_RELATIVE_MODE_CENTER")), "hint round trip");
        check(!SDLHints.SDL_GetHintBoolean("SDL_MOUSE_RELATIVE_MODE_CENTER", true) && SDLHints.SDL_ResetHint("SDL_MOUSE_RELATIVE_MODE_CENTER")
            && SDLHints.SDL_GetHint("SDL_MOUSE_RELATIVE_MODE_CENTER") == null, "hint boolean and reset");
        check(!SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD) && SDLError.SDL_GetError().contains("iOS"), "gamepad subsystem refused with a reason");
        check(SDLInit.SDL_WasInit(SDLInit.SDL_INIT_GAMEPAD) == 0, "gamepad not initialised");
        IntBuffer pads = SDLGamepad.SDL_GetGamepads();
        check(pads != null && pads.remaining() == 0 && !SDLGamepad.SDL_HasGamepad(), "no gamepads");
        SDLStdinc.SDL_free(pads);
        IntBuffer sticks = SDLJoystick.SDL_GetJoysticks();
        check(sticks.remaining() == 0, "no joysticks");
        SDLStdinc.SDL_free(sticks);

        // File dialog answers with failure instead of hanging (Iris)
        int[] answered = {0};
        SDLDialog.SDL_ShowFileDialogWithProperties(SDLDialog.SDL_FILEDIALOG_OPENFILE, (userdata, filelist, filter) -> {
            if (userdata == 7 && filelist == NULL) answered[0]++;
        }, 7, 0);
        check(answered[0] == 1, "file dialog callback gets a null file list");

        // Macros and version
        check(SDLPixels.SDL_BITSPERPIXEL(SDLPixels.SDL_PIXELFORMAT_XRGB8888) == 24 && SDLPixels.SDL_BYTESPERPIXEL(SDLPixels.SDL_PIXELFORMAT_XRGB8888) == 4, "bits/bytes per pixel");
        check(SDLPixels.SDL_ISPIXELFORMAT_ALPHA(SDLPixels.SDL_PIXELFORMAT_ARGB8888) && !SDLPixels.SDL_ISPIXELFORMAT_ALPHA(SDLPixels.SDL_PIXELFORMAT_XRGB8888), "alpha formats");
        check(SDLPixels.SDL_DEFINE_PIXELFORMAT(SDLPixels.SDL_PIXELTYPE_PACKED32, SDLPixels.SDL_PACKEDORDER_ARGB, SDLPixels.SDL_PACKEDLAYOUT_8888, 32, 4) == SDLPixels.SDL_PIXELFORMAT_ARGB8888, "define pixel format");
        check(SDLPixels.SDL_ISPIXELFORMAT_FOURCC(SDLPixels.SDL_PIXELFORMAT_YUY2) && SDLPixels.SDL_BYTESPERPIXEL(SDLPixels.SDL_PIXELFORMAT_YUY2) == 2, "FourCC formats");
        check(SDLPixels.SDL_PIXELFORMAT_RGBA32 == SDLPixels.SDL_PIXELFORMAT_ABGR8888 && SDLPixels.SDL_PIXELFORMAT_XRGB32 == SDLPixels.SDL_PIXELFORMAT_BGRX8888, "little-endian 32-bit aliases");
        check(SDLAudio.SDL_AUDIO_S16 == SDLAudio.SDL_AUDIO_S16LE && SDLAudio.SDL_AUDIO_F32 == SDLAudio.SDL_AUDIO_F32LE && SDLAudio.SDL_AUDIO_ISFLOAT(SDLAudio.SDL_AUDIO_F32)
            && SDLAudio.SDL_AUDIO_BITSIZE(SDLAudio.SDL_AUDIO_S16) == 16, "audio format macros");
        check(SDLTimer.SDL_NS_TO_MS(SDLTimer.SDL_MS_TO_NS(1500)) == 1500 && SDLTimer.SDL_GetPerformanceFrequency() == 1000000000L, "timer macros");
        int version = SDLVersion.SDL_GetVersion();
        check(SDLVersion.SDL_VERSIONNUM_MAJOR(version) == 3 && SDLVersion.SDL_VERSION_ATLEAST(3, 2, 0) && version == SDLVersion.SDL_VERSION, "version");

        // Generated defaults: a function with no iOS backend reports failure instead of looking for libSDL3
        check(SDLRender.SDL_CreateRenderer(game, (CharSequence) null) == NULL && SDLError.SDL_GetError().contains("SDL_CreateRenderer"), "generated default and error text");
        check(SDLGamepad.SDL_OpenGamepad(1) == NULL && !SDLGamepad.SDL_GamepadConnected(NULL), "gamepad defaults");
        System.out.println("ALL PASSED");
    }
}
