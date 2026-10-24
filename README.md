# XeroBox for Android

The Android app of the XeroBox VPN service. It signs in to a XeroBox panel with a login or
invitation code, receives its VPN configurations from the panel and keeps them in sync, shows
the account's plan and data, and stops the connection when the data runs out.

This app is a modified version of **SFA** (sing-box for Android) by nekohasekai / SagerNet. The
VPN engine is a modified **sing-box**, kept in this repository under [`core/`](#the-vpn-core-libbox). XeroBox is not
affiliated with or endorsed by SagerNet. See [License](#license) and [NOTICE.md](NOTICE.md).

## Contents

- [Requirements](#requirements)
- [Build](#build)
- [Configuration (local.properties)](#configuration-localproperties)
- [The VPN core (libbox)](#the-vpn-core-libbox)
- [Release builds and signing](#release-builds-and-signing)
- [Keeping up with upstream](#keeping-up-with-upstream)
- [Troubleshooting](#troubleshooting)
- [License](#license)

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 17 | Temurin or the JDK bundled with Android Studio |
| Android SDK | platform 37, build-tools 37 | via Android Studio or `sdkmanager` |
| Android NDK | 28.0.13004108 | only to build the VPN core |
| Go | see `version.properties` (`GO_VERSION`) | only to build the VPN core |
| gomobile / gobind | `github.com/sagernet/gomobile` v0.1.13 | only to build the VPN core |
| Git | any | on Windows use Git Bash for the commands below |

Android Studio (any recent version) can open and build the project directly once
`app/libs/libbox.aar` exists.

## Build

```sh
git clone https://github.com/<you>/android.git
cd android
# 1. the VPN core: build app/libs/libbox.aar from ./core (see below)
# 2. tell Gradle where the SDK is
echo "sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk" > local.properties
# 3. build a debug APK
./gradlew assembleDebug
```

There is **one app build** for every user (website, Telegram, GitHub releases), Android 7 and
newer. It includes the in-app updater.

| Command | What | Where |
|---|---|---|
| `./gradlew assembleDebug` | debug APKs, for testing | `app/build/outputs/apk/debug/` |
| `./gradlew assembleRelease` | signed release APKs, for users | `app/build/outputs/apk/release/` |

APKs are split per processor: `arm64-v8a` (most phones), `armeabi-v7a` (older and budget
phones) and `x86_64` (emulators, some Chromebooks).

## Configuration (local.properties)

`local.properties` is never committed (`.gitignore`). Everything private or specific to your
deployment goes there:

```properties
sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk

# The XeroBox panel built into the app (public https address of your API; not a secret).
XEROBOX_SERVER_URL=https://api.example.com
# Android package name. Changing it later makes a different app (users must reinstall).
XEROBOX_APP_ID=com.xerobox.vpn
# owner/repo whose GitHub releases are the fallback update source (optional)
XEROBOX_GITHUB_REPO=<you>/android
# Source code link shown in Settings → About (GPL). Default: the repo above.
XEROBOX_SOURCE_URL=https://github.com/<you>/android

# Release signing (see SIGNING.md). Never commit these or the .jks file.
KEYSTORE_FILE=C:/Users/<you>/keys/xerobox-upload.jks
KEYSTORE_PASS=...
ALIAS_NAME=xerobox
ALIAS_PASS=...
```

Debug builds without `XEROBOX_SERVER_URL` use `http://10.0.2.2:8088`, which is a panel on the same
PC as seen from the Android emulator. Release builds need an `https://` address: the app refuses
plain HTTP except to 10.0.2.2 and localhost.

The APK contains no secrets: only the public API address. Accounts sign in with their own codes,
and each gets its own server credentials from the panel.

Branding:
- **App name:** `app/src/main/res/values*/strings.xml` (`app_name`).
- **Icons:** `app/src/main/res/mipmap-*`, `drawable/ic_launcher_*`.
- **Colours:** `values/xero_colors.xml`.
- **Logo:** `compose/component/XMark.kt`.

Versioning: `version.properties`. `VERSION_CODE` (a whole number, 1 for 1.0.0) must go up by at
least 1 with every release, and `VERSION_NAME` (what users see, `MAJOR.MINOR.PATCH`) must go up
too: the panel compares the code, the GitHub-release fallback compares the name. A release tag is
`v` + `VERSION_NAME` (`v1.0.0`); the workflow refuses a tag that doesn't match.

## The VPN core (libbox)

`app/libs/libbox.aar` is the VPN engine compiled for Android. It is a large build output
(about 115 MB), so it is not committed. Build it from `core/`, the XeroBox fork of sing-box with
the XeroBox config codec. Use Git Bash on Windows; gomobile fails under PowerShell:

```sh
cd core
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
go install github.com/sagernet/gomobile/cmd/gobind@v0.1.13
export ANDROID_HOME="C:/Users/<you>/AppData/Local/Android/Sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.0.13004108"
export PATH="$HOME/go/bin:$PATH"
# the sing-box version core/ is based on (version.properties CORE_VERSION) + commit
export XEROBOX_CORE_VERSION="$(grep ^CORE_VERSION= ../version.properties | cut -d= -f2 | tr -d '\r')-$(git rev-parse --short HEAD)"
go run ./cmd/internal/build_libbox -target android     # writes core/libbox.aar
mkdir -p ../app/libs && cp libbox.aar ../app/libs/
```

The GitHub Actions workflow (`.github/workflows/build.yml`) does the same on every push.

## Release builds and signing

See [SIGNING.md](SIGNING.md) to create the signing (upload) key. In short:

1. Bump `VERSION_CODE` / `VERSION_NAME` in `version.properties`.
2. `./gradlew assembleRelease` (or push a tag `v` + `VERSION_NAME`, e.g. `v1.0.1`: the workflow
   builds the APKs and attaches them with `XeroBox-version-metadata.json`, which the in-app
   GitHub update check needs).
3. Test the release APKs on real phones.
4. Publish the APKs and their SHA-256 checksums on your download page and GitHub Releases.

Google Play is not a target of this build. A Play listing would need its own compliance work
(no self-updater, Play Billing instead of outside purchase codes, fewer permissions); see the
deployment documentation.

## Keeping up with upstream

```sh
git remote add upstream https://github.com/SagerNet/sing-box-for-android.git   # once
git fetch upstream --tags
git checkout -b merge-upstream-1.15.x main-xerobox
git merge <upstream tag>                 # resolve conflicts, keeping XeroBox changes
./gradlew assembleDebug                  # build and test before merging back
git checkout main-xerobox && git merge merge-upstream-1.15.x
```

Most XeroBox code lives in its own files (`panel/`, `licensing/`, `config/`, `ext/`, the
`*Card.kt` dashboard cards, `AccountScreen.kt`, `InviteScreen.kt`, `ReportProblemScreen.kt`).
Conflicts are limited to the upstream files XeroBox changed: navigation, settings, dashboard,
`BoxService.kt`, strings, manifests, the build script. Update the core to the matching sing-box
version in the same step; it lives in `core/` with its full history, so a subtree pull merges it:

```sh
git remote add sing-box https://github.com/SagerNet/sing-box.git   # once
git fetch sing-box --tags
git subtree pull --prefix core sing-box <sing-box tag>   # resolve conflicts, keeping XeroBox changes
```

## Troubleshooting

| Problem | Fix |
|---|---|
| `Could not find libbox.aar` / unresolved `io.nekohasekai.libbox` | Build or download the core into `app/libs/` (see above). |
| `SDK location not found` | Add `sdk.dir=...` to `local.properties`. |
| gomobile panics or `=C:` env errors | Run the core build in Git Bash, not PowerShell. |
| `NDK not configured` / wrong NDK | Install NDK 28.0.13004108 and set `ANDROID_NDK_HOME`. |
| Sign-in says it can't reach the server (release build) | `XEROBOX_SERVER_URL` must be `https://` and reachable from the phone. |
| Sign-in on the emulator fails on Android 17 | Allow the "nearby devices / local network" permission when asked (needed for 10.0.2.2). |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A build signed with another key is installed: uninstall it first (`adb uninstall com.xerobox.vpn`). |
| Release build crashes but debug works | R8 shrinking: check `app/proguard-rules.pro`; run `assembleRelease` on an emulator and read `adb logcat`. |

## License

XeroBox for Android is licensed under the **GNU General Public License v3.0 or later**, the same
as the project it is based on (see [LICENSE](LICENSE)). The original notice:

```
Copyright (C) 2022 by nekohasekai <contact-sagernet@sekai.icu>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.

In addition, no derivative work may use the name or imply association
with this application without prior consent.
```

As that notice requires, this fork is published under its own name, XeroBox, and does not use
the names SFA or sing-box for itself. Anyone who receives the app, from Google Play or as an
APK, can get the complete corresponding source code here, including the VPN core (`core/`).
