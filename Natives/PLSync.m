#import "PLProfiles.h"
#import "PLSync.h"
#import "utils.h"

static NSString *const kConfigName = @".amadora_sync.json";
static NSString *const kFallbackOptions = @"fallback_options.txt";
static NSString *const kRemoteBookmark = @"AMADORA.sync_remote_bookmark";

// Per-device settings: a computer's render distance or GUI scale would make the phone slow or unreadable
static NSSet<NSString *> *deviceOptions(void) {
    return [NSSet setWithArray:@[@"renderDistance", @"simulationDistance", @"guiScale", @"fullscreen", @"fullscreenResolution",
        @"maxFps", @"graphicsMode", @"enableVsync", @"overrideWidth", @"overrideHeight", @"prioritizeChunkUpdates",
        @"biomeBlendRadius", @"mipmapLevels", @"entityDistanceScaling", @"cloudRange", @"renderClouds"]];
}

@implementation PLSync

+ (NSString *)syncedDir {
    return [@(getenv("GAME_DIR")) stringByAppendingPathComponent:@"synced"];
}

+ (NSArray<NSString *> *)defaultFiles {
    return @[@"options.txt", @"servers.dat", @"command_history.txt"];
}

+ (NSArray<NSString *> *)defaultFolders {
    return @[@"saves", @"config", @"screenshots", @"resourcepacks", @"downloads", @"shaderpacks", @"flashback",
        @"Distant_Horizons_server_data", @".voxy", @"xaero", @"journeymap", @".bobby", @"schematics"];
}

#pragma mark Config

+ (NSMutableDictionary *)config {
    NSData *data = [NSData dataWithContentsOfFile:[self.syncedDir stringByAppendingPathComponent:kConfigName]];
    NSDictionary *config = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
    NSMutableDictionary *result = [NSMutableDictionary new];
    result[@"files"] = [NSMutableArray arrayWithArray:[config[@"files"] isKindOfClass:NSArray.class] ? config[@"files"] : @[]];
    result[@"folders"] = [NSMutableArray arrayWithArray:[config[@"folders"] isKindOfClass:NSArray.class] ? config[@"folders"] : @[]];
    return result;
}

+ (void)saveConfig:(NSDictionary *)config {
    [NSFileManager.defaultManager createDirectoryAtPath:self.syncedDir withIntermediateDirectories:YES attributes:nil error:nil];
    NSData *data = [NSJSONSerialization dataWithJSONObject:config options:NSJSONWritingPrettyPrinted error:nil];
    [data writeToFile:[self.syncedDir stringByAppendingPathComponent:kConfigName] atomically:YES];
}

+ (BOOL)isFileEnabled:(NSString *)name {
    return [self.config[@"files"] containsObject:name];
}

+ (void)setFile:(NSString *)name enabled:(BOOL)enabled {
    NSMutableDictionary *config = self.config;
    [config[@"files"] removeObject:name];
    if (enabled) [config[@"files"] addObject:name];
    [self saveConfig:config];
}

+ (BOOL)isFolderEnabled:(NSString *)name {
    return [self.config[@"folders"] containsObject:name];
}

// Names come from our own lists, but keep anything that could leave the instance out
static BOOL isSafeName(NSString *name) {
    return name.length && ![name containsString:@".."] && ![name hasPrefix:@"/"];
}

#pragma mark Instances and links

// Profile name -> instance folder; profiles sharing a folder count once (Pandora has one folder per instance)
+ (NSDictionary<NSString *, NSString *> *)instances {
    NSMutableDictionary *result = [NSMutableDictionary new];
    NSMutableSet *seen = [NSMutableSet new];
    NSArray *names = [PLProfiles.current.profiles.allKeys sortedArrayUsingSelector:@selector(compare:)];
    for (NSString *name in names) {
        NSString *dir = [PLProfiles gameDirForProfile:PLProfiles.current.profiles[name]];
        if ([seen containsObject:dir]) continue;
        [seen addObject:dir];
        result[name] = dir;
    }
    return result;
}

// Relative, so links survive iOS moving the app container to a new path on updates
static NSString *relativePath(NSString *fromDir, NSString *to) {
    NSArray *from = fromDir.stringByStandardizingPath.pathComponents, *target = to.stringByStandardizingPath.pathComponents;
    NSUInteger common = 0;
    while (common < from.count && common < target.count && [from[common] isEqualToString:target[common]]) common++;
    NSMutableArray *parts = [NSMutableArray new];
    for (NSUInteger i = common; i < from.count; i++) [parts addObject:@".."];
    [parts addObjectsFromArray:[target subarrayWithRange:NSMakeRange(common, target.count - common)]];
    return [NSString pathWithComponents:parts];
}

+ (BOOL)isLink:(NSString *)path {
    NSDictionary *attributes = [NSFileManager.defaultManager attributesOfItemAtPath:path error:nil];
    return [attributes.fileType isEqualToString:NSFileTypeSymbolicLink];
}

+ (BOOL)link:(NSString *)path targets:(NSString *)target {
    NSString *destination = [NSFileManager.defaultManager destinationOfSymbolicLinkAtPath:path error:nil];
    if (!destination) return NO;
    if (!destination.isAbsolutePath) destination = [path.stringByDeletingLastPathComponent stringByAppendingPathComponent:destination];
    return [destination.stringByStandardizingPath isEqualToString:target.stringByStandardizingPath];
}

// A link we made earlier that now points nowhere (e.g. an absolute path from before an app update)
+ (BOOL)isStaleSyncLink:(NSString *)path name:(NSString *)name {
    if (![self isLink:path]) return NO;
    NSString *destination = [NSFileManager.defaultManager destinationOfSymbolicLinkAtPath:path error:nil];
    return ![NSFileManager.defaultManager fileExistsAtPath:path] || [destination hasSuffix:[@"/synced/" stringByAppendingString:name]];
}

+ (BOOL)isEmptyDir:(NSString *)path {
    BOOL isDir = NO;
    if ([self isLink:path] || ![NSFileManager.defaultManager fileExistsAtPath:path isDirectory:&isDir] || !isDir) return NO;
    return [NSFileManager.defaultManager contentsOfDirectoryAtPath:path error:nil].count == 0;
}

+ (BOOL)makeLinkAt:(NSString *)path to:(NSString *)target {
    NSFileManager *fm = NSFileManager.defaultManager;
    [fm createDirectoryAtPath:target withIntermediateDirectories:YES attributes:nil error:nil];
    [fm createDirectoryAtPath:path.stringByDeletingLastPathComponent withIntermediateDirectories:YES attributes:nil error:nil];
    return [fm createSymbolicLinkAtPath:path withDestinationPath:relativePath(path.stringByDeletingLastPathComponent, target) error:nil];
}

+ (NSUInteger)linkedCountForFolder:(NSString *)name blocked:(NSArray<NSString *> **)blockedInstances total:(NSUInteger *)total {
    NSString *target = [self.syncedDir stringByAppendingPathComponent:name];
    NSDictionary *instances = self.instances;
    NSUInteger linked = 0;
    NSMutableArray *blocked = [NSMutableArray new];
    for (NSString *instance in [instances.allKeys sortedArrayUsingSelector:@selector(localizedCaseInsensitiveCompare:)]) {
        NSString *path = [instances[instance] stringByAppendingPathComponent:name];
        if ([self link:path targets:target]) {
            linked++;
        } else if ([NSFileManager.defaultManager fileExistsAtPath:path] && ![self isEmptyDir:path] && ![self isStaleSyncLink:path name:name]) {
            [blocked addObject:instance];
        }
    }
    if (blockedInstances) *blockedInstances = blocked;
    if (total) *total = instances.count;
    return linked;
}

// Moves an instance's own folder content into synced/, renaming on clashes so nothing is overwritten
+ (BOOL)moveContentsOf:(NSString *)source into:(NSString *)target instance:(NSString *)instance {
    NSFileManager *fm = NSFileManager.defaultManager;
    [fm createDirectoryAtPath:target withIntermediateDirectories:YES attributes:nil error:nil];
    for (NSString *item in [fm contentsOfDirectoryAtPath:source error:nil]) {
        NSString *dest = [target stringByAppendingPathComponent:item];
        if ([fm fileExistsAtPath:dest]) {
            NSString *ext = item.pathExtension;
            NSString *base = [NSString stringWithFormat:@"%@ (%@)", item.stringByDeletingPathExtension, instance];
            dest = [target stringByAppendingPathComponent:ext.length ? [base stringByAppendingPathExtension:ext] : base];
            for (int i = 2; [fm fileExistsAtPath:dest]; i++) {
                NSString *numbered = [NSString stringWithFormat:@"%@ %d", base, i];
                dest = [target stringByAppendingPathComponent:ext.length ? [numbered stringByAppendingPathExtension:ext] : numbered];
            }
        }
        if (![fm moveItemAtPath:[source stringByAppendingPathComponent:item] toPath:dest error:nil]) return NO;
    }
    return [fm removeItemAtPath:source error:nil];
}

+ (NSString *)enableFolder:(NSString *)name moveExisting:(BOOL)moveExisting {
    if (!isSafeName(name)) return @"That folder name can't be synced.";
    NSString *target = [self.syncedDir stringByAppendingPathComponent:name];
    NSArray *blocked;
    [self linkedCountForFolder:name blocked:&blocked total:nil];
    if (blocked.count && !moveExisting) {
        return [NSString stringWithFormat:@"These instances already have their own %@ folder:\n%@", name, [blocked componentsJoinedByString:@"\n"]];
    }
    NSDictionary *instances = self.instances;
    NSFileManager *fm = NSFileManager.defaultManager;
    for (NSString *instance in instances) {
        NSString *path = [instances[instance] stringByAppendingPathComponent:name];
        if ([self link:path targets:target]) continue;
        if ([self isStaleSyncLink:path name:name] || [self isEmptyDir:path]) {
            [fm removeItemAtPath:path error:nil];
        } else if ([fm fileExistsAtPath:path] && ![self moveContentsOf:path into:target instance:instance]) {
            return [NSString stringWithFormat:@"Couldn't move %@'s %@ into the synced folder.", instance, name];
        }
        if (![self makeLinkAt:path to:target]) return [NSString stringWithFormat:@"Couldn't link %@'s %@ folder.", instance, name];
    }
    NSMutableDictionary *config = self.config;
    [config[@"folders"] removeObject:name];
    [config[@"folders"] addObject:name];
    [self saveConfig:config];
    return nil;
}

+ (void)disableFolder:(NSString *)name {
    NSString *target = [self.syncedDir stringByAppendingPathComponent:name];
    NSDictionary *instances = self.instances;
    for (NSString *instance in instances) {
        NSString *path = [instances[instance] stringByAppendingPathComponent:name];
        if ([self link:path targets:target]) [NSFileManager.defaultManager removeItemAtPath:path error:nil];
    }
    NSMutableDictionary *config = self.config;
    [config[@"folders"] removeObject:name];
    [self saveConfig:config];
}

#pragma mark Launch

static NSDate *changedDate(NSString *path) {
    NSDictionary *attributes = [NSFileManager.defaultManager attributesOfItemAtPath:path error:nil];
    if (!attributes) return nil;
    NSDate *created = attributes.fileCreationDate, *modified = attributes.fileModificationDate;
    return created && [created compare:modified] == NSOrderedDescending ? created : modified;
}

static NSMutableArray *readOptions(NSString *path, NSMutableDictionary *values) {
    NSMutableArray *keys = [NSMutableArray new];
    NSString *content = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:nil];
    for (NSString *raw in [content componentsSeparatedByString:@"\n"]) {
        NSString *line = [raw stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceCharacterSet];
        NSRange colon = [line rangeOfString:@":"];
        if (colon.location == NSNotFound) continue;
        NSString *key = [line substringToIndex:colon.location];
        values[key] = [line substringFromIndex:colon.location + 1];
        [keys addObject:key];
    }
    return keys;
}

// Pandora's create_combined_options_txt: oldest to newest, so the most recently changed value wins. Resource pack
// lists stay per instance. The shared fallback joins in by its own date so a computer's changes come through,
// minus the per-device settings.
+ (void)combineOptionsInto:(NSString *)gameDir {
    NSString *fallback = [self.syncedDir stringByAppendingPathComponent:kFallbackOptions];
    NSString *current = [gameDir stringByAppendingPathComponent:@"options.txt"];
    NSMutableArray *sources = [NSMutableArray new];
    if (changedDate(fallback)) [sources addObject:@[changedDate(fallback), fallback]];
    for (NSString *dir in self.instances.allValues) {
        NSString *path = [dir stringByAppendingPathComponent:@"options.txt"];
        if (changedDate(path)) [sources addObject:@[changedDate(path), path]];
    }
    if (sources.count == 0) return;
    [sources sortUsingComparator:^NSComparisonResult(NSArray *a, NSArray *b) { return [a[0] compare:b[0]]; }];

    NSMutableDictionary *values = [NSMutableDictionary new];
    NSMutableArray *order = [NSMutableArray new];
    // Device settings start from this instance's own file, whatever its age
    NSMutableDictionary *own = [NSMutableDictionary new];
    readOptions(current, own);
    for (NSArray *source in sources) {
        NSMutableDictionary *next = [NSMutableDictionary new];
        NSArray *keys = readOptions(source[1], next);
        BOOL isCurrent = [source[1] isEqualToString:current];
        for (NSString *key in keys) {
            if (!isCurrent && ([key isEqualToString:@"resourcePacks"] || [key isEqualToString:@"incompatibleResourcePacks"])) continue;
            if (!values[key]) [order addObject:key];
            values[key] = next[key];
        }
    }
    for (NSString *key in deviceOptions()) {
        if (own[key]) values[key] = own[key];
        else if (values[key] && ![order containsObject:key]) [order addObject:key];
    }
    NSMutableString *combined = [NSMutableString new];
    for (NSString *key in order) {
        if (values[key]) [combined appendFormat:@"%@:%@\n", key, values[key]];
    }
    [combined writeToFile:fallback atomically:YES encoding:NSUTF8StringEncoding error:nil];
    [combined writeToFile:current atomically:YES encoding:NSUTF8StringEncoding error:nil];
}

+ (void)prepareGameDir:(NSString *)gameDir {
    NSFileManager *fm = NSFileManager.defaultManager;
    if ([self remoteFolder]) {
        NSLog(@"[Sync] %@", [self syncWithRemote]);
    }
    NSDictionary *config = self.config;
    NSArray *folders = config[@"folders"], *files = config[@"files"];
    if (folders.count == 0 && files.count == 0) return;
    [fm createDirectoryAtPath:gameDir withIntermediateDirectories:YES attributes:nil error:nil];

    // Links to folders that are no longer synced go, as in Pandora
    for (NSString *name in self.defaultFolders) {
        NSString *path = [gameDir stringByAppendingPathComponent:name];
        if (![folders containsObject:name] && [self link:path targets:[self.syncedDir stringByAppendingPathComponent:name]]) {
            [fm removeItemAtPath:path error:nil];
        }
    }

    for (NSString *name in files) {
        if (!isSafeName(name)) continue;
        if ([name isEqualToString:@"options.txt"]) {
            [self combineOptionsInto:gameDir];
            continue;
        }
        NSString *latest;
        NSDate *latestDate;
        for (NSString *dir in self.instances.allValues) {
            NSString *path = [dir stringByAppendingPathComponent:name];
            NSDate *date = changedDate(path);
            if (date && (!latestDate || [date compare:latestDate] == NSOrderedDescending)) {
                latest = path;
                latestDate = date;
            }
        }
        NSString *target = [gameDir stringByAppendingPathComponent:name];
        if (latest && ![latest isEqualToString:target]) {
            [fm createDirectoryAtPath:target.stringByDeletingLastPathComponent withIntermediateDirectories:YES attributes:nil error:nil];
            [fm removeItemAtPath:target error:nil];
            [fm copyItemAtPath:latest toPath:target error:nil];
        }
    }

    for (NSString *name in folders) {
        if (!isSafeName(name)) continue;
        NSString *path = [gameDir stringByAppendingPathComponent:name];
        NSString *target = [self.syncedDir stringByAppendingPathComponent:name];
        if ([self link:path targets:target]) continue;
        if ([self isStaleSyncLink:path name:name] || [self isEmptyDir:path]) [fm removeItemAtPath:path error:nil];
        if (![fm fileExistsAtPath:path] && ![self isLink:path]) {
            [self makeLinkAt:path to:target];
        } else {
            NSLog(@"[Sync] %@ in %@ has its own content; enable it again on the Syncing page to merge it", name, gameDir.lastPathComponent);
        }
    }
}

#pragma mark Computer

+ (NSURL *)remoteFolder {
    NSData *bookmark = [NSUserDefaults.standardUserDefaults dataForKey:kRemoteBookmark];
    if (!bookmark) return nil;
    BOOL stale = NO;
    NSURL *url = [NSURL URLByResolvingBookmarkData:bookmark options:0 relativeToURL:nil bookmarkDataIsStale:&stale error:nil];
    if (url && stale) [self setRemoteFolder:url];
    return url;
}

+ (NSString *)setRemoteFolder:(NSURL *)url {
    if (!url) {
        [NSUserDefaults.standardUserDefaults removeObjectForKey:kRemoteBookmark];
        return nil;
    }
    BOOL access = [url startAccessingSecurityScopedResource];
    NSError *error;
    NSData *bookmark = [url bookmarkDataWithOptions:0 includingResourceValuesForKeys:nil relativeToURL:nil error:&error];
    if (access) [url stopAccessingSecurityScopedResource];
    if (!bookmark) return error.localizedDescription ?: @"iOS didn't allow remembering that folder.";
    [NSUserDefaults.standardUserDefaults setObject:bookmark forKey:kRemoteBookmark];
    return nil;
}

// Newest modification time anywhere inside path (the path itself for a file)
+ (NSDate *)latestIn:(NSString *)path {
    NSFileManager *fm = NSFileManager.defaultManager;
    BOOL isDir = NO;
    if (![fm fileExistsAtPath:path isDirectory:&isDir]) return nil;
    NSDate *latest = [fm attributesOfItemAtPath:path error:nil].fileModificationDate;
    if (!isDir) return latest;
    NSDirectoryEnumerator *walker = [fm enumeratorAtPath:path];
    for (NSString *relative in walker) {
        NSDate *date = walker.fileAttributes.fileModificationDate;
        if (date && (!latest || [date compare:latest] == NSOrderedDescending)) latest = date;
    }
    return latest;
}

// Copies one file over another through a temporary name, keeping its date so the next sync doesn't copy it back
+ (BOOL)copyFile:(NSString *)source to:(NSString *)dest {
    NSFileManager *fm = NSFileManager.defaultManager;
    NSDate *date = [fm attributesOfItemAtPath:source error:nil].fileModificationDate;
    NSString *temp = [dest stringByAppendingString:@".amadora-sync"];
    [fm removeItemAtPath:temp error:nil];
    [fm createDirectoryAtPath:dest.stringByDeletingLastPathComponent withIntermediateDirectories:YES attributes:nil error:nil];
    if ([fm copyItemAtPath:source toPath:temp error:nil]) {
        [fm removeItemAtPath:dest error:nil];
        if ([fm moveItemAtPath:temp toPath:dest error:nil]) {
            if (date) [fm setAttributes:@{NSFileModificationDate: date} ofItemAtPath:dest error:nil];
            return YES;
        }
    }
    [fm removeItemAtPath:temp error:nil];
    return NO;
}

// Brings dest up to source: missing or different files are copied. Never deletes.
+ (void)update:(NSString *)dest from:(NSString *)source copied:(NSUInteger *)copied failed:(NSUInteger *)failed {
    NSFileManager *fm = NSFileManager.defaultManager;
    BOOL isDir = NO;
    [fm fileExistsAtPath:source isDirectory:&isDir];
    NSArray *relatives = isDir ? [fm subpathsOfDirectoryAtPath:source error:nil] : @[@""];
    for (NSString *relative in relatives) {
        NSString *from = relative.length ? [source stringByAppendingPathComponent:relative] : source;
        NSString *to = relative.length ? [dest stringByAppendingPathComponent:relative] : dest;
        NSDictionary *attributes = [fm attributesOfItemAtPath:from error:nil];
        if ([attributes.fileType isEqualToString:NSFileTypeSymbolicLink] || [from hasSuffix:@".amadora-sync"]) continue;
        if ([attributes.fileType isEqualToString:NSFileTypeDirectory]) {
            [fm createDirectoryAtPath:to withIntermediateDirectories:YES attributes:nil error:nil];
            continue;
        }
        NSDate *toDate = [fm attributesOfItemAtPath:to error:nil].fileModificationDate;
        // The whole item was judged newer on this side, so any differing file is replaced, even one dated later.
        // Two seconds of slack: SMB and some cloud drives round modification times.
        if (toDate && fabs([attributes.fileModificationDate timeIntervalSinceDate:toDate]) <= 2) continue;
        if ([self copyFile:from to:to]) (*copied)++; else (*failed)++;
    }
}

// Each world (and every other top-level item) moves as one unit, from the side changed last. Mixing region files
// from two copies of a world played on both devices would corrupt it.
+ (void)syncItem:(NSString *)local with:(NSString *)other pulled:(NSUInteger *)pulled pushed:(NSUInteger *)pushed failed:(NSUInteger *)failed {
    NSDate *localDate = [self latestIn:local], *otherDate = [self latestIn:other];
    if (localDate && (!otherDate || [localDate timeIntervalSinceDate:otherDate] > 2)) {
        [self update:other from:local copied:pushed failed:failed];
    } else if (otherDate && (!localDate || [otherDate timeIntervalSinceDate:localDate] > 2)) {
        [self update:local from:other copied:pulled failed:failed];
    }
}

+ (NSString *)syncWithRemote {
    NSURL *remote = [self remoteFolder];
    if (!remote) return @"No computer folder chosen.";
    BOOL access = [remote startAccessingSecurityScopedResource];
    NSFileManager *fm = NSFileManager.defaultManager;
    if (![fm fileExistsAtPath:remote.path]) {
        if (access) [remote stopAccessingSecurityScopedResource];
        return @"The computer folder isn't reachable. Check that the computer is on and the share or cloud drive is connected in Files.";
    }
    NSUInteger pulled = 0, pushed = 0, failed = 0;
    [self syncItem:[self.syncedDir stringByAppendingPathComponent:kFallbackOptions] with:[remote.path stringByAppendingPathComponent:kFallbackOptions]
        pulled:&pulled pushed:&pushed failed:&failed];
    for (NSString *name in self.config[@"folders"]) {
        if (!isSafeName(name)) continue;
        NSString *local = [self.syncedDir stringByAppendingPathComponent:name];
        NSString *other = [remote.path stringByAppendingPathComponent:name];
        [fm createDirectoryAtPath:local withIntermediateDirectories:YES attributes:nil error:nil];
        [fm createDirectoryAtPath:other withIntermediateDirectories:YES attributes:nil error:nil];
        NSMutableSet *children = [NSMutableSet setWithArray:[fm contentsOfDirectoryAtPath:local error:nil] ?: @[]];
        [children addObjectsFromArray:[fm contentsOfDirectoryAtPath:other error:nil] ?: @[]];
        for (NSString *child in children) {
            if ([child hasSuffix:@".amadora-sync"] || [child isEqualToString:@".DS_Store"]) continue;
            [self syncItem:[local stringByAppendingPathComponent:child] with:[other stringByAppendingPathComponent:child]
                pulled:&pulled pushed:&pushed failed:&failed];
        }
    }
    if (access) [remote stopAccessingSecurityScopedResource];
    NSString *summary = [NSString stringWithFormat:@"Synced with %@: %lu files from the computer, %lu to it", remote.lastPathComponent, (unsigned long)pulled, (unsigned long)pushed];
    return failed ? [summary stringByAppendingFormat:@", %lu failed", (unsigned long)failed] : summary;
}

@end
