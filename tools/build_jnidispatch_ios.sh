#!/bin/bash
# Builds JNA's libjnidispatch for iOS arm64 from a JNA source checkout, so JNA loads a signed in-bundle
# library instead of extracting an unsigned one to Library/Caches (which iOS refuses to dlopen).
# usage: build_jnidispatch_ios.sh <jna-source-dir> <output.dylib>
set -euo pipefail
JNA="$(cd "$1" && pwd)"
OUT="$2"
SDK="$(xcrun --sdk iphoneos --show-sdk-path)"

# JNA's darwin Makefile hardcodes -mmacosx-version-min; drop it so clang builds for iOS
WRAP="$JNA/build/ioscc"
mkdir -p "$JNA/build"
cat > "$WRAP" <<'EOF'
#!/bin/bash
args=()
for a in "$@"; do case "$a" in -mmacosx-version-min=*) ;; *) args+=("$a") ;; esac; done
exec xcrun -sdk iphoneos clang -target arm64-apple-ios14.0 "${args[@]}"
EOF
chmod +x "$WRAP"

# Generates the JNI headers plus the native version and API checksum that Native.java checks at load time
(cd "$JNA" && ant -q -Dbuild-native=true javah)
PROPS="$JNA/build/headers/jni.properties"
VERSION="$(sed -n 's/^version=//p' "$PROPS")"
CHECKSUM="$(sed -n 's/^checksum=//p' "$PROPS")"
echo "JNA native version $VERSION, checksum $CHECKSUM"

# libffi's aarch64 CFI directives trip newer Apple assemblers ("invalid CFI advance_loc"); they only add unwind info
export gcc_cv_as_cfi_pseudo_op=no
# On an Intel host libffi configures as a cross build and its C++ preprocessor check falls back to /lib/cpp
export CXXCPP="$WRAP -E -x c++"
make -C "$JNA/native" OS=darwin ARCH=aarch64 JAVA_HOME="$JAVA_HOME" JAVAH="$JNA/build/headers" \
    CC="$WRAP" SDKROOT="$SDK" BUILD="$JNA/build/ios" HOST_CONFIG=--host=aarch64-apple-ios \
    JNA_JNI_VERSION="$VERSION" CHECKSUM="$CHECKSUM" MAKEINFO=true "$JNA/build/ios/libjnidispatch.jnilib"

mkdir -p "$(dirname "$OUT")"
cp "$JNA/build/ios/libjnidispatch.jnilib" "$OUT"
install_name_tool -id @rpath/libjnidispatch.dylib "$OUT"

# Fail the build rather than ship a macOS or incomplete library
otool -l "$OUT" | grep -A3 LC_BUILD_VERSION | grep -q "platform 2" || { echo "not an iOS binary"; exit 1; }
nm -gU "$OUT" | grep -q _Java_com_sun_jna_Native_getNativeVersion || { echo "missing JNA natives"; exit 1; }
echo "Built $OUT"
