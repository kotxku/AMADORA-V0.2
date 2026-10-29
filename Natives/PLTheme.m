#import <CoreText/CoreText.h>
#import "PLTheme.h"

static NSString *interName;

static UIColor *hex(uint32_t rgb) {
    return [UIColor colorWithRed:((rgb >> 16) & 0xff) / 255.0 green:((rgb >> 8) & 0xff) / 255.0 blue:(rgb & 0xff) / 255.0 alpha:1];
}

@implementation PLTheme

// Values measured from Pandora's screenshots (Tailwind neutral-950/800/50/400, green/sky/red-900)
+ (UIColor *)background { return hex(0x0a0a0a); }
+ (UIColor *)border { return hex(0x262626); }
+ (UIColor *)foreground { return hex(0xfafafa); }
+ (UIColor *)muted { return hex(0xa3a3a3); }

+ (UIColor *)colorForKind:(PLButtonKind)kind {
    switch (kind) {
        case PLButtonKindSuccess: return hex(0x14532d);
        case PLButtonKindInfo: return hex(0x0c4a6e);
        case PLButtonKindDanger: return hex(0x7f1d1d);
        case PLButtonKindPlain: return UIColor.clearColor;
    }
}

+ (UIFont *)fontOfSize:(CGFloat)size {
    UIFont *font = interName ? [UIFont fontWithName:interName size:size] : nil;
    return font ?: [UIFont systemFontOfSize:size];
}

+ (void)registerInter {
    NSURL *url = [NSBundle.mainBundle URLForResource:@"Inter-Regular" withExtension:@"ttf"];
    if (!url) return;
    CTFontManagerRegisterFontsForURL((__bridge CFURLRef)url, kCTFontManagerScopeProcess, NULL);
    NSArray *descriptors = CFBridgingRelease(CTFontManagerCreateFontDescriptorsFromURL((__bridge CFURLRef)url));
    if (descriptors.count) {
        interName = CFBridgingRelease(CTFontDescriptorCopyAttribute((__bridge CTFontDescriptorRef)descriptors.firstObject, kCTFontNameAttribute));
    }
}

+ (void)apply {
    [self registerInter];

    UINavigationBarAppearance *nav = [UINavigationBarAppearance new];
    [nav configureWithOpaqueBackground];
    nav.backgroundColor = self.background;
    nav.shadowColor = self.border;
    nav.titleTextAttributes = @{NSForegroundColorAttributeName: self.foreground, NSFontAttributeName: [self fontOfSize:18]};
    nav.largeTitleTextAttributes = @{NSForegroundColorAttributeName: self.foreground, NSFontAttributeName: [self fontOfSize:30]};
    UINavigationBar.appearance.standardAppearance = nav;
    UINavigationBar.appearance.compactAppearance = nav;
    UINavigationBar.appearance.scrollEdgeAppearance = nav;

    UIToolbarAppearance *toolbar = [UIToolbarAppearance new];
    [toolbar configureWithOpaqueBackground];
    toolbar.backgroundColor = self.background;
    toolbar.shadowColor = self.border;
    UIToolbar.appearance.standardAppearance = toolbar;
    UIToolbar.appearance.compactAppearance = toolbar;
    if (@available(iOS 15.0, *)) {
        UIToolbar.appearance.scrollEdgeAppearance = toolbar;
    }

    UITableView.appearance.backgroundColor = self.background;
    UITableView.appearance.separatorColor = self.border;
    UITableViewCell.appearance.backgroundColor = self.background;
    UICollectionView.appearance.backgroundColor = self.background;

    UISegmentedControl.appearance.selectedSegmentTintColor = self.border;
    [UISegmentedControl.appearance setTitleTextAttributes:@{NSForegroundColorAttributeName: self.foreground, NSFontAttributeName: [self fontOfSize:14]} forState:UIControlStateNormal];
    // Pandora's toggles are a white track with a dark knob when on
    UISwitch.appearance.onTintColor = self.foreground;
    UISwitch.appearance.thumbTintColor = self.background;
    UIProgressView.appearance.progressTintColor = [self colorForKind:PLButtonKindSuccess];
}

+ (void)applyToWindow:(UIWindow *)window {
    window.overrideUserInterfaceStyle = UIUserInterfaceStyleDark;
    window.tintColor = self.foreground;
    window.backgroundColor = self.background;
}

+ (UIButton *)buttonWithTitle:(NSString *)title systemImage:(NSString *)image kind:(PLButtonKind)kind {
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    [button setTitle:title forState:UIControlStateNormal];
    if (image) {
        [button setImage:[UIImage systemImageNamed:image withConfiguration:[UIImageSymbolConfiguration configurationWithPointSize:13]] forState:UIControlStateNormal];
    }
    [self styleButton:button kind:kind];
    return button;
}

+ (void)styleButton:(UIButton *)button kind:(PLButtonKind)kind {
    button.backgroundColor = [self colorForKind:kind];
    button.tintColor = self.foreground;
    [button setTitleColor:self.foreground forState:UIControlStateNormal];
    [button setTitleColor:self.muted forState:UIControlStateDisabled];
    button.titleLabel.font = [self fontOfSize:15];
    button.layer.cornerRadius = 4;
    button.layer.borderWidth = kind == PLButtonKindPlain ? 1 : 0;
    button.layer.borderColor = self.border.CGColor;
    button.contentEdgeInsets = UIEdgeInsetsMake(6, 10, 6, 10);
    if (button.currentImage) {
        button.imageEdgeInsets = UIEdgeInsetsMake(0, -4, 0, 4);
        button.contentEdgeInsets = UIEdgeInsetsMake(6, 14, 6, 10);
    }
}

+ (UILabel *)sectionHeaderLabel:(NSString *)text {
    UILabel *label = [UILabel new];
    label.text = text;
    label.font = [self fontOfSize:13];
    label.textColor = self.muted;
    return label;
}

+ (void)styleCard:(UIView *)view {
    view.backgroundColor = self.background;
    view.layer.cornerRadius = 6;
    view.layer.borderWidth = 1;
    view.layer.borderColor = self.border.CGColor;
}

@end
