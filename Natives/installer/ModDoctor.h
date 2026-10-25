#import <Foundation/Foundation.h>

@interface ModDoctor : NSObject

// Finds Fabric/Quilt mod dependencies that no installed mod provides and downloads them from Modrinth into mods/.
// Blocking; call off the main thread. Returns a human-readable report.
+ (NSString *)fixMissingModsInGameDir:(NSString *)gameDir minecraftVersion:(NSString *)mcVersion loader:(NSString *)loader;

// Switches off mods whose natives have no iOS build (renamed to .jar.disabled) and finds the highest Java major the
// remaining mods declare (Fabric/Quilt depends.java, Forge mods.toml), jar-in-jar included. Returns a warning naming
// the mods it switched off, or nil.
+ (NSString *)prepareModsIn:(NSString *)modsDir requiredJava:(int *)requiredJava requiredBy:(NSString **)requiredBy;

// Java selection for a profile: the requirement is the highest of Minecraft's version JSON (javaVersion.majorVersion,
// following inheritsFrom; json may be nil to read it from versions/) and every mod's "java" dependency; the runtime is
// the lowest installed Java that meets it, never an older one. Logs [JavaSelect] and runs prepareModsIn (*warning).
// Returns the selected Java major, or 0 when nothing installed is new enough (*required says what's needed). Blocking.
+ (int)selectJavaForProfile:(NSDictionary *)profile versionJSON:(NSDictionary *)json required:(int *)required javaHome:(NSString **)javaHome warning:(NSString **)warning;

// Blocking download to an exact path, verified against sha1 when given. Returns YES on success.
+ (BOOL)download:(NSString *)url toFile:(NSString *)path sha1:(NSString *)sha1;

// The jar's fabric.mod.json, or nil.
+ (NSDictionary *)modInfoForJar:(NSString *)jarPath;

// Lenient JSON parse (allows comments and trailing commas on iOS 15+); nil on failure.
+ (id)parseJSON:(NSData *)data;

// Blocking GET; returns nil on failure.
+ (NSData *)fetch:(NSString *)url;

// Blocking download into dir, named by the server's filename. Returns the saved file name or nil.
+ (NSString *)download:(NSString *)url toDirectory:(NSString *)dir;

@end
