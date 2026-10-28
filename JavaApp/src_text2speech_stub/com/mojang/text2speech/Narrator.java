package com.mojang.text2speech;

public interface Narrator {
    // text2speech 1.17+ (Minecraft 1.21+ and 26.x) API, alongside the older one
    Narrator EMPTY = new NarratorDummy();

    void say(final String msg, final boolean interrupt);

    default void say(final String msg, final boolean interrupt, final float volume) {
        say(msg, interrupt);
    }

    void clear();

    boolean active();

    void destroy();

    static Narrator getNarrator() {
        return new NarratorDummy();
    }

    static void setJNAPath(String sep) {
        System.setProperty("jna.library.path", System.getProperty("jna.library.path") + sep + "./src/natives/resources/");
        System.setProperty("jna.library.path", System.getProperty("jna.library.path") + sep + System.getProperty("java.library.path"));
    }

    class InitializeException extends Exception {
        public InitializeException(String message, Throwable cause) {
            super(message, cause);
        }

        public InitializeException(String message) {
            super(message);
        }
    }

    class FatalException extends RuntimeException {
        public FatalException(String message) {
            super(message);
        }
    }
}
