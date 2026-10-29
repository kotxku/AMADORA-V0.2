#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <libgen.h>
#include <spawn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#include "utils.h"

#import "ios_uikit_bridge.h"
#import "JavaLauncher.h"
#import "LauncherPreferences.h"
#import "MinecraftOptionUtils.h"
#import "PLLogOutputView.h"
#import "PLProfiles.h"

#define fm NSFileManager.defaultManager

extern char **environ;

BOOL validateVirtualMemorySpace(size_t size) {
    size <<= 20; // convert to MB
    void *map = mmap(0, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    // check if process successfully maps and unmaps a contiguous range
    if(map == MAP_FAILED || munmap(map, size) != 0)
        return NO;
    return YES;
}

void init_loadDefaultEnv() {
    /* Define default env */

    // Silent Caciocavallo NPE error in locating Android-only lib
    setenv("LD_LIBRARY_PATH", "", 1);

    // Ignore mipmap for performance(?) seems does not affect iOS
    //setenv("LIBGL_MIPMAP", "3", 1);

    // Disable overloaded functions hack for Minecraft 1.17+
    setenv("LIBGL_NOINTOVLHACK", "1", 1);

    // Fix white color on banner and sheep, since GL4ES 1.1.5
    setenv("LIBGL_NORMALIZE", "1", 1);

    // Override OpenGL version to 4.1 for Zink
    setenv("MESA_GL_VERSION_OVERRIDE", "4.1", 1);

    // Runs JVM in a separate thread
    setenv("HACK_IGNORE_START_ON_FIRST_THREAD", "1", 1);
}

void init_loadCustomEnv() {
    NSString *envvars = getPrefObject(@"java.env_variables");
    if (envvars == nil) return;
    NSLog(@"[JavaLauncher] Reading custom environment variables");
    for (NSString *line in [envvars componentsSeparatedByCharactersInSet:NSCharacterSet.whitespaceCharacterSet]) {
        if (![line containsString:@"="]) {
            NSLog(@"[JavaLauncher] Warning: skipped empty value custom env variable: %@", line);
            continue;
        }
        NSRange range = [line rangeOfString:@"="];
        NSString *key = [line substringToIndex:range.location];
        NSString *value = [line substringFromIndex:range.location+range.length];
        setenv(key.UTF8String, value.UTF8String, 1);
        NSLog(@"[JavaLauncher] Added custom env variable: %@", line);
    }
}

void init_loadCustomJvmFlags(int* argc, const char** argv) {
    NSString *jvmargs = [PLProfiles resolveKeyForCurrentProfile:@"javaArgs"];
    if (jvmargs == nil) return;
    // Make the separator happy
    jvmargs = [jvmargs stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet];
    jvmargs = [@" " stringByAppendingString:jvmargs];

    NSLog(@"[JavaLauncher] Reading custom JVM flags");
    NSArray *argsToPurge = @[@"Xms", @"Xmx", @"d32", @"d64"];
    for (NSString *arg in [jvmargs componentsSeparatedByString:@" -"]) {
        NSString *jvmarg = [arg stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet];
        if (jvmarg.length == 0) continue;
        BOOL ignore = NO;
        for (NSString *argToPurge in argsToPurge) {
            if ([jvmarg hasPrefix:argToPurge]) {
                NSLog(@"[JavaLauncher] Ignored JVM flag: -%@", jvmarg);
                ignore = YES;
                break;
            }
        }
        if (ignore) continue;

        ++*argc;
        argv[*argc] = [@"-" stringByAppendingString:jvmarg].UTF8String;

        NSLog(@"[JavaLauncher] Added custom JVM flag: %s", argv[*argc]);
    }
}

// The version of an org.lwjgl artifact the version JSON lists, or nil (LWJGL 2 versions list none)
static NSString *listedLWJGLVersion(id launchTarget, NSString *artifact) {
    if (![launchTarget isKindOfClass:NSDictionary.class]) return nil;
    for (NSDictionary *library in launchTarget[@"libraries"]) {
        NSArray<NSString *> *parts = [library[@"name"] componentsSeparatedByString:@":"];
        if (parts.count >= 3 && [parts[0] isEqualToString:@"org.lwjgl"] && [parts[1] isEqualToString:artifact]) {
            return parts[2];
        }
    }
    return nil;
}

// Minecraft 26.1+ is built against LWJGL 3.4, which isn't compatible with the bundled 3.3.3
static BOOL requiresLWJGL34(id launchTarget) {
    NSString *version = listedLWJGLVersion(launchTarget, @"lwjgl");
    return version && [version compare:@"3.4" options:NSNumericSearch] != NSOrderedAscending;
}

void logLWJGLSelection(NSDictionary *metadata) {
    NSString *listed = listedLWJGLVersion(metadata, @"lwjgl");
    // Minecraft 26.3+ swaps GLFW for SDL3 (lwjgl-sdl). There's no libSDL3 on iOS: lwjgl341's org.lwjgl.sdl classes
    // are a Java bridge onto the same GLFW layer that drives touch, keyboard and the surface
    NSString *sdl = listedLWJGLVersion(metadata, @"lwjgl-sdl");
    NSLog(@"[LWJGLSelect] Minecraft lists LWJGL %@%@; using bundled %@", listed ?: @"2 (none listed)",
        sdl ? [NSString stringWithFormat:@" with SDL3 (lwjgl-sdl %@)", sdl] : @"",
        !requiresLWJGL34(metadata) ? @"3.3.3 (libs)" : sdl ? @"3.4.1 (lwjgl341) with the AMADORA SDL3 bridge" : @"3.4.1 (lwjgl341, GLFW bridge)");
}

NSString *resolveLaunchRenderer(NSDictionary *metadata, NSString **error) {
    NSString *renderer = [PLProfiles resolveKeyForCurrentProfile:@"renderer"] ?: @"auto";
    // Minecraft 26.1+ (the LWJGL 3.4 versions) needs OpenGL 3.3 core with texture buffers. iOS has no OpenGL ES above
    // 3.0 (EAGL and ANGLE's Metal backend both stop there), so MobileGlues can't reach it; Zink on MoltenVK can.
    BOOL needsGL33 = requiresLWJGL34(metadata);
    if ([renderer isEqualToString:@"auto"] && needsGL33) {
        NSString *frameworks = [NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:@"Frameworks"];
        for (NSString *lib in @[@ RENDERER_NAME_VK_ZINK, @"libMoltenVK.dylib", @"libglapi.0.dylib"]) {
            if ([fm fileExistsAtPath:[frameworks stringByAppendingPathComponent:lib]]) continue;
            NSLog(@"[RendererSelect] auto: this version needs OpenGL 3.3 but %@ is missing, so no renderer can run it", lib);
            if (error) *error = [NSString stringWithFormat:@"This Minecraft version needs OpenGL 3.3. On iOS only the Zink (Vulkan) renderer can provide it, and this AMADORA build doesn't include it (%@ is missing). Install a full AMADORA build and try again.", lib];
            return nil;
        }
        renderer = @ RENDERER_NAME_VK_ZINK;
        NSLog(@"[RendererSelect] auto -> %@: this version needs OpenGL 3.3; Zink on MoltenVK provides OpenGL 4.1, MobileGlues only gets OpenGL ES 3.0 on iOS", renderer);
    } else if ([renderer isEqualToString:@"auto"]) {
        NSLog(@"[RendererSelect] auto: chosen from the OpenGL version Minecraft requests (gl4es for 1.x/2.x, MobileGlues otherwise)");
    } else {
        NSLog(@"[RendererSelect] %@ (set in the profile)%@", renderer, needsGL33 && ![renderer isEqualToString:@ RENDERER_NAME_VK_ZINK]
            ? @"; warning: this version needs OpenGL 3.3, which only Zink provides on iOS" : @"");
    }
    setenv("RENDERER", renderer.UTF8String, 1);
    return renderer;
}

int launchJVM(NSString *username, id launchTarget, int width, int height, int minVersion) {
    NSLog(@"[JavaLauncher] Beginning JVM launch");

    init_loadDefaultEnv();
    init_loadCustomEnv();

    DeviceGetJITFlags(YES); // refresh JIT flags right after loading env
    BOOL requiresTXMWorkaround = DeviceHasJITFlags(JIT_FLAG_FORCE_MIRRORED | JIT_FLAG_HAS_TXM);
    BOOL jit26AlwaysAttached = getPrefBool(@"debug.debug_always_attached_jit");
    if (requiresTXMWorkaround) {
        static void *result;
        if(!result) result = JIT26CreateRegionLegacy(getpagesize());
        if ((uint32_t)result != 0x690000E0) {
            munmap(result, getpagesize());
            // we can't continue since legacy script only allows calling breakpoint once
            NSString *inBundleScriptPath = [NSBundle.mainBundle pathForResource:@"UniversalJIT26" ofType:@"js"];
            NSString *lcAppInfoPath = [NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:@"LCAppInfo.plist"];
            NSMutableDictionary *lcAppInfo = [NSMutableDictionary dictionaryWithContentsOfFile:lcAppInfoPath];
            if(lcAppInfo) {
                // if this is inside LiveContainer, we assign script ourselves and prompt user to restart Amethyst
                lcAppInfo[@"jitLaunchScriptJs"] = [[NSData dataWithContentsOfFile:inBundleScriptPath] base64EncodedStringWithOptions:0];
                if([lcAppInfo writeToFile:lcAppInfoPath atomically:YES]) {
                    showDialog(localize(@"Error", nil), @"AMADORA was launched with a legacy script. We have updated the script to Universal, please restart LiveContainer to continue.");
                    [PLLogOutputView handleExitCode:1];
                    return 1;
                }
            }
            [NSFileManager.defaultManager copyItemAtPath:inBundleScriptPath toPath:[NSString stringWithFormat:@"%s/UniversalJIT26.js", getenv("AME_HOME")] error:nil];
            showDialog(localize(@"Error", nil), @"Support for legacy script has been removed. Please switch to Universal JIT script. To import it, long-press on AMADORA when enabling JIT in StikDebug and tap \"Assign Script\", then go to AMADORA's Documents directory and pick it. (on sideloaded StikDebug, the builtin script is named Amethyst-MeloNX.js)");
            [PLLogOutputView handleExitCode:1];
            return 1;
        }
        JIT26SendJITScript([NSString stringWithContentsOfFile:[NSBundle.mainBundle pathForResource:@"UniversalJIT26Extension" ofType:@"js"]]);
        JIT26SetDetachAfterFirstBr(!jit26AlwaysAttached);
        // make sure we don't get stuck in EXC_BAD_ACCESS
        task_set_exception_ports(mach_task_self(), EXC_MASK_BAD_ACCESS, 0, EXCEPTION_DEFAULT, MACHINE_THREAD_STATE);
    }
    if (!requiresTXMWorkaround || jit26AlwaysAttached) {
        if (jit26AlwaysAttached) {
            // Only allow StikDebug to catch our breakpoints to prevent any stutters
            task_set_exception_ports(mach_task_self(), EXC_MASK_ALL & ~EXC_MASK_BREAKPOINT, 0,
                EXCEPTION_DEFAULT, THREAD_STATE_NONE);
        }
        // Activate Library Validation bypass for external runtime and dylibs (JNA, etc)
        init_bypassDyldLibValidation();
    } else {
        NSLog(@"[DyldLVBypass] Hook disabled! Loading unsigned dylib will cause code signature error.");
    }

    BOOL launchJar = NO;
    NSString *gameDir;
    NSString *defaultJRETag;
    NSString *javaHome;
    NSCAssert(launchTarget, @"Unexpected nil launchTarget");
    if ([launchTarget isKindOfClass:NSDictionary.class]) {
        // Before launching, the launcher stores the Java this profile and its mods need in javaVersion
        // (ModDoctor selectJavaForProfile). Treat it as a minimum too, so a missing runtime never means an older Java.
        int preferredJavaVersion = [PLProfiles resolveKeyForCurrentProfile:@"javaVersion"].intValue;
        if (minVersion > 0 || preferredJavaVersion > 0) {
            minVersion = MAX(minVersion, preferredJavaVersion);
            int selected = selectJavaRuntime(minVersion, preferredJavaVersion, &javaHome);
            NSLog(@"[JavaSelect] Launching with %@", selected ? [NSString stringWithFormat:@"Java %d (%@)", selected, javaHome]
                : [NSString stringWithFormat:@"nothing: no installed runtime is Java %d or newer", minVersion]);
        }
        if (minVersion <= 8) {
            defaultJRETag = @"1_16_5_older";
        } else {
            defaultJRETag = @"1_17_newer";
        }

        // Setup RENDERER
        NSString *rendererError;
        NSString *renderer = resolveLaunchRenderer(launchTarget, &rendererError);
        if (!renderer) {
            UIKit_returnToSplitView();
            showDialog(localize(@"Error", nil), rendererError);
            return 1;
        }
        NSLog(@"[JavaLauncher] RENDERER is set to %@\n", renderer);
        // Setup gameDir
        gameDir = [NSString stringWithFormat:@"%s/instances/%@/%@",
            getenv("AME_HOME"), getPrefObject(@"general.game_directory"),
            [PLProfiles resolveKeyForCurrentProfile:@"gameDir"]]
            .stringByStandardizingPath;
    } else {
        defaultJRETag = @"execute_jar";
        gameDir = @(getenv("GAME_DIR"));
        launchJar = YES;
    }
    if (!javaHome && (launchJar || minVersion == 0)) {
        // A jar, or a version JSON that doesn't say which Java it needs: use the Manage Runtimes defaults
        NSLog(@"[JavaLauncher] Looking for Java %d or later", minVersion);
        javaHome = getSelectedJavaHome(defaultJRETag, minVersion);
    }

    if (javaHome == nil) {
        UIKit_returnToSplitView();
        BOOL isExecuteJar = [defaultJRETag isEqualToString:@"execute_jar"];
        showDialog(localize(@"Error", nil), [NSString stringWithFormat:localize(@"java.error.missing_runtime", nil),
            isExecuteJar ? [launchTarget lastPathComponent] : PLProfiles.current.selectedProfile[@"lastVersionId"], minVersion]);
        return 1;
    } else if ([javaHome hasPrefix:@(getenv("AME_HOME"))]) {
        // Copy libawt_xawt.dylib
        NSString *dest = [NSString stringWithFormat:@"%@/lib/libawt_xawt.dylib", javaHome];
        NSString *source = [NSString stringWithFormat:@"%@/Frameworks/libawt_xawt.dylib", NSBundle.mainBundle.bundlePath];
        NSError *error;
        [fm removeItemAtPath:dest error:nil];
        [fm copyItemAtPath:source toPath:dest error:&error];
        if (error) {
            NSLog(@"[JavaLauncher] Copy libawt_xawt.dylib failed: %@", error.localizedDescription);
        }
    }

    setenv("JAVA_HOME", javaHome.UTF8String, 1);
    NSLog(@"[JavaLauncher] JAVA_HOME has been set to %@", javaHome);

    int allocmem;
    if (getPrefBool(@"java.auto_ram")) {
        CGFloat autoRatio = getEntitlementValue(@"com.apple.private.memorystatus") ? 0.4 : 0.25;
        allocmem = roundf((NSProcessInfo.processInfo.physicalMemory >> 20) * autoRatio);
    } else {
        allocmem = getPrefInt(@"java.allocated_memory");
    }
    NSLog(@"[JavaLauncher] Max RAM allocation is set to %d MB", allocmem);
    if (!validateVirtualMemorySpace(allocmem)) {
        UIKit_returnToSplitView();
        if (getEntitlementValue(@"com.apple.developer.kernel.increased-memory-limit")) {
            showDialog(localize(@"Error", nil), @"Insufficient contiguous virtual memory space. Lower memory allocation and try again.");
        } else {
            showDialog(localize(@"Error", nil), @"Insufficient contiguous virtual memory space. Increased Memory Limit entitlement is missing, please add it via GetMoreRam app.");
        }
        return 1;
    }

    // Setup options.txt
    [MinecraftOptionUtils setupOptionsAtGameDir:gameDir];
    
    int margc = -1;
    const char *margv[1000];

    margv[++margc] = [NSString stringWithFormat:@"%@/bin/java", javaHome].UTF8String;
    margv[++margc] = "-XstartOnFirstThread";
    if (!launchJar) {
        margv[++margc] = "-Djava.system.class.loader=net.kdt.pojavlaunch.PojavClassLoader";
    }
    margv[++margc] = "-Xms128M";
    margv[++margc] = [NSString stringWithFormat:@"-Xmx%dM", allocmem].UTF8String;
    BOOL useLWJGL34 = requiresLWJGL34(launchTarget);
    NSString *lwjgl34Path = [NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:@"lwjgl341"];
    NSLog(@"[JavaLauncher] Using LWJGL %s (version lists %@)", useLWJGL34 ? "3.4.1" : "3.3.3", listedLWJGLVersion(launchTarget, @"lwjgl") ?: @"none");
    if (useLWJGL34) {
        // LWJGL 3.4.1 natives first, everything else (OpenAL, JNA, renderers) still comes from Frameworks
        margv[++margc] = [NSString stringWithFormat:@"-Djava.library.path=%@:%@/Frameworks", lwjgl34Path, NSBundle.mainBundle.bundlePath].UTF8String;
        // 26.x would find MoltenVK and try its Vulkan backend; keep it on Amethyst's OpenGL renderers
        margv[++margc] = "-Dorg.lwjgl.vulkan.libname=amadora-vulkan-disabled";
        // 26.x bootstraps LWJGL's spvc binding, which looks for libspirv-cross.dylib; MobileGlues already ships the same C API
        margv[++margc] = [NSString stringWithFormat:@"-Dorg.lwjgl.spvc.libname=%@/Frameworks/libspirv-cross-c-shared.0.dylib", NSBundle.mainBundle.bundlePath].UTF8String;
        // Sodium quits unless LWJGL is exactly the version 26.x lists (3.4.3); the bundled iOS build is 3.4.1
        margv[++margc] = "-Dsodium.checks.issue2561=false";
    } else {
        margv[++margc] = [NSString stringWithFormat:@"-Djava.library.path=%@/Frameworks", NSBundle.mainBundle.bundlePath].UTF8String;
    }
    margv[++margc] = [NSString stringWithFormat:@"-Duser.dir=%@", gameDir].UTF8String;
    margv[++margc] = [NSString stringWithFormat:@"-Duser.home=%s", getenv("AME_HOME")].UTF8String;
    margv[++margc] = [NSString stringWithFormat:@"-Duser.timezone=%@", NSTimeZone.localTimeZone.name].UTF8String;
    margv[++margc] = [NSString stringWithFormat:@"-DUIScreen.maximumFramesPerSecond=%d", (int)UIScreen.mainScreen.maximumFramesPerSecond].UTF8String;
    margv[++margc] = "-Dorg.lwjgl.glfw.checkThread0=false";
    margv[++margc] = "-Dorg.lwjgl.system.allocator=system";
    //margv[++margc] = "-Dorg.lwjgl.util.NoChecks=true";
    margv[++margc] = "-Dlog4j2.formatMsgNoLookups=true";
    // JNA must load the signed libjnidispatch from Frameworks; an extracted copy in Caches fails code signature checks
    margv[++margc] = [NSString stringWithFormat:@"-Djna.boot.library.path=%@/Frameworks", NSBundle.mainBundle.bundlePath].UTF8String;
    margv[++margc] = "-Djna.nounpack=true";
    // Controlify loads SDL3 through FFM from this path instead of its desktop builds, which iOS can't load
    NSString *sdl3 = [NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:@"Frameworks/libSDL3.dylib"];
    if ([NSFileManager.defaultManager fileExistsAtPath:sdl3]) {
        margv[++margc] = [NSString stringWithFormat:@"-Ddev.isxander.sdl.library=%@", sdl3].UTF8String;
    }
    margv[++margc] = "-Djna.noclasspath=true";
    margv[++margc] = "-Djna.nosys=true";

    // Preset OpenGL libname
    const char *glLibName = getenv("RENDERER");
    if (glLibName) {
        if (!strcmp(glLibName, "auto")) {
            // workaround only applies to 1.20.2+
            glLibName = RENDERER_NAME_MTL_ANGLE;
        }
        margv[++margc] = [NSString stringWithFormat:@"-Dorg.lwjgl.opengl.libname=%s", glLibName].UTF8String;
    }

    NSString *librariesPath = [NSString stringWithFormat:@"%@/libs", NSBundle.mainBundle.bundlePath];
    margv[++margc] = [NSString stringWithFormat:@"-javaagent:%@/patchjna_agent.jar=", librariesPath].UTF8String;
    if(getPrefBool(@"general.cosmetica")) {
        margv[++margc] = [NSString stringWithFormat:@"-javaagent:%@/arc_dns_injector.jar=23.95.137.176", librariesPath].UTF8String;
    }

    // Workaround random stack guard allocation crashes
    margv[++margc] = "-XX:+UnlockExperimentalVMOptions";
    margv[++margc] = "-XX:+DisablePrimordialThreadGuardPages";

    // On iOS 26, use mirror mapped JIT by default
    if (@available(iOS 26.0, *)) {
        margv[++margc] = "-XX:+MirrorMappedCodeCache";
    }

    // Disable Forge 1.16.x early progress window
    margv[++margc] = "-Dfml.earlyprogresswindow=false";

    // Load java
    NSString *libjlipath8 = [NSString stringWithFormat:@"%@/lib/jli/libjli.dylib", javaHome]; // java 8
    NSString *libjlipath11 = [NSString stringWithFormat:@"%@/lib/libjli.dylib", javaHome]; // java 11+
    BOOL isJava8 = [fm fileExistsAtPath:libjlipath8];
    setenv("INTERNAL_JLI_PATH", (isJava8 ? libjlipath8 : libjlipath11).UTF8String, 1);
    void* libjli = dlopen(getenv("INTERNAL_JLI_PATH"), RTLD_GLOBAL);

    if (!libjli) {
        const char *error = dlerror();
        NSLog(@"[Init] JLI lib = NULL: %s", error);
        UIKit_returnToSplitView();
        showDialog(localize(@"Error", nil), @(error));
        return 1;
    }

    // Setup Caciocavallo
    margv[++margc] = "-Djava.awt.headless=false";
    margv[++margc] = "-Dcacio.font.fontmanager=sun.awt.X11FontManager";
    margv[++margc] = "-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler";
    margv[++margc] = [NSString stringWithFormat:@"-Dcacio.managed.screensize=%dx%d", width, height].UTF8String;
    margv[++margc] = "-Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel";
    if (isJava8) {
        // Setup Caciocavallo
        margv[++margc] = "-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit";
        margv[++margc] = "-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment";
    } else {
        // Required by Cosmetica to inject DNS
        margv[++margc] = "--add-opens=java.base/java.net=ALL-UNNAMED";

        // Setup Caciocavallo
        margv[++margc] = "-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit";
        margv[++margc] = "-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment";

        // Required by Caciocavallo17 to access internal API
        margv[++margc] = "--add-exports=java.desktop/java.awt=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/sun.java2d=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/sun.awt=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.desktop/sun.font=ALL-UNNAMED";
        margv[++margc] = "--add-exports=java.base/sun.security.action=ALL-UNNAMED";
        margv[++margc] = "--add-opens=java.base/java.util=ALL-UNNAMED";
        margv[++margc] = "--add-opens=java.desktop/java.awt=ALL-UNNAMED";
        margv[++margc] = "--add-opens=java.desktop/sun.font=ALL-UNNAMED";
        margv[++margc] = "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED";
        margv[++margc] = "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED";

        // TODO: workaround, will be removed once the startup part works without PLaunchApp
        margv[++margc] = "--add-exports=cpw.mods.bootstraplauncher/cpw.mods.bootstraplauncher=ALL-UNNAMED";
    }

    // Add Caciocavallo bootclasspath
    NSString *cacio_classpath = [NSString stringWithFormat:@"-Xbootclasspath/%s", isJava8 ? "p" : "a"];
    NSString *cacio_libs_path = [NSString stringWithFormat:@"%@/libs_caciocavallo%s", NSBundle.mainBundle.bundlePath, isJava8 ? "" : "17"];
    NSArray *files = [fm contentsOfDirectoryAtPath:cacio_libs_path error:nil];
    for(NSString *file in files) {
        if ([file hasSuffix:@".jar"]) {
            cacio_classpath = [NSString stringWithFormat:@"%@:%@/%@", cacio_classpath, cacio_libs_path, file];
        }
    }
    margv[++margc] = cacio_classpath.UTF8String;

    // FIXME: the JVM arg is deprecated; and it is currently broken for Java 25
    if (!getenv("JVM_KEEP_UseCompressedClassPointers") || !getEntitlementValue(@"com.apple.developer.kernel.extended-virtual-addressing")) {
        // In jailed environment, where extended virtual addressing entitlement isn't
        // present (for free dev account), allocating compressed space fails.
        // FIXME: does extended VA allow allocating compressed class space?
        margv[++margc] = "-XX:-UseCompressedClassPointers";
    }

    if ([launchTarget isKindOfClass:NSDictionary.class]) {
        for (NSString *arg in launchTarget[@"arguments"][@"jvm_processed"]) {
            margv[++margc] = arg.UTF8String;
        }
    }

    init_loadCustomJvmFlags(&margc, (const char **)margv);
    NSLog(@"[Init] Found JLI lib");

    NSString *classpath = [NSString stringWithFormat:@"%@/*", librariesPath];
    if (useLWJGL34) {
        // Same jars as libs/* except the LWJGL 3.3.3 one
        NSMutableArray *jars = [NSMutableArray arrayWithObject:[lwjgl34Path stringByAppendingPathComponent:@"lwjgl.jar"]];
        for (NSString *file in [fm contentsOfDirectoryAtPath:librariesPath error:nil]) {
            if ([file hasSuffix:@".jar"] && ![file isEqualToString:@"lwjgl.jar"]) {
                [jars addObject:[librariesPath stringByAppendingPathComponent:file]];
            }
        }
        classpath = [jars componentsJoinedByString:@":"];
    }
    if (launchJar) {
        classpath = [classpath stringByAppendingFormat:@":%@", launchTarget];
    }
    margv[++margc] = "-cp";
    margv[++margc] = classpath.UTF8String;
    margv[++margc] = "net.kdt.pojavlaunch.PojavLauncher";

    if (launchJar) {
        margv[++margc] = "-jar";
    } else {
        margv[++margc] = username.UTF8String;
    }

    if ([launchTarget isKindOfClass:NSDictionary.class]) {
        margv[++margc] = [launchTarget[@"id"] UTF8String];
    } else {
        margv[++margc] = [launchTarget UTF8String];
    }
    //margv[++margc] = "ghidra.GhidraRun";

    pJLI_Launch = (JLI_Launch_func *)dlsym(libjli, "JLI_Launch");

    if (NULL == pJLI_Launch) {
        NSLog(@"[Init] JLI_Launch = NULL");
        return -2;
    }

    NSLog(@"[Init] Calling JLI_Launch");

    // Cr4shed known issue: exit after crash dump,
    // reset signal handler so that JVM can catch them
    signal(SIGSEGV, SIG_DFL);
    signal(SIGPIPE, SIG_DFL);
    signal(SIGBUS, SIG_DFL);
    signal(SIGILL, SIG_DFL);
    signal(SIGFPE, SIG_DFL);

    // Free split VC
    tmpRootVC = nil;

    return pJLI_Launch(++margc, margv,
                   0, NULL, // sizeof(const_jargs) / sizeof(char *), const_jargs,
                   0, NULL, // sizeof(const_appclasspath) / sizeof(char *), const_appclasspath,
                   // These values are ignored in Java 17, so keep it anyways
                   "1.8.0-internal",
                   "1.8",

                   "java", "openjdk",
                   /* (const_jargs != NULL) ? JNI_TRUE : */ JNI_FALSE,
                   JNI_TRUE, JNI_FALSE, JNI_TRUE);
}
