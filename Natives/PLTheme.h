#import <UIKit/UIKit.h>

// Pandora Launcher's dark theme (gpui-component "Default Dark", neutral palette), shared by every launcher screen.
typedef NS_ENUM(NSInteger, PLButtonKind) {
    PLButtonKindSuccess, // green: Install, Add, Play
    PLButtonKindInfo,    // blue: Open Page, secondary actions
    PLButtonKindDanger,  // red: Delete, Kill
    PLButtonKindPlain    // bordered, no fill: segment-like choices
};

@interface PLTheme : NSObject

@property(class, nonatomic, readonly) UIColor *background; // #0a0a0a
@property(class, nonatomic, readonly) UIColor *border;     // #262626, also the selected-row pill
@property(class, nonatomic, readonly) UIColor *foreground; // #fafafa
@property(class, nonatomic, readonly) UIColor *muted;      // #a3a3a3

+ (UIColor *)colorForKind:(PLButtonKind)kind;
// Inter Regular (bundled; Pandora ships only that weight) at the given size; the system font if it failed to load
+ (UIFont *)fontOfSize:(CGFloat)size;

// Registers Inter and sets UIAppearance defaults. Call once before the first window appears.
+ (void)apply;
// Forces dark mode on a window, since Pandora has no light theme
+ (void)applyToWindow:(UIWindow *)window;

+ (UIButton *)buttonWithTitle:(NSString *)title systemImage:(NSString *)image kind:(PLButtonKind)kind;
+ (void)styleButton:(UIButton *)button kind:(PLButtonKind)kind;
// Pandora's small grey section headers ("Library", "Content", ...)
+ (UILabel *)sectionHeaderLabel:(NSString *)text;
// Rounded #262626 border used by Pandora's panels and cards
+ (void)styleCard:(UIView *)view;

@end
