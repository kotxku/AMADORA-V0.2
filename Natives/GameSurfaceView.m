#import "GameSurfaceView.h"
#import "LauncherPreferences.h"
#import "PLProfiles.h"
#import "utils.h"

@interface GameSurfaceView()
@end

@implementation GameSurfaceView

- (id)initWithFrame:(CGRect)frame {
    self = [super initWithFrame:frame];
    self.layer.drawsAsynchronously = YES;
    self.layer.opaque = YES;

    return self;
}

+ (Class)layerClass {
    // RENDERER is resolved (auto included) just before the game view is created
    NSString *renderer = getenv("RENDERER") ? @(getenv("RENDERER")) : [PLProfiles resolveKeyForCurrentProfile:@"renderer"];
    if ([renderer hasPrefix:@"libOSMesa"]) {
        return CALayer.class;
    } else {
        return CAMetalLayer.class;
    }
}

@end
