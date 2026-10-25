# AMYDORA V0.1

**A [Pandora Launcher](https://github.com/Moulberry/PandoraLauncher)-inspired fork of [Angel Aura Amethyst](https://github.com/AngelAuraMC/Amethyst-iOS) for iPhone and iPad.** The goal is to make online play with custom modpacks from the player's PC seamless and easy: take the instances you already play on PC, drop them on your phone, and join the same servers.

> **About this project**
> The long-term goal of AMYDORA is a 1:1 Pandora Launcher experience on iOS, built on top of Amethyst. Amethyst already does the hard parts: running Java Minecraft on iOS, touch controls, JIT, and Microsoft login. Pandora's own interface is built on a desktop-only UI framework and launches Java as a separate process, which iOS doesn't allow, so it can't be ported directly. AMYDORA rebuilds Pandora's features on Amethyst's engine one piece at a time instead.
>
> **This release (V0.1) is a first version of that idea.** It focuses on getting your Pandora instances and other modpacks onto your phone and playable. The screens are still Amethyst's; a Pandora-style interface is planned for later versions.

**[Download the latest IPA from Releases](../../releases/latest)**

## What's new compared to Amethyst

### Add Custom Modpack
The first section of the **Profiles** screen has an **Add Custom Modpack** row. Tap it once, pick a file in the Files app, and AMYDORA creates a ready-to-play profile.

| File | What happens |
|---|---|
| **Pandora Launcher instance** (`.zip` made by `export-instances.ps1`, or a zip of an instance folder) | Reads `info_v1.json` for the Minecraft version and loader and copies the whole `.minecraft` folder (mods, configs, worlds, servers, Essential data). The instance icon is kept. |
| **Pandora-installed modpack inside an instance** | Pandora keeps modpacks as a `.mrpack` file inside `mods/` and unpacks it every time you launch. AMYDORA unpacks it once during import: it downloads every client mod (SHA-1 verified, 8 at a time), adds pack override files that are missing without overwriting your settings, and uses the pack's own Fabric loader version. Mods you switched off in Pandora stay switched off (renamed to `.jar.disabled`). The original `.mrpack` moves to `pandora-modpacks/`. |
| **Modrinth modpack** (`.mrpack`) | Installed through Amethyst's Modrinth installer with its normal progress bar. |
| **CurseForge modpack** (`.zip` with `manifest.json`) | Extracts `overrides/` and downloads each listed file from CurseForge. Files whose authors block third-party downloads are listed so you can add them by hand. |
| **Plain `.minecraft` zip** or **`.forge`** | Finds the folder containing `mods/`, `saves/` or `config/` and imports it. `.forge` isn't a standard format, so it's read as a zip. |

For **Fabric** and **Quilt** the loader is installed automatically. For **Forge** and **NeoForge** the profile is created with the right version name, and you install the loader from **+ > Forge**.

Imports show live progress ("Downloading mods: 120 of 336"), and when they finish, a report lists what was installed and anything that needs your attention.

### Mod Doctor (missing mod finder)
Mod Doctor finds mods that your pack needs but doesn't include, and downloads them into the right `mods/` folder.

- It reads every mod's `fabric.mod.json`, including mods bundled inside other mods (jar-in-jar), and lists dependencies that no installed mod provides.
- It looks each missing mod up on Modrinth for your exact Minecraft version and loader. It only accepts an exact id, slug or title match, so it never installs a look-alike.
- It repeats up to 5 times, because newly downloaded mods can need more mods.
- Mods you disabled count as present, so it won't reinstall something you turned off.
- It switches off **C2ME**, which crashes Minecraft on iOS. The jar is renamed to `.jar.disabled`, not deleted.
- **When it runs:** automatically after every `.zip` or `.forge` import. For any profile (including `.mrpack` installs), long-press it on the Profiles screen and choose **Find & Download Missing Mods**.

### Java for every popular version
The IPA bundles **Java 8, 17, 21 and 25**. The right one is picked from each Minecraft version's own metadata:

| Minecraft | Java |
|---|---|
| 1.8 - 1.16.5 | 8 |
| 1.17 - 1.20.4 | 17 |
| 1.20.5 - 1.21.x | 21 |
| 26.1 - 26.3 | 25 |

### LWJGL 3.4.1 and SDL3 for Minecraft 26.1 - 26.3
Amethyst replaces Minecraft's own LWJGL with an iOS build, and upstream only ships LWJGL 3.3.3. Minecraft 26.1 and 26.2 need **LWJGL 3.4.1**, so AMYDORA also bundles an iOS build of 3.4.1 (built from [Amethyst's LWJGL fork](https://github.com/AngelAuraMC/lwjgl3/tree/wip/rebase_3.4.1)). It also adds the GLFW functions those versions call that Amethyst's iOS GLFW layer was missing: monitor name, platform check, and IME text-input callbacks. The launcher reads each version's library list and picks 3.4.1 when the version asks for LWJGL 3.4 or newer. Every other version keeps 3.3.3, exactly as in Amethyst. Minecraft 26.3 moved its window and input from GLFW to **SDL3**. There's no SDL3 for Amethyst's iOS view, so AMYDORA includes an SDL3 bridge (`JavaApp/src_lwjgl341/org/lwjgl/sdl`). It implements the SDL functions 26.3 calls on top of Amethyst's iOS GLFW layer: every SDL window shares the one iOS surface and OpenGL context, and touch, on-screen keyboard and mouse input are translated into SDL key, text, mouse and window events with real SDL scancodes and keycodes. 26.3 asks for LWJGL 3.4.3, but every LWJGL function it calls exists in the bundled 3.4.1. `tools/check_lwjgl_refs.py` checks a `client.jar` against the bundled LWJGL and lists any method it calls that's missing.

### Builds without a Mac
Upstream builds on a private self-hosted Mac. AMYDORA builds on GitHub's hosted macOS runner (`.github/workflows/pandora.yml`), so every push produces an IPA under **Actions**.

## Install and play (iPhone with LiveContainer + StikDebug)

1. **Install:** download `AMYDORA_V0.1.ipa` from [Releases](../../releases/latest) on your iPhone, open LiveContainer, tap **+** and pick the IPA.
2. **Enable JIT:** launch AMYDORA from LiveContainer. When it asks for JIT, it opens StikDebug for you (`stikjit://` hand-off). On iOS 26+ devices it sends the Universal JIT script automatically. If StikDebug asks for a script, long-press AMYDORA in StikDebug, tap **Assign Script**, and pick `UniversalJIT26.js` from AMYDORA's data folder (AMYDORA copies it there for you).
3. **Sign in:** add your **Microsoft account** under Accounts. You need it to download Minecraft and to play on online servers. Essential's own login and social features work through your Minecraft account.
4. **Bring your PC instances over:**
   - On your PC, run [`tools/export-instances.ps1`](tools/export-instances.ps1) (PowerShell). It zips every Pandora instance from `%APPDATA%\PandoraLauncher\instances` into an `export` folder next to the script, skipping logs, crash reports and downloads.
   - Send the zip to your iPhone with AirDrop, iCloud Drive, or a USB cable to Files.
   - In AMYDORA, go to **Profiles > Add Custom Modpack** and pick the zip. Keep the app open until the report appears.
5. **Play:** select the new profile and tap **Play**. Your server list comes with the instance, so you can join the same servers as on PC.

### Controls
These come straight from Amethyst:
- **Touch controls** with fully custom layouts (Settings > Controls).
- **On-screen mouse:** tap the virtual mouse button in the control layout to get a cursor for inventories and menus. Tap it again to go back to touch aiming.
- **On-screen keyboard:** tap the keyboard button in the control layout to type in chat and on signs.
- If your layout is missing either button, add it in the controls editor as a special button ("Virtual Mouse" / "Keyboard").
- **Hardware:** Bluetooth keyboards, mice and game controllers work too.

## Known limits in V0.1
- The interface is Amethyst's, renamed; the Pandora-style screens aren't built yet.
- Mod Doctor only understands Fabric and Quilt mods (`fabric.mod.json`). It doesn't check version ranges, so a mod that's present but the wrong version isn't flagged.
- Mod Doctor doesn't run automatically after a `.mrpack` install; use the long-press action.
- Minecraft 26.3 runs through AMYDORA's SDL3 bridge, which is new. Message boxes are logged instead of shown, the Vulkan renderer is turned off (OpenGL only), and the text-input candidate window isn't supported.
- Forge and NeoForge loaders have to be installed from + > Forge after import.
- Some CurseForge files can't be downloaded by any third-party launcher.
- Very large instances (multi-GB worlds or Distant Horizons data) take a while to copy and import.

## Building it yourself
Push to your fork and GitHub Actions builds the IPA (`Build AMYDORA IPA` workflow). Locally on a Mac: `brew install ldid make`, install a JDK 8, then `gmake package BOOTJDK=<jdk8>/bin`.

## Credits and license
AMYDORA is a fork of [Angel Aura Amethyst](https://github.com/AngelAuraMC/Amethyst-iOS) and is licensed under the **GPL-3.0**, same as upstream. The idea and the instance format come from [Pandora Launcher](https://github.com/Moulberry/PandoraLauncher). AMYDORA isn't affiliated with or endorsed by the Amethyst or Pandora Launcher teams.

---

*Original Amethyst README follows.*

# Angel Aura Amethyst (iOS)
[![Development build](https://github.com/AngelAuraMC/Amethyst-iOS/actions/workflows/development.yml/badge.svg?branch=main)](https://github.com/AngelAuraMC/Amethyst-iOS/actions/workflows/development.yml)
[![Crowdin](https://badges.crowdin.net/angelauramc/localized.svg)](https://crowdin.com/project/angelauramc)
[![Discord](https://img.shields.io/discord/724163890803638273.svg?label=&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/5ptqkyZxEy)



## Introduction
Amethyst is a Minecraft: Java Edition launcher for Android, iOS, and iPadOS, based off of zhuowei's [Boardwalk](https://github.com/zhuowei/Boardwalk) project.
* Supports most versions of Minecraft: Java Edition, from the very first beta to the newest snapshots.
* Supports Forge, Fabric, OptiFine, and Quilt for you to customize the experience with supported mods.
* Includes customizable on-screen controls, keyboard and mouse support, and game controller support.
* Optimized for jailbroken and TrollStore devices to enable better capabilities.
* Microsoft account and demo mode support for logging into Minecraft.
* ...and much more!

This repository contains the code for our iOS and iPadOS port of Amethyst. Looking for [Android?](https://github.com/AngelAuraMC/Amethyst-Android)

## Getting started with Amethyst
The [Amethyst wiki](https://wiki.angelauramc.dev/wiki/getting_started/INSTALL.html#ios) has extensive documentation on how to install, set up, and play! For those who wish to install quickly, here's the basics:

### Requirements
At the minimum, you'll need one of the following devices on **iOS 14.0** and later:
- iPhone 6s and later
- iPad (5th generation) and later
- iPad Air (2nd generation) and later
- iPad mini (4th generation) and later
- iPad Pro (all models)
- iPod touch (7th generation)

However, we recommend one of the following devices on **iOS 14.0** and later:
- iPhone XS and later, excluding iPhone XR and iPhone SE (2nd generation)
- iPad (10th generation) and later
- iPad Air (4th generation) and later
- iPad mini (6th generation) and later
- iPad Pro (all models, except for 9.7-inch)

Recommended devices provide a smoother and more enjoyable gameplay experience compared to other supported devices.
- iOS 17.x and iOS 18.x is supported. However, a computer is required. For more information, please check out [the official wiki](https://wiki.angelauramc.dev/wiki/faq/ios/JIT.html#what-are-the-methods-to-enable-jit)

### Setting up to sideload
Amethyst can be sideloaded in many ways. Our recommended solution is to install [TrollStore](https://github.com/opa334/TrollStore) if your iOS version supports it. Installing with TrollStore allows you to permanently sign the application, automatically enable JIT, and increase memory limits.

If you cannot, [AltStore](https://altstore.io) and [SideStore](https://sidestore.io) are your next best options.
- Signing services that do not use your UDID (and use distribution certificates) are not supported, as Amethyst requires capabilities they do not allow. However, if you do managed to gain access to a Development certificate, due to it having the necessary entitlement (being com.apple.security.get-task-allow) to attach a debugger to the running process (enabling JIT), you may use a Development certificate.
  
- Only install sideloading software and Amethyst from trusted sources. We are not responsible for any harm caused by using unofficial software.
- Jailbreaks also benefit from permenant signing, autoJIT, and increased memory limits. However, we do not recommend them on devices intended for regular use.

### Installing Amethyst
#### Release build (TrollStore)
1. Download an IPA of Amethyst in [Releases](https://github.com/AngelAuraMC/Amethyst-iOS/releases).
2. Open the package in TrollStore using the share menu.

#### Release build (AltStore/SideStore trusted source)
These builds will be available soon, stay tuned.

#### Nightly builds
*These builds can contain game-breaking bugs. Use with caution.*
1. Download an IPA build of Amethyst in the [Actions tab](https://github.com/AngelAuraMC/Amethyst-iOS/actions).
2. Open the downloaded IPA in your sideloading app to install.

#### Nightly builds (AltStore/SideStore trusted sources)
These builds will be available soon, stay tuned.

### Enabling JIT
Amethyst makes use of **just-in-time compilation**, or JIT, to provide usable speeds for the end user. JIT is not supported on iOS without the application being debugged, so workarounds are required to enable it. You can use this chart to determine the best solution for you and your setup.
| Application         | AltStore | SideStore | StikDebug | TrollStore | Jitterbug          | Jailbroken |
|---------------------|----------|-----------|-----------|------------|--------------------|------------|
| Requires ext-device | Yes      | Yes (#)   | Yes (#)   | No         | If VPN unavailable | No         |
| Requires Wi-Fi      | Yes      | Yes (#)   | Yes (#)   | No         | Yes                | No         |
| Auto enabled        | Yes (*)  | No        | Yes       | Yes        | No                 | Yes        |

(*) AltServer running on the local network is required.
(#) Only the first time.

## Contributors
Amethyst is amazing, and surprisingly stable, and it wouldn't be this way without the commmunity that helped and contribute to the project! Some notable names:

@crystall1nedev - Project manager, iOS port developer  
@khanhduytran0 - iOS port developer  
@artdeell  
@Mathius-Boulay  
@zhuowei  
@jkcoxson   
@Diatrus 

## Third party components and their licenses
- [Caciocavallo](https://github.com/PojavLauncherTeam/caciocavallo): [GNU GPLv2 License](https://github.com/PojavLauncherTeam/caciocavallo/blob/master/LICENSE).
- [jsr305](https://code.google.com/p/jsr-305): [3-Clause BSD License](http://opensource.org/licenses/BSD-3-Clause).
- [Boardwalk](https://github.com/zhuowei/Boardwalk): [Apache 2.0 License](https://github.com/zhuowei/Boardwalk/blob/master/LICENSE) 
- [GL4ES](https://github.com/ptitSeb/gl4es) by @lunixbochs @ptitSeb: [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [MetalANGLE](https://github.com/khanhduytran0/metalangle) by @kakashidinho and ANGLE team: [BSD 2.0 License](https://github.com/kakashidinho/metalangle/blob/master/LICENSE).
- [MoltenVK](https://github.com/KhronosGroup/MoltenVK): [Apache 2.0 License](https://github.com/KhronosGroup/MoltenVK/blob/master/LICENSE).
- [openal-soft](https://github.com/kcat/openal-soft): [LGPLv2 License](https://github.com/kcat/openal-soft/blob/master/COPYING).
- [Azul Zulu JDK](https://www.azul.com/downloads/?package=jdk): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
- [LWJGL3](https://github.com/PojavLauncherTeam/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [LWJGLX](https://github.com/PojavLauncherTeam/lwjglx) (LWJGL2 API compatibility layer for LWJGL3): unknown license.
- [DBNumberedSlider](https://github.com/khanhduytran0/DBNumberedSlider): [Apache 2.0 License](https://github.com/immago/DBNumberedSlider/blob/master/LICENSE)
- [fishhook](https://github.com/khanhduytran0/fishhook): [BSD-3 License](https://github.com/facebook/fishhook/blob/main/LICENSE).
- [shaderc](https://github.com/khanhduytran0/shaderc) (used by Vulkan rendering mods): [Apache 2.0 License](https://github.com/google/shaderc/blob/main/LICENSE).
- [NRFileManager](https://github.com/mozilla-mobile/firefox-ios/tree/b2f89ac40835c5988a1a3eb642982544e00f0f90/ThirdParty/NRFileManager): [MPL-2.0 License](https://www.mozilla.org/en-US/MPL/2.0)
- [AltKit](https://github.com/rileytestut/AltKit)
- [UnzipKit](https://github.com/abbeycode/UnzipKit): [BSD-2 License](https://github.com/abbeycode/UnzipKit/blob/master/LICENSE).
- [DyldDeNeuralyzer](https://github.com/xpn/DyldDeNeuralyzer): bypasses Library Validation for loading external runtime
- Thanks to [MCHeads](https://mc-heads.net) for providing Minecraft avatars.
