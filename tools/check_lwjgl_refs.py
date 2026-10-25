"""Lists LWJGL methods/fields a Minecraft client.jar references that the bundled LWJGL jars don't provide.
usage: python -I check_lwjgl_refs.py client.jar overlay_classes_dir lwjgl_jar...
Overlay classes (Amethyst's GLFW layer) replace the same classes from the jars, like the Makefile does."""
import os, struct, sys, zipfile


def parse(data):
    """Returns (this, super, members {(name, desc)}, refs {(kind, cls, name, desc)})."""
    cp, i = [None], 10
    count = struct.unpack_from('>H', data, 8)[0]
    while len(cp) < count:
        tag = data[i]
        if tag == 1:
            n = struct.unpack_from('>H', data, i + 1)[0]
            cp.append(data[i + 3:i + 3 + n].decode('utf-8', 'replace')); i += 3 + n
        elif tag in (3, 4):
            cp.append(None); i += 5
        elif tag in (5, 6):
            cp += [None, None]; i += 9
        elif tag in (7, 8, 16, 19, 20):
            cp.append(('c', struct.unpack_from('>H', data, i + 1)[0])); i += 3
        elif tag in (9, 10, 11, 12, 17, 18):
            a, b = struct.unpack_from('>HH', data, i + 1)
            cp.append((tag, a, b)); i += 5
        elif tag == 15:
            cp.append(None); i += 4
        else:
            raise ValueError('bad tag %d' % tag)
    name = lambda idx: cp[cp[idx][1]]
    access, this, sup, nif = struct.unpack_from('>HHHH', data, i); i += 8 + 2 * nif
    members = set()
    for _ in range(2):  # fields, then methods
        n = struct.unpack_from('>H', data, i)[0]; i += 2
        for _ in range(n):
            _, ni, di, ac = struct.unpack_from('>HHHH', data, i); i += 8
            members.add((cp[ni], cp[di]))
            for _ in range(ac):
                i += 6 + struct.unpack_from('>I', data, i + 2)[0]
    refs = set()
    for e in cp:
        if isinstance(e, tuple) and e[0] in (9, 10, 11):
            cls = name(e[1]); nt = cp[e[2]]
            refs.add(('field' if e[0] == 9 else 'method', cls, cp[nt[1]], cp[nt[2]]))
    return name(this), (name(sup) if sup else None), members, refs


def main():
    client, overlay, jars = sys.argv[1], sys.argv[2], sys.argv[3:]
    classes = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.endswith('.class') and not n.startswith('META-INF/'):
                    t, s, m, _ = parse(z.read(n)); classes[t] = (s, m)
    for root, _, files in os.walk(overlay):
        for f in files:
            if f.endswith('.class'):
                t, s, m, _ = parse(open(os.path.join(root, f), 'rb').read()); classes[t] = (s, m)

    def has(cls, n, d):
        while cls in classes:
            sup, members = classes[cls]
            if (n, d) in members:
                return True
            cls = sup
        return cls is not None and not cls.startswith('org/lwjgl/')  # inherited from a JDK class: assume present

    missing = set()
    with zipfile.ZipFile(client) as z:
        for n in z.namelist():
            if n.endswith('.class'):
                for kind, cls, mn, md in parse(z.read(n))[3]:
                    if not cls.startswith('org/lwjgl/'):
                        continue
                    if cls not in classes:
                        missing.add('class  %s' % cls)
                    elif not has(cls, mn, md):
                        missing.add('%-6s %s.%s %s' % (kind, cls, mn, md))
    print('\n'.join(sorted(missing)) or 'all LWJGL references resolve')
    return 1 if missing else 0


if __name__ == '__main__':
    sys.exit(main())
