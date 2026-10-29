#import <Foundation/Foundation.h>

// Installs the Rocket Craft kit (mods, the three RL resource packs, config) into an instance, like the kit's
// install.ps1 does for Pandora. The kit lives on a private GitHub release, so it is fetched with a token the
// player enters; the token stays in this device's Keychain and is never part of the app.
@interface RocketCraftInstaller : NSObject

@property(class, nonatomic, copy) NSString *gitHubToken;

// Blocking; call off the main thread. Returns a human-readable report.
+ (NSString *)installIntoGameDir:(NSString *)gameDir progress:(void (^)(NSString *status))progress;

@end
