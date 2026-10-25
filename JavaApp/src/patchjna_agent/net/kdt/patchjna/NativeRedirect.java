package net.kdt.patchjna;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Mods extract their natives to tmp/ or a cache dir and System.load them, which iOS refuses because the copies are
 * unsigned. Every System.load / Runtime.load call in a non-JDK class is rerouted through a synthetic method added to
 * that class: it asks this class for a signed library with the same base name on java.library.path and then calls
 * the original load itself, so the library still binds to the mod's class loader (load is caller-sensitive).
 */
public class NativeRedirect implements Function<String, String> {
    private static final String TAG = "[NativeRedirect] ";
    private static final Set<String> TEMP_DIRS = new HashSet<>(Arrays.asList("tmp", "temp", "cache", "caches", ".cache"));
    private static final Pattern RANDOM_SUFFIX = Pattern.compile("[0-9A-Fa-f-]*");
    private static List<File> signedLibs;

    // Called from the synthetic methods through the system class loader, so Knot's class path isolation never applies.
    // Logs exactly one line per load so a missing line always means the call never reached the hook.
    public String apply(String path) {
        try {
            File file = new File(path);
            List<File> libs = signedLibs();
            if (libs.contains(file.getAbsoluteFile()) || !isTempOrCache(file)) {
                System.out.println(TAG + "pass-through: " + path);
                return path;
            }
            File match = match(file.getName(), libs);
            if (match == null) {
                System.out.println(TAG + "miss: " + path + " has no signed match in java.library.path; this mod needs a bundled library");
                return path;
            }
            System.out.println(TAG + "hit: " + path + " -> " + match);
            return match.getPath();
        } catch (Throwable e) {
            System.out.println(TAG + "error: " + path + " left unchanged: " + e);
            return path;
        }
    }

    // Extractors wrap the original name in random parts: File.createTempFile appends digits ("libzstd-jni_dh-1.5.7-12<digits>"),
    // sqlite-jdbc prefixes a version and UUID ("sqlite-3.53.2.1-<uuid>-libsqlitejdbc"). Longest bundled name wins.
    // shortcut: a bare "libfoo" would also match "libfoo-2<random>"; bundle versioned names, as DH's are, to avoid that
    static File match(String fileName, List<File> libs) {
        String name = stripExtension(fileName);
        File best = null;
        int bestLength = 0;
        for (File lib : libs) {
            String base = stripExtension(lib.getName());
            // Some extractors drop the "lib" prefix; too short a key would match unrelated names
            String[] keys = base.startsWith("lib") && base.length() >= 7 ? new String[]{base, base.substring(3)} : new String[]{base};
            for (String key : keys) {
                if (key.length() > bestLength && occursAsName(name, key)) {
                    best = lib;
                    bestLength = key.length();
                }
            }
        }
        return best;
    }

    private static boolean occursAsName(String name, String key) {
        for (int i = name.indexOf(key); i >= 0; i = name.indexOf(key, i + 1)) {
            boolean atBoundary = i == 0 || "-_.".indexOf(name.charAt(i - 1)) >= 0;
            if (atBoundary && RANDOM_SUFFIX.matcher(name.substring(i + key.length())).matches()) return true;
        }
        return false;
    }

    private static boolean isTempOrCache(File file) {
        String tmp = System.getProperty("java.io.tmpdir");
        File tmpDir = tmp == null || tmp.isEmpty() ? null : new File(tmp).getAbsoluteFile();
        if (tmpDir != null && tmpDir.getParent() != null && file.getAbsolutePath().startsWith(tmpDir.getPath())) return true;
        for (File dir = file.getAbsoluteFile().getParentFile(); dir != null; dir = dir.getParentFile()) {
            if (TEMP_DIRS.contains(dir.getName().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static synchronized List<File> signedLibs() {
        if (signedLibs == null) {
            signedLibs = new ArrayList<>();
            for (String dir : System.getProperty("java.library.path", "").split(File.pathSeparator)) {
                File[] files = dir.isEmpty() ? null : new File(dir).getAbsoluteFile().listFiles();
                if (files == null) continue;
                for (File f : files) {
                    String n = f.getName();
                    if (n.endsWith(".dylib") || n.endsWith(".jnilib") || n.endsWith(".so")) signedLibs.add(f);
                }
            }
        }
        return signedLibs;
    }

    // ---- class file patching ----

    private static final String STRING_DESC = "(Ljava/lang/String;)V";
    private static final String RUNTIME_DESC = "(Ljava/lang/Runtime;Ljava/lang/String;)V";
    private static final String THROWABLE_DESC = "(Ljava/lang/Throwable;)V";
    // Mixin merges mod hooks into these as handler$... methods; Minecraft 26.x is unobfuscated, so the names are stable
    private static final Set<String> CRASH_CLASSES = new HashSet<>(Arrays.asList(
            "net/minecraft/CrashReport", "net/minecraft/SystemReport", "net/minecraft/CrashReportCategory"));
    private static final byte[] INSN_LENGTH = new byte[202];
    static {
        Arrays.fill(INSN_LENGTH, (byte) 1);
        for (int op : new int[]{0x10, 0x12, 0x15, 0x16, 0x17, 0x18, 0x19, 0x36, 0x37, 0x38, 0x39, 0x3a, 0xa9, 0xbc}) INSN_LENGTH[op] = 2;
        for (int op = 0x99; op <= 0xa8; op++) INSN_LENGTH[op] = 3;
        for (int op = 0xb2; op <= 0xb8; op++) INSN_LENGTH[op] = 3;
        for (int op : new int[]{0x11, 0x13, 0x14, 0x84, 0xbb, 0xbd, 0xc0, 0xc1, 0xc6, 0xc7}) INSN_LENGTH[op] = 3;
        INSN_LENGTH[0xc5] = 4;
        for (int op : new int[]{0xb9, 0xba, 0xc8, 0xc9}) INSN_LENGTH[op] = 5;
    }

    /** Returns the patched class, or null when the class has no System.load / Runtime.load call sites. */
    static byte[] transform(ClassLoader loader, String className, byte[] original) {
        if (loader == null || className == null || className.startsWith("java/") || className.startsWith("jdk/")
                || className.startsWith("sun/") || className.startsWith("com/sun/") || className.startsWith("net/kdt/patchjna/")) {
            return null;
        }
        try {
            if (CRASH_CLASSES.contains(className)) { // none of these load natives
                return new Patcher(original.clone()).guardCrashReports(className, className.equals("net/minecraft/CrashReport"));
            }
            byte[] patched = new Patcher(original.clone()).patch();
            if (patched != null) System.out.println(TAG + "hooked native loading in " + className);
            return patched;
        } catch (Throwable e) {
            System.out.println(TAG + "could not patch " + className + ": " + e);
            return null;
        }
    }

    private static final class Patcher {
        final byte[] b;
        final int[] cp;
        int cpEnd;
        final ByteArrayOutputStream extraBytes = new ByteArrayOutputStream();
        final DataOutputStream extra = new DataOutputStream(extraBytes);
        int nextIndex;

        Patcher(byte[] b) {
            this.b = b;
            cp = new int[u2(8)];
            int o = 10;
            for (int i = 1; i < cp.length; i++) {
                cp[i] = o;
                int tag = b[o] & 0xff;
                switch (tag) {
                    case 1: o += 3 + u2(o + 1); break;
                    case 7: case 8: case 16: case 19: case 20: o += 3; break;
                    case 15: o += 4; break;
                    case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: o += 5; break;
                    case 5: case 6: o += 9; i++; break;
                    default: throw new IllegalStateException("constant pool tag " + tag);
                }
            }
            cpEnd = o;
            nextIndex = cp.length;
        }

        byte[] patch() throws IOException {
            Set<Integer> systemRefs = new HashSet<>(), runtimeRefs = new HashSet<>();
            for (int i = 1; i < cp.length; i++) {
                if (cp[i] == 0 || b[cp[i]] != 10) continue;
                int nat = cp[u2(cp[i] + 3)];
                if (!utf8(u2(nat + 1)).equals("load") || !utf8(u2(nat + 3)).equals(STRING_DESC)) continue;
                String owner = utf8(u2(cp[u2(cp[i] + 1)] + 1));
                if (owner.equals("java/lang/System")) systemRefs.add(i);
                if (owner.equals("java/lang/Runtime")) runtimeRefs.add(i);
            }
            // A Runtime::load method handle is an instance reference; it cannot point at the static replacement
            for (int i = 1; i < cp.length; i++) {
                if (cp[i] != 0 && b[cp[i]] == 15 && runtimeRefs.contains(u2(cp[i] + 2))) runtimeRefs.clear();
            }
            int access = u2(cpEnd);
            if ((systemRefs.isEmpty() && runtimeRefs.isEmpty()) || (access & 0x0200) != 0) return null;
            int thisClass = u2(cpEnd + 2);

            int string = classRef("java/lang/String");
            int code = utf8("Code");
            // Pushes (String) NativeRedirect.apply(<local slot>); the slot byte at [15] is filled in per method
            byte[] call = callFunction(NativeRedirect.class.getName(), 0);
            byte[] redirect = Arrays.copyOf(call, call.length + 3);
            redirect[call.length] = (byte) 0xc0; redirect[call.length + 1] = hi(string); redirect[call.length + 2] = lo(string);

            int methodsCount = skipMembers(cpEnd + 8 + 2 * u2(cpEnd + 6));
            int methodsEnd = skipMembers(methodsCount);
            if (!runtimeRefs.isEmpty()) patchInvokeVirtual(runtimeRefs); // before the methods are copied
            ByteArrayOutputStream methods = new ByteArrayOutputStream();
            methods.write(b, methodsCount + 2, methodsEnd - methodsCount - 2);
            int added = 0;
            if (!systemRefs.isEmpty()) {
                int load = memberRef(10, classRef("java/lang/System"), "load", STRING_DESC);
                int name = utf8("amadora$load"), desc = utf8(STRING_DESC);
                retarget(systemRefs, thisClass, constant(12, name, desc));
                redirect[15] = 0x2a; // aload_0: path
                methods.write(method(name, desc, code, 2, 1, redirect, new byte[]{(byte) 0xb8, hi(load), lo(load), (byte) 0xb1}));
                added++;
            }
            if (!runtimeRefs.isEmpty()) {
                int load = memberRef(10, classRef("java/lang/Runtime"), "load", STRING_DESC);
                int name = utf8("amadora$runtimeLoad"), desc = utf8(RUNTIME_DESC);
                retarget(runtimeRefs, thisClass, constant(12, name, desc));
                redirect[15] = 0x2b; // aload_1: path, under the Runtime pushed by aload_0
                byte[] body = new byte[redirect.length + 1];
                body[0] = 0x2a;
                System.arraycopy(redirect, 0, body, 1, redirect.length);
                methods.write(method(name, desc, code, 3, 2, body, new byte[]{(byte) 0xb6, hi(load), lo(load), (byte) 0xb1}));
                added++;
            }
            return assemble(methodsCount, methodsEnd, methods, u2(methodsCount) + added);
        }

        /**
         * Wraps each void mixin handler (handler$...) in a catch-all that logs through CrashGuard.HookFailure and returns,
         * and for CrashReport makes the (String, Throwable) constructor hand its exception to CrashGuard before anything else.
         */
        byte[] guardCrashReports(String className, boolean logOriginal) throws IOException {
            int thisClass = u2(cpEnd + 2);
            int desc = utf8(THROWABLE_DESC);
            int logName = utf8("amadora$logCrash"), hookName = utf8("amadora$hookFailed");
            int logRef = constant(10, thisClass, constant(12, logName, desc));
            int hookRef = constant(10, thisClass, constant(12, hookName, desc));
            int throwable = classRef("java/lang/Throwable");
            int stackMap = utf8("StackMapTable");

            int methodsCount = skipMembers(cpEnd + 8 + 2 * u2(cpEnd + 6));
            int methodsEnd = skipMembers(methodsCount);
            ByteArrayOutputStream methods = new ByteArrayOutputStream();
            int wrapped = 0;
            boolean logged = false;
            for (int i = 0, o = methodsCount + 2; i < u2(methodsCount); i++) {
                String name = utf8(u2(o + 2)), mdesc = utf8(u2(o + 4));
                boolean wrap = name.startsWith("handler$") && mdesc.endsWith(")V");
                boolean prologue = logOriginal && name.equals("<init>") && mdesc.equals("(Ljava/lang/String;Ljava/lang/Throwable;)V");
                int attrs = u2(o + 6);
                methods.write(b, o, 8);
                o += 8;
                for (int a = 0; a < attrs; a++) {
                    int len = 6 + u4(o + 2);
                    byte[] code = !utf8(u2(o)).equals("Code") ? null
                            : wrap ? wrapCode(o, hookRef, throwable, stackMap) : prologue ? prologue(o, logRef) : null;
                    if (code != null) {
                        methods.write(code);
                        if (wrap) wrapped++; else logged = true;
                    } else {
                        methods.write(b, o, len);
                    }
                    o += len;
                }
            }
            int codeName = utf8("Code");
            int added = 0;
            byte[] call = callFunction(CrashGuard.class.getName(), 0x2a);
            byte[] popReturn = {0x57, (byte) 0xb1};
            if (logged) { methods.write(method(logName, desc, codeName, 2, 1, call, popReturn)); added++; }
            byte[] hookCall = callFunction(CrashGuard.HookFailure.class.getName(), 0x2a);
            if (wrapped > 0) { methods.write(method(hookName, desc, codeName, 2, 1, hookCall, popReturn)); added++; }
            if (added == 0 || nextIndex > 0xffff) return null;
            System.out.println(CrashGuard.TAG + "guarding " + className + ": " + wrapped + " mod hook(s) wrapped"
                    + (logged ? ", original exception logged first" : ""));
            return assemble(methodsCount, methodsEnd, methods, u2(methodsCount) + added);
        }

        // Code attribute layout at o: name u2, length u4, max_stack u2, max_locals u2, code_length u4, code, exceptions, attributes
        private byte[] wrapCode(int o, int hookRef, int throwable, int stackMapName) throws IOException {
            int codeLen = u4(o + 10), code = o + 14, exc = code + codeLen, excCount = u2(exc), attrs = exc + 2 + 8 * excCount;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream c = new DataOutputStream(bytes);
            c.writeShort(Math.max(u2(o + 6), 1)); c.writeShort(u2(o + 8));
            c.writeInt(codeLen + 4);
            c.write(b, code, codeLen);
            c.write(0xb8); c.writeShort(hookRef); c.write(0xb1); // amadora$hookFailed(caught); return
            c.writeShort(excCount + 1);
            c.write(b, exc + 2, 8 * excCount);
            c.writeShort(0); c.writeShort(codeLen); c.writeShort(codeLen); c.writeShort(0); // last, so the hook's own handlers win

            int mapAt = -1, n = u2(attrs);
            for (int a = 0, p = attrs + 2; a < n; a++, p += 6 + u4(p + 2)) if (utf8(u2(p)).equals("StackMapTable")) mapAt = p;
            int lastPc = mapAt < 0 ? -1 : lastFramePc(mapAt);
            // full_frame at the handler: no locals, the caught Throwable on the stack
            byte[] frame = {(byte) 255, hi(codeLen - lastPc - 1), lo(codeLen - lastPc - 1), 0, 0, 0, 1, 7, hi(throwable), lo(throwable)};
            c.writeShort(mapAt < 0 ? n + 1 : n);
            for (int a = 0, p = attrs + 2; a < n; a++, p += 6 + u4(p + 2)) {
                if (p != mapAt) { c.write(b, p, 6 + u4(p + 2)); continue; }
                c.writeShort(u2(p)); c.writeInt(u4(p + 2) + frame.length);
                c.writeShort(u2(p + 6) + 1);
                c.write(b, p + 8, u4(p + 2) - 2);
                c.write(frame);
            }
            if (mapAt < 0) { c.writeShort(stackMapName); c.writeInt(2 + frame.length); c.writeShort(1); c.write(frame); }
            return codeAttribute(o, bytes);
        }

        // Inserts "aload_2; invokestatic amadora$logCrash" at the start; 4 bytes keep tableswitch padding unchanged.
        // Returns null when the code carries an attribute with offsets this does not know how to shift.
        private byte[] prologue(int o, int logRef) throws IOException {
            int codeLen = u4(o + 10), code = o + 14, exc = code + codeLen, excCount = u2(exc), attrs = exc + 2 + 8 * excCount;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream c = new DataOutputStream(bytes);
            c.writeShort(Math.max(u2(o + 6), 1)); c.writeShort(u2(o + 8));
            c.writeInt(codeLen + 4);
            c.write(0x2c); c.write(0xb8); c.writeShort(logRef);
            c.write(b, code, codeLen);
            c.writeShort(excCount);
            for (int e = exc + 2; e < attrs; e += 8) {
                c.writeShort(u2(e) + 4); c.writeShort(u2(e + 2) + 4); c.writeShort(u2(e + 4) + 4); c.writeShort(u2(e + 6));
            }
            int n = u2(attrs);
            c.writeShort(n);
            for (int a = 0, p = attrs + 2; a < n; a++, p += 6 + u4(p + 2)) {
                String name = utf8(u2(p));
                int end = p + 6 + u4(p + 2);
                if (name.equals("LineNumberTable")) {
                    c.write(b, p, 8);
                    for (int e = p + 8; e < end; e += 4) { c.writeShort(u2(e) + 4); c.writeShort(u2(e + 2)); }
                } else if (name.equals("LocalVariableTable") || name.equals("LocalVariableTypeTable")) {
                    c.write(b, p, 8);
                    for (int e = p + 8; e < end; e += 10) {
                        boolean fromStart = u2(e) == 0; // parameters stay in scope over the inserted code
                        c.writeShort(fromStart ? 0 : u2(e) + 4); c.writeShort(u2(e + 2) + (fromStart ? 4 : 0));
                        c.write(b, e + 4, 6);
                    }
                } else if (name.equals("StackMapTable")) {
                    ByteArrayOutputStream frames = new ByteArrayOutputStream();
                    int f = p + 8, type = u2(p + 6) == 0 ? -1 : b[f] & 0xff, rest;
                    if (type < 0) rest = f;
                    else if (type < 128) { // same_frame / same_locals_1_stack_item: the delta lives in the type byte
                        int delta = (type & 63) + 4;
                        if (delta < 64) frames.write(type < 64 ? delta : 64 + delta);
                        else { frames.write(type < 64 ? 251 : 247); frames.write(hi(delta)); frames.write(lo(delta)); }
                        rest = f + 1;
                    } else if (type >= 247) {
                        frames.write(type); frames.write(hi(u2(f + 1) + 4)); frames.write(lo(u2(f + 1) + 4));
                        rest = f + 3;
                    } else return null;
                    frames.write(b, rest, end - rest);
                    c.writeShort(u2(p)); c.writeInt(2 + frames.size()); c.writeShort(u2(p + 6));
                    frames.writeTo(c);
                } else {
                    return null;
                }
            }
            return codeAttribute(o, bytes);
        }

        private byte[] codeAttribute(int o, ByteArrayOutputStream body) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream c = new DataOutputStream(bytes);
            c.writeShort(u2(o)); c.writeInt(body.size());
            body.writeTo(c);
            return bytes.toByteArray();
        }

        private int lastFramePc(int map) {
            int pc = -1;
            for (int i = 0, n = u2(map + 6), f = map + 8; i < n; i++) {
                int type = b[f] & 0xff;
                int delta = type < 64 ? type : type < 128 ? type - 64 : u2(f + 1);
                if (type < 64) f += 1;
                else if (type < 128) f = skipTypes(f + 1, 1);
                else if (type == 247) f = skipTypes(f + 3, 1);
                else if (type >= 248 && type <= 251) f += 3;
                else if (type >= 252 && type <= 254) f = skipTypes(f + 3, type - 251);
                else if (type == 255) { f = skipTypes(f + 5, u2(f + 3)); f = skipTypes(f + 2, u2(f)); }
                else throw new IllegalStateException("stack map frame type " + type);
                pc += delta + 1;
            }
            return pc;
        }

        private int skipTypes(int f, int n) {
            for (; n > 0; n--) f += (b[f] & 0xff) >= 7 ? 3 : 1; // Object and Uninitialized carry a u2
            return f;
        }

        // Pushes new <className>().apply(<value pushed by the aload opcode>) through the system class loader, so the
        // patched class never links against the agent and Knot's class path isolation never applies
        private byte[] callFunction(String className, int aload) throws IOException {
            int classLoader = classRef("java/lang/ClassLoader");
            int getSystemLoader = memberRef(10, classLoader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
            int loadClass = memberRef(10, classLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
            int newInstance = memberRef(10, classRef("java/lang/Class"), "newInstance", "()Ljava/lang/Object;");
            int function = classRef("java/util/function/Function");
            int apply = memberRef(11, function, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
            int name = constant(8, utf8(className));
            return new byte[]{
                    (byte) 0xb8, hi(getSystemLoader), lo(getSystemLoader), 0x13, hi(name), lo(name),
                    (byte) 0xb6, hi(loadClass), lo(loadClass), (byte) 0xb6, hi(newInstance), lo(newInstance),
                    (byte) 0xc0, hi(function), lo(function), (byte) aload, (byte) 0xb9, hi(apply), lo(apply), 2, 0};
        }

        // Header, constant pool plus the appended constants, everything up to the methods, then the given methods
        private byte[] assemble(int methodsCount, int methodsEnd, ByteArrayOutputStream methods, int count) throws IOException {
            if (nextIndex > 0xffff) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + extraBytes.size() + methods.size());
            out.write(b, 0, 8);
            out.write(hi(nextIndex)); out.write(lo(nextIndex));
            out.write(b, 10, cpEnd - 10);
            extraBytes.writeTo(out);
            out.write(b, cpEnd, methodsCount - cpEnd);
            out.write(hi(count)); out.write(lo(count));
            methods.writeTo(out);
            out.write(b, methodsEnd, b.length - methodsEnd);
            return out.toByteArray();
        }

        private void retarget(Set<Integer> refs, int owner, int nameAndType) {
            for (int i : refs) {
                b[cp[i] + 1] = hi(owner); b[cp[i] + 2] = lo(owner);
                b[cp[i] + 3] = hi(nameAndType); b[cp[i] + 4] = lo(nameAndType);
            }
        }

        // invokevirtual Runtime.load(String) becomes invokestatic amadora$runtimeLoad(Runtime, String): same stack, same length
        private void patchInvokeVirtual(Set<Integer> refs) {
            int methods = skipMembers(cpEnd + 8 + 2 * u2(cpEnd + 6));
            for (int m = 0, o = methods + 2; m < u2(methods); m++) {
                int attrs = u2(o + 6);
                o += 8;
                for (int a = 0; a < attrs; a++) {
                    int len = u4(o + 2);
                    if (utf8(u2(o)).equals("Code")) {
                        int start = o + 14, end = start + u4(o + 10);
                        for (int pc = 0; start + pc < end; pc += insnLength(start, pc)) {
                            if ((b[start + pc] & 0xff) == 0xb6 && refs.contains(u2(start + pc + 1))) b[start + pc] = (byte) 0xb8;
                        }
                    }
                    o += 6 + len;
                }
            }
        }

        private int insnLength(int code, int pc) {
            int op = b[code + pc] & 0xff;
            int pad = ((pc + 4) & ~3) - pc;
            if (op == 0xaa) return pad + 12 + 4 * (u4(code + pc + pad + 8) - u4(code + pc + pad + 4) + 1);
            if (op == 0xab) return pad + 8 + 8 * u4(code + pc + pad + 4);
            if (op == 0xc4) return (b[code + pc + 1] & 0xff) == 0x84 ? 6 : 4;
            if (op >= INSN_LENGTH.length) throw new IllegalStateException("opcode " + op);
            return INSN_LENGTH[op];
        }

        private int skipMembers(int o) {
            int n = u2(o);
            o += 2;
            for (int i = 0; i < n; i++) {
                int attrs = u2(o + 6);
                o += 8;
                for (int a = 0; a < attrs; a++) o += 6 + u4(o + 2);
            }
            return o;
        }

        private static byte[] method(int name, int desc, int code, int maxStack, int maxLocals, byte[] prefix, byte[] suffix) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream m = new DataOutputStream(bytes);
            m.writeShort(0x100a); // private static synthetic
            m.writeShort(name); m.writeShort(desc); m.writeShort(1);
            m.writeShort(code); m.writeInt(12 + prefix.length + suffix.length);
            m.writeShort(maxStack); m.writeShort(maxLocals); m.writeInt(prefix.length + suffix.length);
            m.write(prefix); m.write(suffix);
            m.writeShort(0); m.writeShort(0); // no exception table, no attributes
            return bytes.toByteArray();
        }

        private int utf8(String s) throws IOException {
            extra.writeByte(1);
            extra.writeUTF(s);
            return nextIndex++;
        }

        private int constant(int tag, int... refs) throws IOException {
            extra.writeByte(tag);
            for (int r : refs) extra.writeShort(r);
            return nextIndex++;
        }

        private int classRef(String name) throws IOException { return constant(7, utf8(name)); }

        private int memberRef(int tag, int owner, String name, String desc) throws IOException {
            return constant(tag, owner, constant(12, utf8(name), utf8(desc)));
        }

        private String utf8(int index) {
            return new String(b, cp[index] + 3, u2(cp[index] + 1), StandardCharsets.ISO_8859_1);
        }

        private int u2(int o) { return ((b[o] & 0xff) << 8) | (b[o + 1] & 0xff); }
        private int u4(int o) { return (u2(o) << 16) | u2(o + 2); }
        private static byte hi(int v) { return (byte) (v >> 8); }
        private static byte lo(int v) { return (byte) v; }
    }
}
