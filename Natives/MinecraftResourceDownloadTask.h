#import <UIKit/UIKit.h>

@class ModpackAPI;

@interface MinecraftResourceDownloadTask : NSObject
@property NSProgress *progress, *textProgress;
@property NSMutableArray *fileList, *progressList;
@property NSMutableDictionary* metadata;
@property(nonatomic, copy) void(^handleError)(void);
// Called once on the main thread after every download and the step that queued them have finished
@property(nonatomic, copy) void(^handleSuccess)(void);
@property(nonatomic, copy) NSString *errorString;
// Deleted after a successful install (the downloaded modpack package, kept until then so Retry can reuse it)
@property(nonatomic, copy) NSString *cleanupPath;

- (NSURLSessionDownloadTask *)createDownloadTask:(NSString *)url size:(NSUInteger)size sha:(NSString *)sha altName:(NSString *)altName toPath:(NSString *)path;
- (void)finishDownloadWithErrorString:(NSString *)error;
// The installer has queued its last download; success fires once the downloads still running finish
- (void)finishSubmitting;

- (void)downloadVersion:(NSDictionary *)version;
- (void)downloadModpackFromAPI:(ModpackAPI *)api detail:(NSDictionary *)modDetail atIndex:(NSUInteger)selectedVersion;
- (void)downloadModpackFromPackage:(NSString *)packagePath;

@end
