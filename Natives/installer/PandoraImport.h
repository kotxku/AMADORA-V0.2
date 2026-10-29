#import <Foundation/Foundation.h>

@class UZKArchive, UZKFileInfo;

@interface PandoraImport : NSObject

// Imports a Pandora export, CurseForge zip, plain .minecraft zip or .forge archive as a new profile.
// Blocking; call off the main thread. Returns a human-readable report.
+ (NSString *)importArchiveAtPath:(NSString *)path progress:(void (^)(NSString *status))progress;

// Reads loader and Minecraft version from a profile's lastVersionId.
+ (NSDictionary<NSString *, NSString *> *)loaderInfoForVersionId:(NSString *)versionId;

// Streams one archive entry to dest (a directory entry creates the directory). The caller checks the entry's path.
+ (BOOL)extract:(UZKFileInfo *)info from:(UZKArchive *)archive to:(NSString *)dest error:(NSError **)error;

@end
