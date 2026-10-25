/*
 * AMADORA: GLFW key codes (what Amethyst's controls send) to SDL3 scancodes and keycodes (what Minecraft 26.3 reads).
 * Keycodes follow a US layout, like SDL's default keymap.
 */
package org.lwjgl.sdl;

final class Keys {
    private Keys() {}

    private static final int[] GLFW_TO_SCANCODE = new int[349];
    private static final int[] SCANCODE_TO_KEYCODE = new int[AmadoraSDL.SCANCODE_COUNT];
    private static final String[] SCANCODE_NAMES = new String[AmadoraSDL.SCANCODE_COUNT];

    private static void map(int glfwKey, int scancode, int keycode, String name) {
        GLFW_TO_SCANCODE[glfwKey] = scancode;
        SCANCODE_TO_KEYCODE[scancode] = keycode;
        SCANCODE_NAMES[scancode] = name;
    }

    /** Keys without a character get scancode | SDLK_SCANCODE_MASK, as in SDL. */
    private static void mapSpecial(int glfwKey, int scancode, String name) {
        map(glfwKey, scancode, scancode | AmadoraSDL.KEYCODE_SCANCODE_MASK, name);
    }

    static {
        for (int i = 0; i < 26; i++) map(65 + i, 4 + i, 'a' + i, String.valueOf((char) ('A' + i)));
        for (int i = 1; i <= 9; i++) map(48 + i, 29 + i, '0' + i, String.valueOf(i));
        map(48, 39, '0', "0");
        map(257, 40, '\r', "Return");
        map(256, 41, 27, "Escape");
        map(259, 42, '\b', "Backspace");
        map(258, 43, '\t', "Tab");
        map(32, 44, ' ', "Space");
        map(45, 45, '-', "-");
        map(61, 46, '=', "=");
        map(91, 47, '[', "[");
        map(93, 48, ']', "]");
        map(92, 49, '\\', "\\");
        map(59, 51, ';', ";");
        map(39, 52, '\'', "'");
        map(96, 53, '`', "`");
        map(44, 54, ',', ",");
        map(46, 55, '.', ".");
        map(47, 56, '/', "/");
        mapSpecial(280, 57, "CapsLock");
        for (int i = 0; i < 12; i++) mapSpecial(290 + i, 58 + i, "F" + (i + 1));
        mapSpecial(283, 70, "PrintScreen");
        mapSpecial(281, 71, "ScrollLock");
        mapSpecial(284, 72, "Pause");
        mapSpecial(260, 73, "Insert");
        mapSpecial(268, 74, "Home");
        mapSpecial(266, 75, "PageUp");
        map(261, 76, 127, "Delete");
        mapSpecial(269, 77, "End");
        mapSpecial(267, 78, "PageDown");
        mapSpecial(262, 79, "Right");
        mapSpecial(263, 80, "Left");
        mapSpecial(264, 81, "Down");
        mapSpecial(265, 82, "Up");
        mapSpecial(282, 83, "Numlock");
        mapSpecial(331, 84, "Keypad /");
        mapSpecial(332, 85, "Keypad *");
        mapSpecial(333, 86, "Keypad -");
        mapSpecial(334, 87, "Keypad +");
        mapSpecial(335, 88, "Keypad Enter");
        for (int i = 1; i <= 9; i++) mapSpecial(320 + i, 88 + i, "Keypad " + i);
        mapSpecial(320, 98, "Keypad 0");
        mapSpecial(330, 99, "Keypad .");
        mapSpecial(161, 100, "\\");
        mapSpecial(348, 101, "Menu");
        mapSpecial(336, 103, "Keypad =");
        for (int i = 0; i < 12; i++) mapSpecial(302 + i, 104 + i, "F" + (i + 13));
        mapSpecial(341, 224, "Left Ctrl");
        mapSpecial(340, 225, "Left Shift");
        mapSpecial(342, 226, "Left Alt");
        mapSpecial(343, 227, "Left GUI");
        mapSpecial(345, 228, "Right Ctrl");
        mapSpecial(344, 229, "Right Shift");
        mapSpecial(346, 230, "Right Alt");
        mapSpecial(347, 231, "Right GUI");
    }

    static int scancodeForGlfwKey(int glfwKey) {
        return glfwKey >= 0 && glfwKey < GLFW_TO_SCANCODE.length ? GLFW_TO_SCANCODE[glfwKey] : 0;
    }

    static int keycodeForScancode(int scancode, short mod) {
        if (scancode <= 0 || scancode >= SCANCODE_TO_KEYCODE.length) return 0;
        int key = SCANCODE_TO_KEYCODE[scancode];
        if (key == 0) return scancode | AmadoraSDL.KEYCODE_SCANCODE_MASK;
        return key;
    }

    static String nameForKeycode(int keycode) {
        if ((keycode & AmadoraSDL.KEYCODE_SCANCODE_MASK) != 0) {
            int scancode = keycode & ~AmadoraSDL.KEYCODE_SCANCODE_MASK;
            return scancode < SCANCODE_NAMES.length && SCANCODE_NAMES[scancode] != null ? SCANCODE_NAMES[scancode] : "";
        }
        for (int sc = 0; sc < SCANCODE_TO_KEYCODE.length; sc++) {
            if (SCANCODE_TO_KEYCODE[sc] == keycode && SCANCODE_NAMES[sc] != null) return SCANCODE_NAMES[sc];
        }
        return keycode > 32 && keycode < 127 ? String.valueOf((char) Character.toUpperCase(keycode)) : "";
    }

    // SDL_Keymod bits
    static final short KMOD_LSHIFT = 0x1, KMOD_RSHIFT = 0x2, KMOD_LCTRL = 0x40, KMOD_RCTRL = 0x80,
        KMOD_LALT = 0x100, KMOD_RALT = 0x200, KMOD_LGUI = 0x400, KMOD_RGUI = 0x800, KMOD_NUM = 0x1000, KMOD_CAPS = 0x2000;

    static short modForScancode(int scancode) {
        switch (scancode) {
            case 224: return KMOD_LCTRL;
            case 225: return KMOD_LSHIFT;
            case 226: return KMOD_LALT;
            case 227: return KMOD_LGUI;
            case 228: return KMOD_RCTRL;
            case 229: return KMOD_RSHIFT;
            case 230: return KMOD_RALT;
            case 231: return KMOD_RGUI;
            default: return 0;
        }
    }

    /** Modifier keys pressed on the on-screen controls arrive only as GLFW mod flags, so merge those too. */
    static short lockMods(int glfwMods) {
        short mod = 0;
        if ((glfwMods & 0x1) != 0) mod |= KMOD_LSHIFT;
        if ((glfwMods & 0x2) != 0) mod |= KMOD_LCTRL;
        if ((glfwMods & 0x4) != 0) mod |= KMOD_LALT;
        if ((glfwMods & 0x8) != 0) mod |= KMOD_LGUI;
        if ((glfwMods & 0x10) != 0) mod |= KMOD_CAPS;
        if ((glfwMods & 0x20) != 0) mod |= KMOD_NUM;
        return mod;
    }
}
