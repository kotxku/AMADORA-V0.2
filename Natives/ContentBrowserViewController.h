#import <UIKit/UIKit.h>
#import "installer/ContentInstaller.h"

// Pandora's Modrinth/CurseForge page: search a source and install straight into an instance.
@interface ContentBrowserViewController : UITableViewController

// targetProfileName set: results are filtered to that instance and Install goes straight into it (Pandora's
// "Add from Modrinth" on an instance). nil: the sidebar page, which asks which instance to install into.
- (instancetype)initWithSource:(PLContentSource)source targetProfileName:(NSString *)profileName;

// Menu icon name, as LauncherMenuViewController reads from its pages
- (NSString *)imageName;

@end
