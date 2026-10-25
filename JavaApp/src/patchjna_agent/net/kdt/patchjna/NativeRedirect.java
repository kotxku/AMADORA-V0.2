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

/**
 * Mods extract their natives to tmp/ or a cache dir and System.load them, which iOS refuses because the copies are
 * unsigned. Every System.load / Runtime.load call in a non-JDK class is rerouted through a synthetic method added to
 * that class: it asks this class for a signed library with the same base name on java.library.path and then calls
 * the original load itself, so the library still binds to the mod's class loader (load is caller-sensitive).
 */
public class NativeRedirect implements Function<String, String> {
    private static final String TAG = "[NativeRedirect] ";
    private static final Set<String> TEMP_DIRS = new HashSet<>(Arrays.asList("tmp", "temp", "cache", "caches", ".cache"));
    private static List<File> signedLibs;

    // Called from the synthetic methods through the system class loader, so Knot's class path isolation never applies
    public String apply(String path) {
        File file = new File(path);
        List<File> libs = signedLibs();
        if (!isTempOrCache(file) || libs.contains(file.getAbsoluteFile())) return path;
        String name = stripExtension(file.getName());
        File best = null;
        for (File lib : libs) {
            String libName = stripExtension(lib.getName());
            // File.createTempFile and most extractors append digits, hex or a UUID to the original name.
            // shortcut: a bare "libfoo" would also match "libfoo-2<random>"; bundle versioned names, as DH's are, to avoid that
            if (name.startsWith(libName) && name.substring(libName.length()).matches("[0-9A-Fa-f-]*")
                    && (best == null || libName.length() > stripExtension(best.getName()).length())) {
                best = lib;
            }
        }
        if (best == null) {
            System.out.println(TAG + "miss: " + path + " has no signed match for '" + name
                    + "' in java.library.path; this mod needs a bundled library");
            return path;
        }
        System.out.println(TAG + path + " -> " + best);
        return best.getPath();
    }

    private static boolean isTempOrCache(File file) {
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

            // Constants used by the synthetic method bodies
            int classLoader = classRef("java/lang/ClassLoader");
            int getSystemLoader = memberRef(10, classLoader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
            int loadClass = memberRef(10, classLoader, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
            int newInstance = memberRef(10, classRef("java/lang/Class"), "newInstance", "()Ljava/lang/Object;");
            int function = classRef("java/util/function/Function");
            int apply = memberRef(11, function, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
            int string = classRef("java/lang/String");
            int redirectName = constant(8, utf8(NativeRedirect.class.getName()));
            int code = utf8("Code");
            // Pushes NativeRedirect.apply(<local slot>) without linking against NativeRedirect
            byte[] redirect = {
                    (byte) 0xb8, hi(getSystemLoader), lo(getSystemLoader), 0x13, hi(redirectName), lo(redirectName),
                    (byte) 0xb6, hi(loadClass), lo(loadClass), (byte) 0xb6, hi(newInstance), lo(newInstance),
                    (byte) 0xc0, hi(function), lo(function), 0, (byte) 0xb9, hi(apply), lo(apply), 2, 0,
                    (byte) 0xc0, hi(string), lo(string)};

            ByteArrayOutputStream methods = new ByteArrayOutputStream();
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
                patchInvokeVirtual(runtimeRefs);
                redirect[15] = 0x2b; // aload_1: path, under the Runtime pushed by aload_0
                byte[] body = new byte[redirect.length + 1];
                body[0] = 0x2a;
                System.arraycopy(redirect, 0, body, 1, redirect.length);
                methods.write(method(name, desc, code, 3, 2, body, new byte[]{(byte) 0xb6, hi(load), lo(load), (byte) 0xb1}));
                added++;
            }
            if (nextIndex > 0xffff) return null;

            int methodsCount = skipMembers(cpEnd + 8 + 2 * u2(cpEnd + 6));
            int methodsEnd = skipMembers(methodsCount);
            ByteArrayOutputStream out = new ByteArrayOutputStream(b.length + extraBytes.size() + methods.size());
            out.write(b, 0, 8);
            out.write(hi(nextIndex)); out.write(lo(nextIndex));
            out.write(b, 10, cpEnd - 10);
            extraBytes.writeTo(out);
            out.write(b, cpEnd, methodsCount - cpEnd);
            int count = u2(methodsCount) + added;
            out.write(hi(count)); out.write(lo(count));
            out.write(b, methodsCount + 2, methodsEnd - methodsCount - 2);
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
