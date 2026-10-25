#import <Foundation/Foundation.h>

@interface ModDoctor : NSObject

// Finds Fabric/Quilt mod dependencies that no installed mod provides and downloads them from Modrinth into mods/.
// Blocking; call off the main thread. Returns a human-readable report.
+ (NSString *)fixMissingModsInGameDir:(NSString *)gameDir minecraftVersion:(NSString *)mcVersion loader:(NSString *)loader;

// Lenient JSON parse (allows comments and trailing commas on iOS 15+); nil on failure.
+ (id)parseJSON:(NSData *)data;

// Blocking GET; returns nil on failure.
+ (NSData *)fetch:(NSString *)url;

// Blocking download into dir, named by the server's filename. Returns the saved file name or nil.
+ (NSString *)download:(NSString *)url toDirectory:(NSString *)dir;

@end
