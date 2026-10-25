#import "ModDoctor.h"
#import "UnzipKit.h"

@implementation ModDoctor

+ (NSMutableURLRequest *)requestFor:(NSString *)url {
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:url]];
    // Modrinth asks clients to identify themselves
    [request setValue:@"kotxku/PandoraLauncher-iOS" forHTTPHeaderField:@"User-Agent"];
    return request;
}

+ (NSData *)fetch:(NSString *)url {
    if (!url) return nil;
    __block NSData *result;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    [[NSURLSession.sharedSession dataTaskWithRequest:[self requestFor:url]
    completionHandler:^(NSData *data, NSURLResponse *response, NSError *error) {
        if (!error && [(NSHTTPURLResponse *)response statusCode] == 200) result = data;
        dispatch_semaphore_signal(done);
    }] resume];
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER);
    return result;
}

+ (NSString *)download:(NSString *)url toDirectory:(NSString *)dir {
    if (!url) return nil;
    __block NSString *saved;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    [[NSURLSession.sharedSession downloadTaskWithRequest:[self requestFor:url]
    completionHandler:^(NSURL *location, NSURLResponse *response, NSError *error) {
        // Server-supplied name is untrusted: keep only the last component
        NSString *name = response.suggestedFilename.lastPathComponent;
        if (!error && [(NSHTTPURLResponse *)response statusCode] == 200 && name.length && ![name hasPrefix:@"."]) {
            NSFileManager *fm = NSFileManager.defaultManager;
            NSString *dest = [dir stringByAppendingPathComponent:name];
            [fm createDirectoryAtPath:dir withIntermediateDirectories:YES attributes:nil error:nil];
            [fm removeItemAtPath:dest error:nil];
            if ([fm moveItemAtPath:location.path toPath:dest error:nil]) saved = name;
        }
        dispatch_semaphore_signal(done);
    }] resume];
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER);
    return saved;
}

+ (id)parseJSON:(NSData *)data {
    if (!data) return nil;
    NSJSONReadingOptions options = kNilOptions;
    if (@available(iOS 15.0, *)) {
        // fabric.mod.json files often have comments or trailing commas, which Fabric itself accepts
        options = NSJSONReadingJSON5Allowed;
    }
    return [NSJSONSerialization JSONObjectWithData:data options:options error:nil];
}

static id typed(id obj, Class cls) {
    return [obj isKindOfClass:cls] ? obj : nil;
}

// Adds the mod ids a jar provides and depends on, including mods bundled inside it (jar-in-jar).
+ (void)scanJar:(NSString *)jarPath provides:(NSMutableSet *)provides depends:(NSMutableSet *)depends {
    UZKArchive *archive = [[UZKArchive alloc] initWithPath:jarPath error:nil];
    NSDictionary *json = typed([self parseJSON:[archive extractDataFromFile:@"fabric.mod.json" error:nil]], NSDictionary.class);
    if (!json) return;

    NSString *modId = typed(json[@"id"], NSString.class);
    if (modId) [provides addObject:modId];
    for (id alias in typed(json[@"provides"], NSArray.class)) {
        if (typed(alias, NSString.class)) [provides addObject:alias];
    }
    [depends addObjectsFromArray:[typed(json[@"depends"], NSDictionary.class) allKeys] ?: @[]];

    for (NSDictionary *jar in typed(json[@"jars"], NSArray.class)) {
        NSString *inner = typed(typed(jar, NSDictionary.class)[@"file"], NSString.class);
        NSData *innerData = inner ? [archive extractDataFromFile:inner error:nil] : nil;
        if (!innerData) continue;
        // UnzipKit can only open archives from disk
        NSString *tmp = [NSTemporaryDirectory() stringByAppendingPathComponent:NSUUID.UUID.UUIDString];
        [innerData writeToFile:tmp atomically:NO];
        [self scanJar:tmp provides:provides depends:depends];
        [NSFileManager.defaultManager removeItemAtPath:tmp error:nil];
    }
}

+ (NSString *)modrinthFileURLForProject:(NSString *)project minecraftVersion:(NSString *)mcVersion loaders:(NSString *)loaders {
    NSURLComponents *url = [NSURLComponents componentsWithString:[NSString stringWithFormat:@"https://api.modrinth.com/v2/project/%@/version",
        [project stringByAddingPercentEncodingWithAllowedCharacters:NSCharacterSet.URLPathAllowedCharacterSet]]];
    url.queryItems = @[
        [NSURLQueryItem queryItemWithName:@"loaders" value:loaders],
        [NSURLQueryItem queryItemWithName:@"game_versions" value:[NSString stringWithFormat:@"[\"%@\"]", mcVersion]]
    ];
    NSDictionary *version = typed([typed([self parseJSON:[self fetch:url.URL.absoluteString]], NSArray.class) firstObject], NSDictionary.class);
    NSArray *files = typed(version[@"files"], NSArray.class);
    NSDictionary *file = files.firstObject;
    for (NSDictionary *candidate in files) {
        if ([typed(candidate, NSDictionary.class)[@"primary"] boolValue]) file = candidate;
    }
    return typed(typed(file, NSDictionary.class)[@"url"], NSString.class);
}

static NSString *normalized(NSString *name) {
    NSCharacterSet *separators = [NSCharacterSet characterSetWithCharactersInString:@"-_ "];
    return [[name.lowercaseString componentsSeparatedByCharactersInSet:separators] componentsJoinedByString:@""];
}

+ (NSString *)fileURLForModId:(NSString *)modId minecraftVersion:(NSString *)mcVersion loader:(NSString *)loader {
    NSString *loaders = [loader isEqualToString:@"quilt"] ? @"[\"quilt\",\"fabric\"]" : @"[\"fabric\"]";
    // "fabric" is the old id of Fabric API
    NSString *slug = [modId isEqualToString:@"fabric"] ? @"fabric-api" : modId;

    // Most mod ids are also their Modrinth slug
    NSOrderedSet *slugs = [NSOrderedSet orderedSetWithArray:@[slug, [slug stringByReplacingOccurrencesOfString:@"_" withString:@"-"]]];
    for (NSString *candidate in slugs) {
        NSString *url = [self modrinthFileURLForProject:candidate minecraftVersion:mcVersion loaders:loaders];
        if (url) return url;
    }

    // Otherwise search, but only accept a project whose slug or title matches the id, so we never install a lookalike
    NSURLComponents *search = [NSURLComponents componentsWithString:@"https://api.modrinth.com/v2/search"];
    search.queryItems = @[
        [NSURLQueryItem queryItemWithName:@"query" value:modId],
        [NSURLQueryItem queryItemWithName:@"limit" value:@"10"],
        [NSURLQueryItem queryItemWithName:@"facets" value:[NSString stringWithFormat:@"[[\"project_type:mod\"],[\"versions:%@\"]]", mcVersion]]
    ];
    NSDictionary *response = typed([self parseJSON:[self fetch:search.URL.absoluteString]], NSDictionary.class);
    for (NSDictionary *hit in typed(response[@"hits"], NSArray.class)) {
        NSString *hitSlug = typed(typed(hit, NSDictionary.class)[@"slug"], NSString.class);
        NSString *title = typed(hit[@"title"], NSString.class);
        NSString *projectId = typed(hit[@"project_id"], NSString.class);
        if (!projectId || (![normalized(hitSlug) isEqualToString:normalized(slug)] && ![normalized(title) isEqualToString:normalized(slug)])) continue;
        NSString *url = [self modrinthFileURLForProject:projectId minecraftVersion:mcVersion loaders:loaders];
        if (url) return url;
    }
    return nil;
}

+ (NSString *)fixMissingModsInGameDir:(NSString *)gameDir minecraftVersion:(NSString *)mcVersion loader:(NSString *)loader {
    // shortcut: Forge/NeoForge mods.toml is not parsed, only fabric.mod.json; add a TOML reader if Forge packs need it
    if (![loader isEqualToString:@"fabric"] && ![loader isEqualToString:@"quilt"]) {
        return [NSString stringWithFormat:@"Mod Doctor can only check Fabric and Quilt mods. This profile uses %@.", loader];
    }
    if (mcVersion.length == 0) {
        return @"Mod Doctor needs to know the Minecraft version. Set one in this profile first.";
    }

    NSString *modsDir = [gameDir stringByAppendingPathComponent:@"mods"];
    NSSet *builtIn = [NSSet setWithArray:@[@"minecraft", @"java", @"fabricloader", @"fabric-loader", @"quilt_loader", @"forge", @"neoforge"]];
    NSMutableArray *installed = [NSMutableArray new];
    NSMutableArray *notFound = [NSMutableArray new];
    NSMutableSet *tried = [NSMutableSet new];

    // Newly downloaded mods can have dependencies of their own
    for (int pass = 0; pass < 5; pass++) {
        NSMutableSet *provides = [NSMutableSet new];
        NSMutableSet *depends = [NSMutableSet new];
        for (NSString *file in [NSFileManager.defaultManager contentsOfDirectoryAtPath:modsDir error:nil]) {
            if ([file.pathExtension.lowercaseString isEqualToString:@"jar"]) {
                [self scanJar:[modsDir stringByAppendingPathComponent:file] provides:provides depends:depends];
            }
        }
        [depends minusSet:provides];
        [depends minusSet:builtIn];
        [depends minusSet:tried];
        if (depends.count == 0) break;

        for (NSString *modId in depends) {
            [tried addObject:modId];
            NSString *saved = [self download:[self fileURLForModId:modId minecraftVersion:mcVersion loader:loader] toDirectory:modsDir];
            if (saved) {
                [installed addObject:[NSString stringWithFormat:@"%@ (%@)", modId, saved]];
            } else {
                [notFound addObject:modId];
            }
        }
    }

    if (installed.count == 0 && notFound.count == 0) {
        return @"No missing mods. Every dependency is installed.";
    }
    NSMutableString *report = [NSMutableString new];
    if (installed.count) {
        [report appendFormat:@"Downloaded %lu missing mod(s):\n%@", (unsigned long)installed.count, [installed componentsJoinedByString:@"\n"]];
    }
    if (notFound.count) {
        [report appendFormat:@"%@Couldn't find these on Modrinth for Minecraft %@, so add them yourself:\n%@",
            report.length ? @"\n\n" : @"", mcVersion, [notFound componentsJoinedByString:@"\n"]];
    }
    return report;
}

@end
