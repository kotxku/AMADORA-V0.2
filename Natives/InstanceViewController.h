#import <UIKit/UIKit.h>

// Pandora's instance page on a phone: the instance's mods, resource packs and shaders, with switches to turn them
// on or off, delete, and add from Modrinth, CurseForge or a file. Settings opens the existing profile editor.
@interface InstanceViewController : UITableViewController

- (instancetype)initWithProfileName:(NSString *)profileName;

@end
