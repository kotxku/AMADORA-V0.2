package net.kdt.patchjna;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.function.Function;

/**
 * Building a CrashReport runs every mod's SystemReport/CrashReport mixin hook, and a hook that throws (Handbook reads
 * Minecraft.gui before it exists) replaces the real crash. NativeRedirect's patcher makes CrashReport's constructor
 * call this first, and wraps those hooks so a failing one is logged by HookFailure and skipped.
 */
public class CrashGuard implements Function<Throwable, Object> {
    static final String TAG = "[CrashGuard] ";

    // Called from CrashReport(String, Throwable) through the system class loader, before any report hook runs
    public Object apply(Throwable crash) {
        try {
            // Minecraft builds a throwaway report at startup to preload the classes; that call proves the hook is in
            for (StackTraceElement frame : new Throwable().getStackTrace()) {
                if (frame.getClassName().equals("net.minecraft.CrashReport") && frame.getMethodName().equals("preload")) {
                    System.out.println(TAG + "armed: crash reports will log their original exception first");
                    return null;
                }
            }
            print("original exception, before mods add report details:", crash);
        } catch (Throwable e) {
            System.out.println(TAG + "could not log the original exception: " + e);
        }
        return null;
    }

    static void print(String heading, Throwable t) {
        StringWriter trace = new StringWriter();
        if (t == null) trace.write("null (the crash report was created without an exception)");
        else t.printStackTrace(new PrintWriter(trace));
        StringBuilder out = new StringBuilder(TAG).append(heading).append('\n');
        for (String line : trace.toString().split("\r?\n")) out.append(TAG).append(line).append('\n');
        System.out.print(out); // one write, so lines from other threads don't interleave
        System.out.flush();
    }

    /** Called from the catch-all wrapped around each mod hook in SystemReport, CrashReport and CrashReportCategory. */
    public static class HookFailure implements Function<Throwable, Object> {
        public Object apply(Throwable failure) {
            try {
                String hook = "a mod hook";
                for (StackTraceElement frame : failure.getStackTrace()) {
                    if (frame.getMethodName().startsWith("handler$")) {
                        hook = frame.getClassName() + "." + frame.getMethodName();
                        break;
                    }
                }
                print("skipped " + hook + ", which threw while a crash/system report was built:", failure);
            } catch (Throwable e) {
                System.out.println(TAG + "a crash report hook failed: " + failure);
            }
            return null;
        }
    }
}
