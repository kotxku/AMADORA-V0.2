#import <Security/Security.h>
#import "installer/ModDoctor.h"
#import "installer/PandoraImport.h"
#import "installer/RocketCraftInstaller.h"
#import "UnzipKit.h"

static NSString *const kRelease = @"https://api.github.com/repos/kotxku/Rocket-Craft/releases/tags/v1.0.0";
static NSString *const kAsset = @"Rocket.Craft.zip";
static NSString *const kKitRoot = @"Rocket Craft/";
static NSString *const kKeychainService = @"AMADORA.rocketcraft";

static id typed(id obj, Class cls) {
    return [obj isKindOfClass:cls] ? obj : nil;
}

// Streams the asset to disk with progress. GitHub answers with a redirect to signed storage, which rejects
// requests that still carry the token, so the redirect drops Authorization.
@interface RocketCraftDownload : NSObject <NSURLSessionDownloadDelegate>
@property(nonatomic, copy) NSString *dest;
@property(nonatomic, copy) void (^progress)(NSString *status);
@property(nonatomic) NSInteger status;
@property(nonatomic) BOOL saved;
@property(nonatomic) dispatch_semaphore_t done;
@end

@implementation RocketCraftDownload

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task willPerformHTTPRedirection:(NSHTTPURLResponse *)response
        newRequest:(NSURLRequest *)request completionHandler:(void (^)(NSURLRequest *))completionHandler {
    NSMutableURLRequest *redirect = request.mutableCopy;
    [redirect setValue:nil forHTTPHeaderField:@"Authorization"];
    completionHandler(redirect);
}

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didWriteData:(int64_t)bytesWritten
        totalBytesWritten:(int64_t)written totalBytesExpectedToWrite:(int64_t)total {
    if (total > 0 && (written - bytesWritten) * 100 / total != written * 100 / total) {
        self.progress([NSString stringWithFormat:@"Downloading Rocket Craft: %lld of %lld MB", written >> 20, total >> 20]);
    }
}

- (void)URLSession:(NSURLSession *)session downloadTask:(NSURLSessionDownloadTask *)downloadTask didFinishDownloadingToURL:(NSURL *)location {
    self.status = [(NSHTTPURLResponse *)downloadTask.response statusCode];
    if (self.status != 200) return;
    [NSFileManager.defaultManager removeItemAtPath:self.dest error:nil];
    self.saved = [NSFileManager.defaultManager moveItemAtPath:location.path toPath:self.dest error:nil];
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    if (error) NSLog(@"[RocketCraft] Download failed: %@", error.localizedDescription);
    dispatch_semaphore_signal(self.done);
}

@end

@implementation RocketCraftInstaller

#pragma mark Token

+ (NSMutableDictionary *)keychainQuery {
    return @{(id)kSecClass: (id)kSecClassGenericPassword, (id)kSecAttrService: kKeychainService, (id)kSecAttrAccount: @"github"}.mutableCopy;
}

+ (NSString *)gitHubToken {
    NSMutableDictionary *query = self.keychainQuery;
    query[(id)kSecReturnData] = @YES;
    query[(id)kSecMatchLimit] = (id)kSecMatchLimitOne;
    CFTypeRef data = NULL;
    if (SecItemCopyMatching((__bridge CFDictionaryRef)query, &data) != errSecSuccess) return nil;
    return [[NSString alloc] initWithData:(__bridge_transfer NSData *)data encoding:NSUTF8StringEncoding];
}

+ (void)setGitHubToken:(NSString *)token {
    NSMutableDictionary *query = self.keychainQuery;
    SecItemDelete((__bridge CFDictionaryRef)query);
    token = [token stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (!token.length) return;
    query[(id)kSecAttrAccessible] = (id)kSecAttrAccessibleWhenUnlockedThisDeviceOnly;
    query[(id)kSecValueData] = [token dataUsingEncoding:NSUTF8StringEncoding];
    SecItemAdd((__bridge CFDictionaryRef)query, NULL);
}

#pragma mark Download

+ (NSString *)downloadKitTo:(NSString *)dest progress:(void (^)(NSString *))progress {
    NSString *token = self.gitHubToken;
    if (!token) return @"Enter your GitHub token first.";

    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:kRelease]];
    [request setValue:[@"Bearer " stringByAppendingString:token] forHTTPHeaderField:@"Authorization"];
    [request setValue:@"application/vnd.github+json" forHTTPHeaderField:@"Accept"];
    __block NSData *body;
    __block NSInteger status = 0;
    dispatch_semaphore_t fetched = dispatch_semaphore_create(0);
    [[NSURLSession.sharedSession dataTaskWithRequest:request completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        body = data;
        status = [(NSHTTPURLResponse *)response statusCode];
        dispatch_semaphore_signal(fetched);
    }] resume];
    dispatch_semaphore_wait(fetched, DISPATCH_TIME_FOREVER);
    NSLog(@"[RocketCraft] Release lookup: HTTP %ld", (long)status);
    if (status == 401) return @"GitHub didn't accept the token. Check it and enter it again.";
    if (status == 404) return @"GitHub can't find the Rocket Craft release with this token. It needs read access to kotxku/Rocket-Craft (Contents: Read-only).";
    if (status != 200) return [NSString stringWithFormat:@"Couldn't reach GitHub (HTTP %ld). Check your internet connection.", (long)status];

    NSString *assetURL;
    for (NSDictionary *asset in typed(typed([ModDoctor parseJSON:body], NSDictionary.class)[@"assets"], NSArray.class)) {
        if ([typed(asset, NSDictionary.class)[@"name"] isEqual:kAsset]) assetURL = asset[@"url"];
    }
    if (!assetURL) return [NSString stringWithFormat:@"The release has no %@.", kAsset];

    request.URL = [NSURL URLWithString:assetURL];
    [request setValue:@"application/octet-stream" forHTTPHeaderField:@"Accept"];
    RocketCraftDownload *download = [RocketCraftDownload new];
    download.dest = dest;
    download.progress = progress;
    download.done = dispatch_semaphore_create(0);
    NSURLSession *session = [NSURLSession sessionWithConfiguration:NSURLSessionConfiguration.defaultSessionConfiguration delegate:download delegateQueue:nil];
    [[session downloadTaskWithRequest:request] resume];
    dispatch_semaphore_wait(download.done, DISPATCH_TIME_FOREVER);
    [session finishTasksAndInvalidate];
    NSLog(@"[RocketCraft] Kit download: HTTP %ld, saved %d", (long)download.status, download.saved);
    return download.saved ? nil : [NSString stringWithFormat:@"The download stopped (HTTP %ld). Check your connection and free space (it needs about 4 GB), then try again.", (long)download.status];
}

#pragma mark Install

+ (NSString *)installIntoGameDir:(NSString *)gameDir progress:(void (^)(NSString *))progress {
    NSFileManager *fm = NSFileManager.defaultManager;
    NSString *zipPath = [NSTemporaryDirectory() stringByAppendingPathComponent:kAsset];
    NSString *error = [self downloadKitTo:zipPath progress:progress];
    if (error) return error;
    NSString *report = [self installKit:zipPath intoGameDir:gameDir progress:progress];
    [fm removeItemAtPath:zipPath error:nil];
    return report;
}

// Same rules as the kit's install.ps1
+ (NSString *)installKit:(NSString *)zipPath intoGameDir:(NSString *)gameDir progress:(void (^)(NSString *))progress {
    NSFileManager *fm = NSFileManager.defaultManager;
    NSError *error;
    UZKArchive *archive = [[UZKArchive alloc] initWithPath:zipPath error:&error];
    NSArray<UZKFileInfo *> *entries = [archive listFileInfo:&error];
    if (!entries) return [NSString stringWithFormat:@"The downloaded kit can't be opened: %@", error.localizedDescription];

    NSString *modsDir = [gameDir stringByAppendingPathComponent:@"mods"];
    NSString *packsDir = [gameDir stringByAppendingPathComponent:@"resourcepacks"];
    for (NSString *dir in @[modsDir, packsDir, [gameDir stringByAppendingPathComponent:@"config"]]) {
        [fm createDirectoryAtPath:dir withIntermediateDirectories:YES attributes:nil error:nil];
    }
    NSArray<NSString *> *packs = @[@"RocketCraft RL Assets", @"RocketCraft RL Pack", @"RocketCraft Music Pack"];
    for (NSString *pack in packs) {
        [fm removeItemAtPath:[packsDir stringByAppendingPathComponent:pack] error:nil];
    }

    // Fabric refuses two copies of one mod, and packs like Fabulously Optimized already ship Fabric API and YACL,
    // so a library is copied only when the instance lacks it. Rocket Craft itself always replaces the old jar.
    NSMutableDictionary<NSString *, NSString *> *installed = [NSMutableDictionary new];
    for (NSString *file in [fm contentsOfDirectoryAtPath:modsDir error:nil]) {
        if (![file hasSuffix:@".jar"]) continue;
        NSString *modId = typed([ModDoctor modInfoForJar:[modsDir stringByAppendingPathComponent:file]][@"id"], NSString.class);
        if (modId) installed[modId] = file;
    }

    NSMutableArray<NSString *> *notes = [NSMutableArray new];
    NSUInteger done = 0, assets = 0;
    for (UZKFileInfo *info in entries) {
        NSString *path = info.filename;
        if (![path hasPrefix:kKitRoot] || [[path componentsSeparatedByString:@"/"] containsObject:@".."]) continue;
        NSString *relative = [path substringFromIndex:kKitRoot.length];
        if (progress && ++done % 100 == 0) {
            progress([NSString stringWithFormat:@"Installing Rocket Craft: %lu of %lu files", (unsigned long)done, (unsigned long)entries.count]);
        }

        if ([relative hasPrefix:@"resourcepacks/"] && relative.pathComponents.count > 2) {
            if (![PandoraImport extract:info from:archive to:[gameDir stringByAppendingPathComponent:relative] error:&error]) {
                return [NSString stringWithFormat:@"Couldn't unpack %@: %@", relative, error.localizedDescription];
            }
            if (!info.isDirectory) assets++;
        } else if ([relative isEqualToString:@"config/rocketcraft_anthem.txt"]) {
            NSString *dest = [gameDir stringByAppendingPathComponent:relative];
            if (![fm fileExistsAtPath:dest]) [PandoraImport extract:info from:archive to:dest error:nil];
        } else if ([relative hasPrefix:@"mods/"] && [relative hasSuffix:@".jar"] && relative.pathComponents.count == 2) {
            NSString *name = relative.lastPathComponent;
            NSString *temp = [NSTemporaryDirectory() stringByAppendingPathComponent:name];
            if (![PandoraImport extract:info from:archive to:temp error:&error]) {
                return [NSString stringWithFormat:@"Couldn't unpack %@: %@", name, error.localizedDescription];
            }
            NSString *modId = typed([ModDoctor modInfoForJar:temp][@"id"], NSString.class);
            NSString *have = modId ? installed[modId] : nil;
            if (have && ![modId isEqualToString:@"rocketcraft"]) {
                [notes addObject:[NSString stringWithFormat:@"Kept %@ (instance already has it)", have]];
                [fm removeItemAtPath:temp error:nil];
                continue;
            }
            if (have) [fm removeItemAtPath:[modsDir stringByAppendingPathComponent:have] error:nil];
            NSString *dest = [modsDir stringByAppendingPathComponent:name];
            [fm removeItemAtPath:dest error:nil];
            [fm moveItemAtPath:temp toPath:dest error:nil];
            [notes addObject:[@"Added " stringByAppendingString:name]];
        }
    }
    NSLog(@"[RocketCraft] Installed into %@: %lu resource pack files; %@", gameDir, (unsigned long)assets, [notes componentsJoinedByString:@"; "]);

    [self enablePacks:packs inGameDir:gameDir];
    [notes addObject:[NSString stringWithFormat:@"Added the RL Assets, RL Pack and Music Pack resource packs (%lu files) and turned them on", (unsigned long)assets]];
    return [[notes componentsJoinedByString:@"\n"] stringByAppendingString:@"\n\nStart the instance and use the Rocket Craft menu key shown on screen."];
}

// Adds the packs to options.txt's resourcePacks list in install.ps1's order, Music Pack on top
+ (void)enablePacks:(NSArray<NSString *> *)packs inGameDir:(NSString *)gameDir {
    NSString *optionsPath = [gameDir stringByAppendingPathComponent:@"options.txt"];
    NSMutableArray<NSString *> *wanted = [NSMutableArray new];
    for (NSString *pack in packs) [wanted addObject:[@"file/" stringByAppendingString:pack]];

    NSString *options = [NSString stringWithContentsOfFile:optionsPath encoding:NSUTF8StringEncoding error:nil] ?: @"";
    NSMutableArray<NSString *> *lines = [options componentsSeparatedByString:@"\n"].mutableCopy;
    if ([lines.lastObject isEqualToString:@""]) [lines removeLastObject];
    NSUInteger index = [lines indexOfObjectPassingTest:^BOOL(NSString *line, NSUInteger i, BOOL *stop) {
        return [line hasPrefix:@"resourcePacks:"];
    }];
    NSMutableArray *list = [NSMutableArray arrayWithObject:@"vanilla"];
    if (index != NSNotFound) {
        NSData *json = [[lines[index] substringFromIndex:@"resourcePacks:".length] dataUsingEncoding:NSUTF8StringEncoding];
        list = [typed([ModDoctor parseJSON:json], NSArray.class) mutableCopy] ?: list;
        [list removeObjectsInArray:wanted];
    }
    [list addObjectsFromArray:wanted];
    NSString *line = [@"resourcePacks:" stringByAppendingString:[[NSString alloc] initWithData:[NSJSONSerialization dataWithJSONObject:list
        options:NSJSONWritingWithoutEscapingSlashes error:nil] encoding:NSUTF8StringEncoding]];
    if (index != NSNotFound) lines[index] = line; else [lines addObject:line];
    [[[lines componentsJoinedByString:@"\n"] stringByAppendingString:@"\n"] writeToFile:optionsPath atomically:YES encoding:NSUTF8StringEncoding error:nil];
}

@end
