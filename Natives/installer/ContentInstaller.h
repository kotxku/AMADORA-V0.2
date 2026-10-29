#import <Foundation/Foundation.h>

typedef NS_ENUM(NSInteger, PLContentSource) {
    PLContentSourceModrinth,
    PLContentSourceCurseForge
};

typedef NS_ENUM(NSInteger, PLContentType) {
    PLContentTypeMod,
    PLContentTypeModpack,
    PLContentTypeResourcePack,
    PLContentTypeShader
};

// Pandora-style content installs: search Modrinth/CurseForge and put a project, plus its required dependencies,
// straight into an instance. Network calls are blocking; call them off the main thread.
@interface ContentInstaller : NSObject

// The user's own CurseForge API key (console.curseforge.com); CurseForge refuses requests without one
@property(class, nonatomic, copy) NSString *curseForgeAPIKey;

// Instance folder a content type installs into: mods, resourcepacks or shaderpacks (nil for modpacks)
+ (NSString *)folderForType:(PLContentType)type;

// Results are dictionaries with id, slug, title, author, summary, iconUrl, downloads (NSNumber), pageUrl, tags.
// mcVersion/loader narrow the search when given (loader as in PandoraImport: fabric, quilt, forge, neoforge, vanilla).
+ (NSArray<NSDictionary *> *)search:(NSString *)query source:(PLContentSource)source type:(PLContentType)type
    mcVersion:(NSString *)mcVersion loader:(NSString *)loader offset:(NSUInteger)offset error:(NSString **)error;

// Installs the newest compatible file and its required dependencies into gameDir. Returns a report for the user.
+ (NSString *)install:(NSDictionary *)project source:(PLContentSource)source type:(PLContentType)type
    gameDir:(NSString *)gameDir mcVersion:(NSString *)mcVersion loader:(NSString *)loader progress:(void (^)(NSString *status))progress;

// What install recorded, keyed by file name without ".disabled": title, version, iconUrl, source, projectId
+ (NSDictionary<NSString *, NSDictionary *> *)installedContentInGameDir:(NSString *)gameDir;
+ (void)forgetFile:(NSString *)fileName inGameDir:(NSString *)gameDir;

@end
