#import <UIKit/UIKit.h>

void loadPreferences(BOOL reset);
void toggleIsolatedPref(BOOL forceEnable);

id getPrefObject(NSString *key);
BOOL getPrefBool(NSString *key);
float getPrefFloat(NSString *key);
NSInteger getPrefInt(NSString *key);

void setPrefObject(NSString *key, id value);
void setPrefBool(NSString *key, BOOL value);
void setPrefFloat(NSString *key, float value);
void setPrefInt(NSString *key, NSInteger value);

void resetWarnings();

BOOL getEntitlementValue(NSString *key);

UIEdgeInsets getDefaultSafeArea();
CGRect getSafeArea(CGRect screenBounds);
void setSafeArea(CGSize screenSize, CGRect safeArea);

NSString* getSelectedJavaHome(NSString* defaultJRETag, int minVersion);
// Java major version -> JAVA_HOME of every runtime that can run on this device
NSDictionary<NSNumber *, NSString *> *installedJavaRuntimes(void);
// Lowest installed Java >= required (preferred instead when it is installed and >= required); 0 if none qualifies
int selectJavaRuntime(int required, int preferred, NSString **javaHome);
BOOL canLoadUnsignedLibraries(void);

NSArray* getRendererKeys(BOOL containsDefault);
NSArray* getRendererNames(BOOL containsDefault);
