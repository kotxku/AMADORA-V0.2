#import <Foundation/Foundation.h>

@interface PandoraImport : NSObject

// Imports a Pandora export, CurseForge zip, plain .minecraft zip or .forge archive as a new profile.
// Blocking; call off the main thread. Returns a human-readable report.
+ (NSString *)importArchiveAtPath:(NSString *)path;

// Reads loader and Minecraft version from a profile's lastVersionId.
+ (NSDictionary<NSString *, NSString *> *)loaderInfoForVersionId:(NSString *)versionId;

@end
