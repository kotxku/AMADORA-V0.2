#import <UIKit/UIKit.h>

#define TYPE_INSTALLED 0
#define TYPE_RELEASE 1
#define TYPE_SNAPSHOT 2
#define TYPE_OLDBETA 3
#define TYPE_OLDALPHA 4

@interface MinecraftResourceUtils : NSObject

+ (void)processVersion:(NSMutableDictionary *)json inheritsFrom:(NSMutableDictionary *)inheritsFrom;
+ (void)tweakVersionJson:(NSMutableDictionary *)json;
// Names of the non-skipped libraries (and client jar) of a tweaked version JSON that aren't on disk. Logs [LibCheck].
+ (NSArray<NSString *> *)missingLibraries:(NSDictionary *)json;

+ (NSObject *)findVersion:(NSString *)version inList:(NSArray *)list;
+ (NSObject *)findNearestVersion:(NSObject *)version expectedType:(int)type;

@end
