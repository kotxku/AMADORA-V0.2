#!/bin/bash
# Builds SDL3 for iOS arm64 as a signed in-bundle libSDL3.dylib. Controlify binds it through FFM
# (-Ddev.isxander.sdl.library, set by JavaLauncher.m) instead of its desktop builds, which iOS refuses to load.
# usage: build_sdl3_ios.sh <SDL source dir (tag release-3.4.14, the version Controlify's bindings target)> <output.dylib>
set -euo pipefail
SRC="$(cd "$1" && pwd)"
OUT="$2"
BUILD="$SRC/build-ios"

# SDL expects to own main() on iOS (SDL_main / SDL_RunApp) and refuses SDL_Init until then; here it's a library
# inside AMADORA's process, so it starts out ready.
MAIN="$SRC/src/SDL.c"
grep -q 'SDL_MainIsReady = false;' "$MAIN" || { echo "SDL_MainIsReady initializer not found in $MAIN"; exit 1; }
perl -0pi -e 's/static bool SDL_MainIsReady = false;/static bool SDL_MainIsReady = true; \/\* AMADORA: library, not the app \*\//' "$MAIN"
grep -q 'AMADORA: library' "$MAIN" || { echo "SDL_MainIsReady patch failed"; exit 1; }

# HIDAPI would open Bluetooth (no usage description in the app, so iOS kills it) and the camera needs a usage
# description too; controllers come through GameController (MFi), which is all Controlify needs on iOS.
rm -rf "$BUILD"
cmake -S "$SRC" -B "$BUILD" -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=14.0 \
    -DCMAKE_BUILD_TYPE=Release -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST_LIBRARY=OFF -DSDL_TESTS=OFF -DSDL_EXAMPLES=OFF \
    -DSDL_HIDAPI=OFF -DSDL_CAMERA=OFF
cmake --build "$BUILD" --config Release -j "$(sysctl -n hw.ncpu)"

LIB="$(find "$BUILD" -name 'libSDL3*.dylib' -type f | head -1)"
[ -n "$LIB" ] || { echo "no libSDL3 dylib built"; exit 1; }
mkdir -p "$(dirname "$OUT")"
cp "$LIB" "$OUT"
install_name_tool -id @rpath/libSDL3.dylib "$OUT"

# Fail the build rather than ship a macOS binary or one without the gamepad API Controlify binds
otool -l "$OUT" | grep -A4 LC_BUILD_VERSION | grep -q "platform 2" || { echo "not an iOS binary"; exit 1; }
EXPORTS="$(nm -gU "$OUT")" # captured once: grep -q closing a pipe early fails it under pipefail
for sym in SDL_GetVersion SDL_InitSubSystem SDL_GetGamepads SDL_OpenGamepad SDL_GetGamepadAxis SDL_SetHint; do
    grep -q " _$sym\$" <<<"$EXPORTS" || { echo "missing $sym"; exit 1; }
done
echo "Built $OUT"
