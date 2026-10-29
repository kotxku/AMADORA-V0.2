#import <Foundation/Foundation.h>

// Pandora Launcher's file syncing (crates/backend/src/syncing.rs) on iOS. Synced folders live in GAME_DIR/synced and
// every instance links to them; synced files are copied from the instance that changed them last, and options.txt
// is merged key by key. Optionally mirrors GAME_DIR/synced with the synced folder of Pandora on a computer, reached
// through the Files app (an SMB share or a cloud drive), copying whichever side is newer.
@interface PLSync : NSObject

@property(class, nonatomic, readonly) NSString *syncedDir;
@property(class, nonatomic, readonly) NSArray<NSString *> *defaultFiles;   // options.txt, servers.dat, command_history.txt
@property(class, nonatomic, readonly) NSArray<NSString *> *defaultFolders; // Pandora's DEFAULT_FOLDERS

+ (BOOL)isFileEnabled:(NSString *)name;
+ (void)setFile:(NSString *)name enabled:(BOOL)enabled;
+ (BOOL)isFolderEnabled:(NSString *)name;

// Pandora's "(6/6 folders synced)": instances linked, and the instances whose own non-empty folder blocks linking
+ (NSUInteger)linkedCountForFolder:(NSString *)name blocked:(NSArray<NSString *> **)blockedInstances total:(NSUInteger *)total;

// Links every instance's folder to synced/<name>. Like Pandora it refuses while an instance has its own non-empty
// folder, unless moveExisting: then that content moves into synced/ first (name clashes get the instance's name).
// Returns nil on success or a message for the user.
+ (NSString *)enableFolder:(NSString *)name moveExisting:(BOOL)moveExisting;
// Removes the links; the content stays in synced/
+ (void)disableFolder:(NSString *)name;

// Before launch: pull from the computer, then bring this instance up to date (blocking, off the main thread)
+ (void)prepareGameDir:(NSString *)gameDir;

// The Pandora folder on a computer; nil to stop syncing with it
+ (NSURL *)remoteFolder;
+ (NSString *)setRemoteFolder:(NSURL *)url;
// Copies newer files both ways between synced/ and the computer. Returns a summary or an error message. Blocking.
+ (NSString *)syncWithRemote;

@end
