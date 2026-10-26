#!/bin/bash
# Builds OSMesa with Zink from a current Mesa for iOS arm64. The Payload step copies Natives/build/*.dylib over
# Natives/resources/Frameworks, replacing the Mesa 21.0 build whose Zink cannot compile Minecraft 26.x's shaders.
# usage: build_mesa_ios.sh <mesa-source (tag mesa-25.0.7, the last with OSMesa)> <MoltenVK-source (tag v1.2.9)> <output-dir>
set -euo pipefail
SRC="$(cd "$1" && pwd)"
MVK="$(cd "$2" && pwd)"
OUT="$3"
BUILD="$SRC/build-ios"

# Zink dlopens the Vulkan loader by name; an iOS app has none, so it loads the MoltenVK bundled next to libOSMesa
sed -i '' 's|#define VK_LIBNAME "libvulkan.1.dylib"|#define VK_LIBNAME "@loader_path/libMoltenVK.dylib"|' \
    "$SRC/src/gallium/drivers/zink/zink_screen.c"
grep -q '@loader_path/libMoltenVK.dylib' "$SRC/src/gallium/drivers/zink/zink_screen.c" || { echo "VK_LIBNAME not patched"; exit 1; }

SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
CC="$(xcrun --sdk iphoneos -f clang)"
CXX="$(xcrun --sdk iphoneos -f clang++)"
# MoltenVK's headers turn on the Metal and iOS surface platforms, but only after some files already included
# vulkan.h without them; defining them everywhere keeps the generated dispatch tables and vulkan.h in agreement
FLAGS="'-arch', 'arm64', '-isysroot', '$SDK', '-miphoneos-version-min=14.0', '-DVK_USE_PLATFORM_METAL_EXT', '-DVK_USE_PLATFORM_IOS_MVK'"
cat > "$SRC/ios-cross.txt" <<EOF
[binaries]
c = '$CC'
cpp = '$CXX'
objc = '$CC'
ar = '$(xcrun --sdk iphoneos -f ar)'
strip = '$(xcrun --sdk iphoneos -f strip)'

[built-in options]
c_args = [$FLAGS]
cpp_args = [$FLAGS]
objc_args = [$FLAGS]
c_link_args = [$FLAGS, '-framework', 'Foundation', '-framework', 'IOSurface', '-framework', 'Metal', '-framework', 'QuartzCore']
cpp_link_args = [$FLAGS, '-framework', 'Foundation', '-framework', 'IOSurface', '-framework', 'Metal', '-framework', 'QuartzCore']

[host_machine]
system = 'darwin'
subsystem = 'ios'
kernel = 'xnu'
cpu_family = 'aarch64'
cpu = 'arm64'
endian = 'little'
EOF

rm -rf "$BUILD"
# OSMesa refuses to build without a software rasterizer; the launcher picks Zink with GALLIUM_DRIVER=zink
meson setup "$BUILD" "$SRC" --cross-file "$SRC/ios-cross.txt" \
    -Dbuildtype=release -Db_ndebug=true \
    -Dgallium-drivers=softpipe,zink -Dvulkan-drivers= -Dplatforms= \
    -Dosmesa=true -Dopengl=true -Dshared-glapi=enabled \
    -Dglx=disabled -Degl=disabled -Dgbm=disabled -Dgles1=disabled -Dgles2=disabled -Dglvnd=disabled \
    -Dllvm=disabled -Dvalgrind=disabled -Dlibunwind=disabled -Dlmsensors=disabled -Dzstd=disabled \
    -Dexpat=disabled -Dxmlconfig=disabled -Dshader-cache=disabled -Dperfetto=false \
    -Dgallium-vdpau=disabled -Dgallium-va=disabled -Dgallium-xa=disabled -Dgallium-nine=false \
    -Dgallium-opencl=disabled -Dgallium-rusticl=false -Dvideo-codecs= -Dtools= -Dbuild-tests=false \
    -Dmoltenvk-dir="$MVK/MoltenVK"
ninja -C "$BUILD"

mkdir -p "$OUT"
OSMESA="$(find "$BUILD" -name 'libOSMesa.8.dylib' -type f | head -1)"
GLAPI="$(find "$BUILD" -name 'libglapi.0.dylib' -type f | head -1)"
cp "$OSMESA" "$OUT/libOSMesa.8.dylib"
cp "$GLAPI" "$OUT/libglapi.0.dylib"
# Same install names as the Mesa 21.0 build, so dlopen("@rpath/libOSMesa.8.dylib") and its glapi link resolve in Frameworks
install_name_tool -id @rpath/libOSMesa.8.dylib "$OUT/libOSMesa.8.dylib"
install_name_tool -id @rpath/libglapi.0.dylib "$OUT/libglapi.0.dylib"
OLD_GLAPI="$(otool -L "$OUT/libOSMesa.8.dylib" | awk '/libglapi/ {print $1}')"
[ "$OLD_GLAPI" = "@rpath/libglapi.0.dylib" ] || install_name_tool -change "$OLD_GLAPI" @rpath/libglapi.0.dylib "$OUT/libOSMesa.8.dylib"
otool -l "$OUT/libOSMesa.8.dylib" | grep -q LC_RPATH || install_name_tool -add_rpath @loader_path "$OUT/libOSMesa.8.dylib"

# Fail the build rather than ship a macOS binary, one without Zink, or one missing the entry points osm_bridge uses
for lib in "$OUT"/*.dylib; do
    otool -l "$lib" | grep -A4 LC_BUILD_VERSION | grep -q "platform 2" || { echo "$lib is not an iOS binary"; exit 1; }
done
# osm_bridge dlsyms these through libOSMesa's handle, which also searches libglapi
for sym in OSMesaCreateContext OSMesaMakeCurrent OSMesaGetCurrentContext OSMesaDestroyContext OSMesaPixelStore OSMesaGetProcAddress glGetString glFinish; do
    nm -gU "$OUT"/*.dylib | grep -q " _$sym\$" || { echo "libOSMesa does not export $sym"; exit 1; }
done
strings "$OUT/libOSMesa.8.dylib" | grep -q '@loader_path/libMoltenVK.dylib' || { echo "Zink not built in"; exit 1; }
otool -L "$OUT/libOSMesa.8.dylib"
echo "Built $OUT/libOSMesa.8.dylib and libglapi.0.dylib"
