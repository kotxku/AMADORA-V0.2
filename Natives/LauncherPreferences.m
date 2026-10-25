#import "config.h"
#import "utils.h"
#import "LauncherPreferences.h"
#import "PLPreferences.h"
#import "UIKit+hook.h"
#import <CoreFoundation/CoreFoundation.h>

static PLPreferences* pref;

void loadPreferences(BOOL reset) {
    assert(getenv("AME_HOME"));
    if (reset) {
        [pref reset];
    } else {
        pref = [[PLPreferences alloc] initWithAutomaticMigrator];
    }
}

void toggleIsolatedPref(BOOL forceEnable) {
    if (!pref.instancePath) {
        pref.instancePath = [NSString stringWithFormat:@"%s/launcher_preferences.plist", getenv("GAME_DIR")];
    }
    [pref toggleIsolationForced:forceEnable];
}

id getPrefObject(NSString *key) {
    return [pref getObject:key];
}
BOOL getPrefBool(NSString *key) {
    return [getPrefObject(key) boolValue];
}
float getPrefFloat(NSString *key) {
    return [getPrefObject(key) floatValue];
}
NSInteger getPrefInt(NSString *key) {
    return [getPrefObject(key) intValue];
}

void setPrefObject(NSString *key, id value) {
    [pref setObject:key value:value];
}
void setPrefBool(NSString *key, BOOL value) {
    setPrefObject(key, @(value));
}
void setPrefFloat(NSString *key, float value) {
    setPrefObject(key, @(value));
}
void setPrefInt(NSString *key, NSInteger value) {
    setPrefObject(key, @(value));
}

void resetWarnings() {
    for (int i = 0; i < pref.globalPref[@"warnings"].count; i++) {
        NSString *key = pref.globalPref[@"warnings"].allKeys[i];
        pref.globalPref[@"warnings"][key] = @YES;
    }
}

#pragma mark Safe area

CGRect getSafeArea(CGRect screenBounds) {
    UIEdgeInsets safeArea = UIEdgeInsetsFromString(getPrefObject(@"control.control_safe_area"));
    if (screenBounds.size.width < screenBounds.size.height) {
        safeArea = UIEdgeInsetsMake(safeArea.right, safeArea.top, safeArea.left, safeArea.bottom);
    }
    return UIEdgeInsetsInsetRect(screenBounds, safeArea);
}

void setSafeArea(CGSize screenSize, CGRect frame) {
    UIEdgeInsets safeArea;
    // TODO: make safe area consistent across opposite orientations?
    if (screenSize.width < screenSize.height) {
        safeArea = UIEdgeInsetsMake(
            frame.origin.x,
            screenSize.height - CGRectGetMaxY(frame),
            screenSize.width - CGRectGetMaxX(frame),
            frame.origin.y);
    } else {
        safeArea = UIEdgeInsetsMake(
            frame.origin.y,
            frame.origin.x,
            screenSize.height - CGRectGetMaxY(frame),
            screenSize.width - CGRectGetMaxX(frame));
    }
    setPrefObject(@"control.control_safe_area", NSStringFromUIEdgeInsets(safeArea));
}

UIEdgeInsets getDefaultSafeArea() {
    UIEdgeInsets safeArea = UIApplication.sharedApplication.windows.firstObject.safeAreaInsets;
    CGSize screenSize = UIScreen.mainScreen.bounds.size;
    if (screenSize.width < screenSize.height) {
        safeArea.left = safeArea.top;
        safeArea.right = safeArea.bottom;
    }
    safeArea.top = safeArea.bottom = 0;
    return safeArea;
}

#pragma mark Java runtime

NSString* getSelectedJavaHome(NSString* defaultJRETag, int minVersion) {
    NSDictionary *pref = getPrefObject(@"java.java_homes");
    NSDictionary<NSString *, NSString *> *selected = pref[@"0"];
    NSString *selectedVer = selected[defaultJRETag];
    if (minVersion > selectedVer.intValue) {
        NSArray *sortedVersions = [pref.allKeys valueForKeyPath:@"self.integerValue"];
        sortedVersions = [sortedVersions sortedArrayUsingSelector:@selector(compare:)];
        for (NSNumber *version in sortedVersions) {
            if (version.intValue >= minVersion) {
                selectedVer = version.stringValue;
                break;
            }
        }
        if (!selectedVer) {
            NSLog(@"Error: requested Java >= %d was not installed!", minVersion);
            return nil;
        }
    }

    id selectedDir = pref[selectedVer];
    if ([selectedDir isEqualToString:@"internal"]) {
        selectedDir = [NSString stringWithFormat:@"%@/java_runtimes/java-%@-openjdk", NSBundle.mainBundle.bundlePath, selectedVer];
    } else {
        selectedDir = [NSString stringWithFormat:@"%s/java_runtimes/%@", getenv("AME_HOME"), selectedDir];
    }

    if ([NSFileManager.defaultManager fileExistsAtPath:selectedDir]) {
        return selectedDir;
    } else {
        NSLog(@"Error: selected runtime for %@ does not exist: %@", defaultJRETag, selectedDir);
        return nil;
    }
}

// Same condition JavaLauncher uses to turn on the dyld library validation bypass
BOOL canLoadUnsignedLibraries(void) {
    return !DeviceHasJITFlags(JIT_FLAG_FORCE_MIRRORED | JIT_FLAG_HAS_TXM) || getPrefBool(@"debug.debug_always_attached_jit");
}

// JAVA_VERSION from the runtime's release file: "1.8.0_452" -> 8, "21.0.7" -> 21; 0 if unreadable
static int javaMajorOfRuntime(NSString *path) {
    NSString *release = [NSString stringWithContentsOfFile:[path stringByAppendingPathComponent:@"release"] encoding:NSUTF8StringEncoding error:nil];
    NSArray *parts = [release componentsSeparatedByString:@"JAVA_VERSION=\""];
    if (parts.count < 2) return 0;
    NSArray *numbers = [[parts[1] componentsSeparatedByString:@"\""][0] componentsSeparatedByString:@"."];
    int major = [numbers[0] intValue];
    return major == 1 && numbers.count > 1 ? [numbers[1] intValue] : major;
}

NSDictionary<NSNumber *, NSString *> *installedJavaRuntimes(void) {
    NSMutableDictionary *runtimes = [NSMutableDictionary new];
    NSFileManager *fm = NSFileManager.defaultManager;
    NSString *internal = [NSBundle.mainBundle.bundlePath stringByAppendingPathComponent:@"java_runtimes"];
    for (NSString *dir in [fm contentsOfDirectoryAtPath:internal error:nil]) {
        NSArray *parts = [dir componentsSeparatedByString:@"-"]; // bundled runtimes are java-<major>-openjdk
        if (parts.count == 3 && [parts[1] intValue] > 0) runtimes[@([parts[1] intValue])] = [internal stringByAppendingPathComponent:dir];
    }
    // Runtimes imported into Documents are unsigned, so they can only load while the bypass is active
    if (canLoadUnsignedLibraries()) {
        NSDictionary *pref = getPrefObject(@"java.java_homes");
        NSString *external = [@(getenv("AME_HOME")) stringByAppendingPathComponent:@"java_runtimes"];
        for (NSString *dir in [fm contentsOfDirectoryAtPath:external error:nil]) {
            NSString *path = [external stringByAppendingPathComponent:dir];
            int major = javaMajorOfRuntime(path);
            // The runtime picked in Manage Runtimes wins over the bundled one of the same version
            if (major > 0 && (!runtimes[@(major)] || [pref[@(major).stringValue] isEqualToString:dir])) runtimes[@(major)] = path;
        }
    }
    return runtimes;
}

int selectJavaRuntime(int required, int preferred, NSString **javaHome) {
    NSDictionary<NSNumber *, NSString *> *runtimes = installedJavaRuntimes();
    NSArray<NSNumber *> *versions = [runtimes.allKeys sortedArrayUsingSelector:@selector(compare:)];
    if (preferred >= required && runtimes[@(preferred)]) {
        if (javaHome) *javaHome = runtimes[@(preferred)];
        return preferred;
    }
    for (NSNumber *version in versions) {
        if (version.intValue < required) continue; // never an older Java than the pack needs
        if (javaHome) *javaHome = runtimes[version];
        return version.intValue;
    }
    return 0;
}

#pragma mark Renderer
NSArray* getRendererKeys(BOOL containsDefault) {
    NSMutableArray *array = @[
        @"auto",
        @ RENDERER_NAME_GL4ES,
        @ RENDERER_NAME_MTL_ANGLE,
        @ RENDERER_NAME_MOBILEGLUES,
        @ RENDERER_NAME_VK_ZINK
    ].mutableCopy;

    if (containsDefault) {
        [array insertObject:@"(default)" atIndex:0];
    }
    
    return array;
}

NSArray* getRendererNames(BOOL containsDefault) {
    NSMutableArray *array;

    array = @[
        localize(@"preference.title.renderer.debug.auto", nil),
        localize(@"preference.title.renderer.debug.gl4es", nil),
        localize(@"preference.title.renderer.debug.angle", nil),
        localize(@"preference.title.renderer.debug.mg", nil),
        localize(@"preference.title.renderer.debug.zink", nil)
    ].mutableCopy;

    if (containsDefault) {
        [array insertObject:@"(default)" atIndex:0];
    }

    return array;
}
