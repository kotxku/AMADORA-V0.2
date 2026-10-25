#!/bin/bash
# Builds the zstd-jni copy that Distant Horizons shades to dhcomgithubluben.zstd, for iOS arm64, so DH's
# System.loadLibrary fallback finds a signed in-bundle library instead of the unsigned one it extracts to tmp/.
# usage: build_zstd_jni_ios.sh <zstd-jni-source-dir (tag v1.5.7-12)> <output.dylib>
set -euo pipefail
SRC="$(cd "$1" && pwd)"
OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$SRC/build/ios-dh"

# DH relocates com.github.luben.zstd -> dhcomgithubluben.zstd; apply the same rename to every JNI symbol and class string
rm -rf "$WORK" && mkdir -p "$WORK" && cp -R "$SRC/src/main/native/." "$WORK/"
grep -rl luben "$WORK" | xargs sed -i '' -e 's/com_github_luben_zstd_/dhcomgithubluben_zstd_/g' \
    -e 's#com/github/luben/zstd/#dhcomgithubluben/zstd/#g'
if grep -rnE 'com_github_luben|com/github/luben' "$WORK"; then echo "unrelocated references left"; exit 1; fi

# Same defines and source set as upstream make_so.sh; huf_decompress_amd64.S is x86-only
echo '_Java_dhcomgithubluben_zstd_*' > "$WORK/exported.txt"
mkdir -p "$(dirname "$OUT")"
xcrun -sdk iphoneos clang -target arm64-apple-ios14.0 -dynamiclib -O3 -flto -fPIC -std=c99 \
    -DZSTD_LEGACY_SUPPORT=4 -DZSTD_MULTITHREAD=1 \
    -I"$SRC/jni" -I"$WORK" -I"$WORK/common" -I"$WORK/legacy" \
    -exported_symbols_list "$WORK/exported.txt" -install_name @rpath/libzstd-jni_dh-1.5.7-12.dylib \
    -o "$OUT" "$WORK"/*.c "$WORK"/legacy/*.c "$WORK"/common/*.c "$WORK"/compress/*.c \
    "$WORK"/decompress/*.c "$WORK"/dictBuilder/*.c -lpthread

# Fail the build rather than ship a macOS binary or one whose exports differ from DH's own darwin/aarch64 library
otool -l "$OUT" | grep -A4 LC_BUILD_VERSION | grep -q "platform 2" || { echo "not an iOS binary"; exit 1; }
diff <(nm -gU "$OUT" | awk '{print $3}' | sort -u) "$HERE/dh_zstd_jni_symbols.txt" || { echo "exports differ from DH's zstd-jni"; exit 1; }
strings "$OUT" | grep 'dhcomgithubluben/zstd/ZstdFrameProgression' >/dev/null || { echo "FindClass string not relocated"; exit 1; }
echo "Built $OUT"
