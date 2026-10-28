/*
 * AMADORA: SDL3 emulation for Minecraft 26.3+ on iOS.
 * Minecraft 26.3 talks to SDL3 instead of GLFW. Amethyst's iOS surface, touch controls and on-screen
 * keyboard only drive the GLFW stub (org.lwjgl.glfw.GLFW), so the SDL classes in this package are
 * implemented on top of it: one GLFW context backs every SDL window, and GLFW callbacks become SDL events.
 */
package org.lwjgl.sdl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryUtil.*;

public final class AmadoraSDL {
    private AmadoraSDL() {}

    // SDL3 event types
    static final int EVENT_QUIT = 0x100;
    static final int EVENT_WINDOW_SHOWN = 0x202, EVENT_WINDOW_RESIZED = 0x206, EVENT_WINDOW_PIXEL_SIZE_CHANGED = 0x207,
        EVENT_WINDOW_MOUSE_ENTER = 0x20C, EVENT_WINDOW_MOUSE_LEAVE = 0x20D,
        EVENT_WINDOW_FOCUS_GAINED = 0x20E, EVENT_WINDOW_FOCUS_LOST = 0x20F, EVENT_WINDOW_CLOSE_REQUESTED = 0x210;
    static final int EVENT_KEY_DOWN = 0x300, EVENT_KEY_UP = 0x301, EVENT_TEXT_INPUT = 0x303;
    static final int EVENT_MOUSE_MOTION = 0x400, EVENT_MOUSE_BUTTON_DOWN = 0x401, EVENT_MOUSE_BUTTON_UP = 0x402, EVENT_MOUSE_WHEEL = 0x403;

    static final long WINDOW_FULLSCREEN = 0x1L, WINDOW_HIDDEN = 0x8L, WINDOW_INPUT_FOCUS = 0x200L, WINDOW_MOUSE_FOCUS = 0x400L;
    static final int SCANCODE_COUNT = 512;
    static final int KEYCODE_SCANCODE_MASK = 1 << 30;

    static final class Window {
        final int id;
        long flags;
        int properties; // SDL_GetWindowProperties, created on first use
        Window(int id, long flags) { this.id = id; this.flags = flags; }
    }

    static final Map<Long, Window> windows = new HashMap<>();
    static long nextWindow = 0x1000;
    static long context; // the single GLFW stub window/context
    static boolean initialized, shown, textInput;
    static String error = "";
    static final Map<Integer, Integer> glAttributes = new HashMap<>();
    static final long startNanos = System.nanoTime();

    static final ByteBuffer keyboardState = memCalloc(SCANCODE_COUNT);
    static short modState;
    static float mouseX, mouseY;
    static int buttonMask;

    /** A pending SDL event; written into the caller's SDL_Event by SDL_PollEvent. */
    static final class Event {
        int type;
        int i1, i2, i3;
        float f1, f2, f3, f4;
        short mod;
        boolean flag;
        String text;
    }
    static final ArrayDeque<Event> queue = new ArrayDeque<>();
    static long lastText; // UTF-8 text owned by the most recently polled text event

    static void setError(String message) {
        error = message;
    }

    // Parts of SDL3 the bridge doesn't provide; the generated defaults land here
    private static final java.util.Set<String> reported = java.util.concurrent.ConcurrentHashMap.newKeySet();

    static void unsupported(String function) {
        setError(function + " isn't available on iOS");
        if (reported.add(function)) {
            System.out.println("[SDLBridge] " + function + " isn't available on iOS; returning a default");
        }
    }

    // State the extra window, mouse and init queries report back
    static int swapInterval, initFlags;
    static boolean relativeMouse, cursorVisible = true;
    static long cursor = 1; // SDL_GetDefaultCursor
    static final Map<String, String> hints = new java.util.concurrent.ConcurrentHashMap<>();

    static void init() {
        if (!initialized) {
            initialized = GLFW.glfwInit();
        }
    }

    static long createWindow(long flags) {
        init();
        long handle = nextWindow++;
        windows.put(handle, new Window((int) (handle - 0x1000 + 1), flags));
        showIfNeeded();
        return handle;
    }

    static void destroyWindow(long handle) {
        windows.remove(handle);
    }

    /** The visible window events belong to: the newest window not created hidden. */
    static long eventWindow() {
        long best = 0;
        for (Map.Entry<Long, Window> e : windows.entrySet()) {
            if ((e.getValue().flags & WINDOW_HIDDEN) == 0 && e.getKey() > best) best = e.getKey();
        }
        if (best == 0) {
            for (Long handle : windows.keySet()) best = Math.max(best, handle);
        }
        return best;
    }

    static int eventWindowId() {
        Window w = windows.get(eventWindow());
        return w == null ? 0 : w.id;
    }

    static long windowForId(int id) {
        for (Map.Entry<Long, Window> e : windows.entrySet()) {
            if (e.getValue().id == id) return e.getKey();
        }
        return 0;
    }

    static long createContext() {
        if (context == 0) {
            init();
            context = GLFW.glfwCreateWindow(GLFW.mGLFWWindowWidth, GLFW.mGLFWWindowHeight, "Minecraft", 0, 0);
            if (context == 0) {
                setError("Couldn't create the iOS OpenGL context");
                return 0;
            }
            registerCallbacks(context);
        }
        GLFW.glfwMakeContextCurrent(context);
        showIfNeeded();
        return context;
    }

    private static void showIfNeeded() {
        if (shown || context == 0) return;
        for (Window w : windows.values()) {
            if ((w.flags & WINDOW_HIDDEN) == 0) {
                shown = true;
                GLFW.glfwShowWindow(context);
                int[] size = framebufferSize();
                push(EVENT_WINDOW_SHOWN, 0, 0);
                push(EVENT_WINDOW_RESIZED, size[0], size[1]);
                push(EVENT_WINDOW_PIXEL_SIZE_CHANGED, size[0], size[1]);
                push(EVENT_WINDOW_FOCUS_GAINED, 0, 0);
                push(EVENT_WINDOW_MOUSE_ENTER, 0, 0);
                return;
            }
        }
    }

    static int[] framebufferSize() {
        if (context != 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1);
                GLFW.glfwGetFramebufferSize(context, w, h);
                if (w.get(0) > 0 && h.get(0) > 0) return new int[]{w.get(0), h.get(0)};
            } catch (RuntimeException ignored) {
            }
        }
        return new int[]{Math.max(1, GLFW.mGLFWWindowWidth), Math.max(1, GLFW.mGLFWWindowHeight)};
    }

    private static Event push(int type, int i1, int i2) {
        Event e = new Event();
        e.type = type;
        e.i1 = i1;
        e.i2 = i2;
        synchronized (queue) {
            queue.add(e);
        }
        return e;
    }

    private static void registerCallbacks(long window) {
        GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            int sc = Keys.scancodeForGlfwKey(key);
            if (sc == 0) return;
            boolean down = action != GLFW.GLFW_RELEASE;
            keyboardState.put(sc, (byte) (down ? 1 : 0));
            short modBit = Keys.modForScancode(sc);
            if (modBit != 0) modState = (short) (down ? modState | modBit : modState & ~modBit);
            short mod = (short) (modState | Keys.lockMods(mods));
            Event e = push(down ? EVENT_KEY_DOWN : EVENT_KEY_UP, sc, Keys.keycodeForScancode(sc, mod));
            e.mod = mod;
            e.flag = action == GLFW.GLFW_REPEAT;
        });
        GLFW.glfwSetCharCallback(window, (win, codepoint) -> {
            if (!textInput) return;
            push(EVENT_TEXT_INPUT, 0, 0).text = new String(Character.toChars(codepoint));
        });
        GLFW.glfwSetCursorPosCallback(window, (win, x, y) -> {
            Event e = push(EVENT_MOUSE_MOTION, 0, 0);
            e.f1 = (float) x;
            e.f2 = (float) y;
            e.f3 = (float) x - mouseX;
            e.f4 = (float) y - mouseY;
            e.i3 = buttonMask;
            mouseX = (float) x;
            mouseY = (float) y;
        });
        GLFW.glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            int sdlButton = button == GLFW.GLFW_MOUSE_BUTTON_LEFT ? 1 : button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ? 3
                : button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE ? 2 : button + 1;
            boolean down = action == GLFW.GLFW_PRESS;
            buttonMask = down ? buttonMask | (1 << (sdlButton - 1)) : buttonMask & ~(1 << (sdlButton - 1));
            Event e = push(down ? EVENT_MOUSE_BUTTON_DOWN : EVENT_MOUSE_BUTTON_UP, sdlButton, 0);
            e.flag = down;
            e.f1 = mouseX;
            e.f2 = mouseY;
        });
        GLFW.glfwSetScrollCallback(window, (win, dx, dy) -> {
            Event e = push(EVENT_MOUSE_WHEEL, (int) dx, (int) dy);
            e.f1 = (float) dx;
            e.f2 = (float) dy;
            e.f3 = mouseX;
            e.f4 = mouseY;
        });
        GLFW.glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            if (w <= 0 || h <= 0) return;
            push(EVENT_WINDOW_RESIZED, w, h);
            push(EVENT_WINDOW_PIXEL_SIZE_CHANGED, w, h);
        });
        GLFW.glfwSetWindowFocusCallback(window, (win, focused) ->
            push(focused ? EVENT_WINDOW_FOCUS_GAINED : EVENT_WINDOW_FOCUS_LOST, 0, 0));
        GLFW.glfwSetCursorEnterCallback(window, (win, entered) ->
            push(entered ? EVENT_WINDOW_MOUSE_ENTER : EVENT_WINDOW_MOUSE_LEAVE, 0, 0));
        GLFW.glfwSetWindowCloseCallback(window, win -> push(EVENT_WINDOW_CLOSE_REQUESTED, 0, 0));
    }

    static void pump() {
        if (context != 0) GLFW.glfwPollEvents();
    }

    static boolean poll(SDL_Event event) {
        if (lastText != NULL) {
            nmemFree(lastText);
            lastText = NULL;
        }
        Event e;
        synchronized (queue) {
            if (queue.isEmpty()) {
                // SDL_PollEvent pumps when the queue is empty
                pump();
            }
            e = queue.poll();
        }
        if (e == null) return false;
        if (event != null) write(e, event.address());
        return true;
    }

    static void flush(int minType, int maxType) {
        synchronized (queue) {
            for (Iterator<Event> it = queue.iterator(); it.hasNext(); ) {
                int type = it.next().type;
                if (type >= minType && type <= maxType) it.remove();
            }
        }
    }

    private static void write(Event e, long a) {
        memSet(a, 0, SDL_Event.SIZEOF);
        memPutInt(a, e.type);
        memPutLong(a + SDL_WindowEvent.TIMESTAMP, System.nanoTime() - startNanos);
        int windowId = eventWindowId();
        switch (e.type) {
            case EVENT_KEY_DOWN:
            case EVENT_KEY_UP:
                memPutInt(a + SDL_KeyboardEvent.WINDOWID, windowId);
                memPutInt(a + SDL_KeyboardEvent.SCANCODE, e.i1);
                memPutInt(a + SDL_KeyboardEvent.KEY, e.i2);
                memPutShort(a + SDL_KeyboardEvent.MOD, e.mod);
                memPutByte(a + SDL_KeyboardEvent.DOWN, (byte) (e.type == EVENT_KEY_DOWN ? 1 : 0));
                memPutByte(a + SDL_KeyboardEvent.REPEAT, (byte) (e.flag ? 1 : 0));
                break;
            case EVENT_TEXT_INPUT:
                lastText = memAddress(memUTF8(e.text, true));
                memPutInt(a + SDL_TextInputEvent.WINDOWID, windowId);
                memPutAddress(a + SDL_TextInputEvent.TEXT, lastText);
                break;
            case EVENT_MOUSE_MOTION:
                memPutInt(a + SDL_MouseMotionEvent.WINDOWID, windowId);
                memPutInt(a + SDL_MouseMotionEvent.STATE, e.i3);
                memPutFloat(a + SDL_MouseMotionEvent.X, e.f1);
                memPutFloat(a + SDL_MouseMotionEvent.Y, e.f2);
                memPutFloat(a + SDL_MouseMotionEvent.XREL, e.f3);
                memPutFloat(a + SDL_MouseMotionEvent.YREL, e.f4);
                break;
            case EVENT_MOUSE_BUTTON_DOWN:
            case EVENT_MOUSE_BUTTON_UP:
                memPutInt(a + SDL_MouseButtonEvent.WINDOWID, windowId);
                memPutByte(a + SDL_MouseButtonEvent.BUTTON, (byte) e.i1);
                memPutByte(a + SDL_MouseButtonEvent.DOWN, (byte) (e.flag ? 1 : 0));
                memPutByte(a + SDL_MouseButtonEvent.CLICKS, (byte) 1);
                memPutFloat(a + SDL_MouseButtonEvent.X, e.f1);
                memPutFloat(a + SDL_MouseButtonEvent.Y, e.f2);
                break;
            case EVENT_MOUSE_WHEEL:
                memPutInt(a + SDL_MouseWheelEvent.WINDOWID, windowId);
                memPutFloat(a + SDL_MouseWheelEvent.X, e.f1);
                memPutFloat(a + SDL_MouseWheelEvent.Y, e.f2);
                memPutFloat(a + SDL_MouseWheelEvent.MOUSE_X, e.f3);
                memPutFloat(a + SDL_MouseWheelEvent.MOUSE_Y, e.f4);
                memPutInt(a + SDL_MouseWheelEvent.INTEGER_X, e.i1);
                memPutInt(a + SDL_MouseWheelEvent.INTEGER_Y, e.i2);
                break;
            case EVENT_QUIT:
                break;
            default: // window events
                memPutInt(a + SDL_WindowEvent.WINDOWID, windowId);
                memPutInt(a + SDL_WindowEvent.DATA1, e.i1);
                memPutInt(a + SDL_WindowEvent.DATA2, e.i2);
        }
    }

    // Display: the iOS screen is the only display
    static final int DISPLAY_ID = 1;
    static final int PIXELFORMAT_XRGB8888 = 0x16161804;
    private static SDL_DisplayMode displayMode;
    private static SDL_PixelFormatDetails formatDetails;

    static float refreshRate() {
        try {
            return Float.parseFloat(System.getProperty("UIScreen.maximumFramesPerSecond", "60"));
        } catch (NumberFormatException e) {
            return 60f;
        }
    }

    static SDL_DisplayMode displayMode() {
        if (displayMode == null) displayMode = SDL_DisplayMode.calloc();
        fillDisplayMode(displayMode.address());
        return displayMode;
    }

    static void fillDisplayMode(long a) {
        int[] size = framebufferSize();
        int refresh = Math.round(refreshRate());
        memPutInt(a + SDL_DisplayMode.DISPLAYID, DISPLAY_ID);
        memPutInt(a + SDL_DisplayMode.FORMAT, PIXELFORMAT_XRGB8888);
        memPutInt(a + SDL_DisplayMode.W, size[0]);
        memPutInt(a + SDL_DisplayMode.H, size[1]);
        memPutFloat(a + SDL_DisplayMode.PIXEL_DENSITY, 1f);
        memPutFloat(a + SDL_DisplayMode.REFRESH_RATE, refresh);
        memPutInt(a + SDL_DisplayMode.REFRESH_RATE_NUMERATOR, refresh);
        memPutInt(a + SDL_DisplayMode.REFRESH_RATE_DENOMINATOR, 1);
    }

    static SDL_PixelFormatDetails formatDetails() {
        if (formatDetails == null) {
            formatDetails = SDL_PixelFormatDetails.create(nmemCalloc(1, SDL_PixelFormatDetails.SIZEOF));
            long a = formatDetails.address();
            memPutInt(a + SDL_PixelFormatDetails.FORMAT, PIXELFORMAT_XRGB8888);
            memPutByte(a + SDL_PixelFormatDetails.BITS_PER_PIXEL, (byte) 24);
            memPutByte(a + SDL_PixelFormatDetails.BYTES_PER_PIXEL, (byte) 4);
            memPutInt(a + SDL_PixelFormatDetails.RMASK, 0x00FF0000);
            memPutInt(a + SDL_PixelFormatDetails.GMASK, 0x0000FF00);
            memPutInt(a + SDL_PixelFormatDetails.BMASK, 0x000000FF);
            memPutByte(a + SDL_PixelFormatDetails.RBITS, (byte) 8);
            memPutByte(a + SDL_PixelFormatDetails.GBITS, (byte) 8);
            memPutByte(a + SDL_PixelFormatDetails.BBITS, (byte) 8);
            memPutByte(a + SDL_PixelFormatDetails.RSHIFT, (byte) 16);
            memPutByte(a + SDL_PixelFormatDetails.GSHIFT, (byte) 8);
        }
        return formatDetails;
    }
}
