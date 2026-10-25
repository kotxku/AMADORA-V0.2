#import "installer/FabricUtils.h"
#import "installer/ModDoctor.h"
#import "installer/PandoraImport.h"
#import "PLProfiles.h"
#import "UnzipKit.h"

@implementation PandoraImport

static id typed(id obj, Class cls) {
    return [obj isKindOfClass:cls] ? obj : nil;
}

+ (NSDictionary<NSString *, NSString *> *)loaderInfoForVersionId:(NSString *)versionId {
    // shortcut: assumes the Minecraft version has no '-', so snapshot/pre-release ids are misread; store the version in the profile if that matters
    NSArray<NSString *> *parts = [versionId componentsSeparatedByString:@"-"];
    if ([versionId hasPrefix:@"fabric-loader-"]) return @{@"loader": @"fabric", @"mc": parts.lastObject};
    if ([versionId hasPrefix:@"quilt-loader-"]) return @{@"loader": @"quilt", @"mc": parts.lastObject};
    if ([versionId containsString:@"-forge-"]) return @{@"loader": @"forge", @"mc": parts.firstObject};
    if ([versionId hasPrefix:@"neoforge-"]) return @{@"loader": @"neoforge", @"mc": @""};
    return @{@"loader": @"vanilla", @"mc": versionId ?: @""};
}

+ (BOOL)entries:(NSDictionary *)entries haveDirectory:(NSString *)dir {
    NSString *prefix = [dir stringByAppendingString:@"/"];
    for (NSString *name in entries) {
        if ([name hasPrefix:prefix]) return YES;
    }
    return NO;
}

+ (NSString *)importArchiveAtPath:(NSString *)path {
    NSFileManager *fm = NSFileManager.defaultManager;
    NSError *error;
    UZKArchive *archive = [[UZKArchive alloc] initWithPath:path error:&error];
    NSArray<UZKFileInfo *> *infos = [archive listFileInfo:&error];
    if (!infos) {
        return [NSString stringWithFormat:@"Couldn't open %@ as a zip: %@", path.lastPathComponent, error.localizedDescription];
    }

    // Windows tools write "./" prefixes and backslashes
    NSMutableDictionary<NSString *, UZKFileInfo *> *entries = [NSMutableDictionary new];
    for (UZKFileInfo *info in infos) {
        NSString *name = [info.filename stringByReplacingOccurrencesOfString:@"\\" withString:@"/"];
        while ([name hasPrefix:@"./"]) name = [name substringFromIndex:2];
        if (name.length) entries[name] = info;
    }

    // The metadata file closest to the archive root decides the format
    NSString *metaPath;
    for (NSString *name in entries) {
        if (![@[@"info_v1.json", @"manifest.json"] containsObject:name.lastPathComponent]) continue;
        if (!metaPath || name.pathComponents.count < metaPath.pathComponents.count) metaPath = name;
    }

    NSString *name = path.lastPathComponent.stringByDeletingPathExtension;
    NSString *mcVersion, *loader, *loaderVersion;
    NSArray *curseForgeFiles;
    NSString *baseDir = metaPath.stringByDeletingLastPathComponent ?: @"";
    NSDictionary *meta = metaPath ? typed([ModDoctor parseJSON:[archive extractData:entries[metaPath] error:nil]], NSDictionary.class) : nil;
    if ([metaPath.lastPathComponent isEqualToString:@"manifest.json"] && !typed(meta[@"minecraft"], NSDictionary.class)) {
        // Some mod's own manifest.json, not a CurseForge pack
        meta = nil;
        baseDir = @"";
    }
    if ([metaPath.lastPathComponent isEqualToString:@"info_v1.json"]) {
        // Pandora Launcher instance
        mcVersion = typed(meta[@"minecraft_version"], NSString.class);
        loader = typed(meta[@"loader"], NSString.class);
        if (baseDir.length) name = baseDir.lastPathComponent;
    } else if (meta) {
        // CurseForge modpack
        NSDictionary *minecraft = typed(meta[@"minecraft"], NSDictionary.class);
        mcVersion = typed(minecraft[@"version"], NSString.class);
        name = typed(meta[@"name"], NSString.class) ?: name;
        curseForgeFiles = typed(meta[@"files"], NSArray.class);
        // e.g. "fabric-0.15.11", "forge-47.2.0", "neoforge-21.1.77"
        NSString *loaderId = typed(typed([typed(minecraft[@"modLoaders"], NSArray.class) firstObject], NSDictionary.class)[@"id"], NSString.class);
        NSRange dash = [loaderId rangeOfString:@"-"];
        if (loaderId && dash.location != NSNotFound) {
            loader = [loaderId substringToIndex:dash.location];
            loaderVersion = [loaderId substringFromIndex:dash.location + 1];
        }
    }
    loader = loader.lowercaseString ?: @"vanilla";

    // Find the folder inside the archive that holds the game files
    NSString *root = baseDir;
    for (NSString *sub in @[@".minecraft", @"minecraft", @"overrides"]) {
        NSString *candidate = [baseDir stringByAppendingPathComponent:sub];
        if ([self entries:entries haveDirectory:candidate]) {
            root = candidate;
            break;
        }
    }
    if (!meta) {
        // Plain zip: the game folder is whichever holds mods/, saves/ or config/
        root = nil;
        NSUInteger rootDepth = NSUIntegerMax;
        for (NSString *entry in entries) {
            NSArray *components = [entry componentsSeparatedByString:@"/"];
            for (NSUInteger i = 0; i < components.count - 1 && i < rootDepth; i++) {
                if ([@[@"mods", @"saves", @"config"] containsObject:components[i]]) {
                    root = [[components subarrayWithRange:NSMakeRange(0, i)] componentsJoinedByString:@"/"];
                    rootDepth = i;
                    break;
                }
            }
        }
        if (!root) {
            return @"This archive has no mods, saves or config folder, so it doesn't look like a Minecraft instance or modpack.";
        }
        if (root.length) name = root.lastPathComponent;
    }

    // Pick a free folder name for the new instance
    NSString *safeName = [[name componentsSeparatedByCharactersInSet:[NSCharacterSet characterSetWithCharactersInString:@"/\\:"]] componentsJoinedByString:@"_"];
    if (safeName.length == 0 || [safeName hasPrefix:@"."]) safeName = [@"Imported" stringByAppendingString:safeName];
    NSString *gameDirs = [NSString stringWithFormat:@"%s/custom_gamedir", getenv("GAME_DIR")];
    NSString *folderName = safeName;
    for (int i = 2; [fm fileExistsAtPath:[gameDirs stringByAppendingPathComponent:folderName]]; i++) {
        folderName = [NSString stringWithFormat:@"%@ (%d)", safeName, i];
    }
    NSString *destDir = [gameDirs stringByAppendingPathComponent:folderName];

    // Stream every file to disk; whole-file extraction would run out of memory on multi-GB worlds
    NSString *prefix = root.length ? [root stringByAppendingString:@"/"] : @"";
    for (NSString *entry in entries) {
        if (![entry hasPrefix:prefix]) continue;
        NSString *relative = [entry substringFromIndex:prefix.length];
        // Zip entries are untrusted: never write outside the instance folder
        if (relative.length == 0 || [relative hasPrefix:@"/"] || [[relative componentsSeparatedByString:@"/"] containsObject:@".."]) continue;
        UZKFileInfo *info = entries[entry];
        NSString *dest = [destDir stringByAppendingPathComponent:relative];
        if (info.isDirectory || [entry hasSuffix:@"/"]) {
            [fm createDirectoryAtPath:dest withIntermediateDirectories:YES attributes:nil error:nil];
            continue;
        }
        [fm createDirectoryAtPath:dest.stringByDeletingLastPathComponent withIntermediateDirectories:YES attributes:nil error:nil];
        [fm createFileAtPath:dest contents:nil attributes:nil];
        NSFileHandle *handle = [NSFileHandle fileHandleForWritingAtPath:dest];
        BOOL extracted = handle && [archive extractBufferedDataFromFile:info.filename error:&error action:^(NSData *chunk, CGFloat percent) {
            [handle writeData:chunk];
        }];
        [handle closeFile];
        if (!extracted) {
            return [NSString stringWithFormat:@"Failed to extract %@: %@\nPartly imported files are in custom_gamedir/%@.", relative, error.localizedDescription, folderName];
        }
    }

    NSMutableArray<NSString *> *notes = [NSMutableArray new];

    // CurseForge packs only list mod ids; the website's download endpoint needs no API key
    NSUInteger curseForgeFailed = 0;
    for (NSDictionary *file in curseForgeFiles) {
        id projectID = typed(file, NSDictionary.class)[@"projectID"], fileID = typed(file, NSDictionary.class)[@"fileID"];
        if (!typed(projectID, NSNumber.class) || !typed(fileID, NSNumber.class)) continue;
        // shortcut: everything goes to mods/, so resource packs listed in the manifest land there too
        NSString *url = [NSString stringWithFormat:@"https://www.curseforge.com/api/v1/mods/%@/files/%@/download", projectID, fileID];
        if (![ModDoctor download:url toDirectory:[destDir stringByAppendingPathComponent:@"mods"]]) curseForgeFailed++;
    }
    if (curseForgeFailed) {
        [notes addObject:[NSString stringWithFormat:@"%lu CurseForge file(s) couldn't be downloaded, usually because their authors block third-party downloads. Get them from curseforge.com and put them in mods/.", (unsigned long)curseForgeFailed]];
    }

    // Work out which version to launch, installing the loader profile when we can
    NSString *versionId = mcVersion ?: @"latest-release";
    if (!mcVersion) {
        [notes addObject:@"This zip doesn't say which Minecraft version it's for. Edit the profile and pick the right version."];
    } else if ([loader isEqualToString:@"fabric"] || [loader isEqualToString:@"quilt"]) {
        NSDictionary *endpoints = FabricUtils.endpoints[[loader isEqualToString:@"fabric"] ? @"Fabric" : @"Quilt"];
        if (!loaderVersion) {
            for (NSDictionary *candidate in typed([ModDoctor parseJSON:[ModDoctor fetch:endpoints[@"loader"]]], NSArray.class)) {
                if (!typed(candidate, NSDictionary.class) || (candidate[@"stable"] && ![candidate[@"stable"] boolValue])) continue;
                loaderVersion = typed(candidate[@"version"], NSString.class);
                if (loaderVersion) break;
            }
        }
        NSData *profileJSON = loaderVersion ? [ModDoctor fetch:[NSString stringWithFormat:endpoints[@"json"], mcVersion, loaderVersion]] : nil;
        if (profileJSON) {
            versionId = [NSString stringWithFormat:@"%@-loader-%@-%@", loader, loaderVersion, mcVersion];
            NSString *versionDir = [NSString stringWithFormat:@"%s/versions/%@", getenv("GAME_DIR"), versionId];
            [fm createDirectoryAtPath:versionDir withIntermediateDirectories:YES attributes:nil error:nil];
            [profileJSON writeToFile:[versionDir stringByAppendingFormat:@"/%@.json", versionId] atomically:YES];
        } else {
            [notes addObject:[NSString stringWithFormat:@"Couldn't download the %@ loader for Minecraft %@. Check your connection, or install it from + > Fabric/Quilt and select it in this profile.", loader, mcVersion]];
        }
    } else if ([loader isEqualToString:@"forge"]) {
        // Same id the Forge installer creates, so the profile works once Forge is installed
        if (loaderVersion) versionId = [NSString stringWithFormat:@"%@-forge-%@", mcVersion, loaderVersion];
        [notes addObject:[NSString stringWithFormat:@"Forge has to be installed separately: tap + > Forge, install it for Minecraft %@, then select it in this profile.", mcVersion]];
    } else if ([loader isEqualToString:@"neoforge"]) {
        if (loaderVersion) versionId = [NSString stringWithFormat:@"neoforge-%@", loaderVersion];
        [notes addObject:[NSString stringWithFormat:@"NeoForge has to be installed separately with its installer jar for Minecraft %@, then selected in this profile.", mcVersion]];
    }

    NSMutableDictionary *profile = @{
        @"name": folderName,
        @"gameDir": [@"./custom_gamedir" stringByAppendingPathComponent:folderName],
        @"lastVersionId": versionId
    }.mutableCopy;
    NSData *icon = entries[[baseDir stringByAppendingPathComponent:@"icon.png"]] ? [archive extractData:entries[[baseDir stringByAppendingPathComponent:@"icon.png"]] error:nil] : nil;
    if (icon) {
        profile[@"icon"] = [@"data:image/png;base64," stringByAppendingString:[icon base64EncodedStringWithOptions:0]];
    }
    dispatch_sync(dispatch_get_main_queue(), ^{
        PLProfiles.current.profiles[folderName] = profile;
        PLProfiles.current.selectedProfileName = folderName; // also saves
    });

    NSString *doctor = [ModDoctor fixMissingModsInGameDir:destDir minecraftVersion:mcVersion loader:loader];
    return [NSString stringWithFormat:@"Imported \"%@\" (%@ %@).\n\n%@%@%@",
        folderName, loader, mcVersion ?: @"unknown version",
        notes.count ? [notes componentsJoinedByString:@"\n\n"] : @"",
        notes.count ? @"\n\n" : @"", doctor];
}

@end
