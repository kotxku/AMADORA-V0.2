#import <Foundation/Foundation.h>

// Installs the Rocket Craft kit (mods, the three RL resource packs, config) into an instance, like the kit's
// install.ps1 does for Pandora, downloading it from the public kotxku/Rocket-Craft v1.0.0 release.
@interface RocketCraftInstaller : NSObject

// Blocking; call off the main thread. Returns a human-readable report.
+ (NSString *)installIntoGameDir:(NSString *)gameDir progress:(void (^)(NSString *status))progress;

@end
