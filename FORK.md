# XeroBox for Android: running this as your own repository

This folder started as a clone of SagerNet/sing-box-for-android (GPL-3.0). It now
carries the XeroBox brand, account system, sync, traffic guard, invites, ads, bug
reports and plans. This file explains how to keep it as an independent repository
while still taking upstream fixes.

## 1. Make it your own repository

Already done here: all XeroBox work is committed on the branch `main-xerobox`, and the
SagerNet remote is renamed `upstream` (so fixes can still be fetched). What's left is
pointing it at your own repository (Git Bash, in this folder):

```sh
git remote add origin https://github.com/<you>/xerobox-android.git   # your new, empty repo
git push -u origin main-xerobox
```

(For a fresh clone elsewhere: `git checkout -b main-xerobox`, commit, then
`git remote rename origin upstream` before adding your own `origin`.)

Create the new repository on GitHub (or Gitea, GitLab) first, empty, without a
README. Make it **public, or give every user a way to get the source**: the app is
GPL-3.0, so anyone you give the APK to may ask for the source code of that version.
Keep `LICENSE` as it is. Settings → About → Source code opens `XEROBOX_SOURCE_URL`.

Never commit: `local.properties`, `*.keystore` / `*.jks`, `service-account-credentials.json`
(all already in `.gitignore`).

## 2. What makes the build "yours" (local.properties)

| Setting | What it does | Default |
|---|---|---|
| `XEROBOX_APP_ID` | Android package name. Changing it later makes a different app (users must reinstall). | `com.xerobox.vpn` |
| `XEROBOX_SERVER_URL` | The panel built into the app, e.g. `https://api.example.com` | debug: `http://10.0.2.2:8088` (emulator → this PC) |
| `XEROBOX_GITHUB_REPO` | `owner/repo` whose GitHub releases are the fallback update source | none |
| `XEROBOX_SOURCE_URL` | Source code link in About (GPL) | your `XEROBOX_GITHUB_REPO`, else upstream |
| `KEYSTORE_FILE`, `KEYSTORE_PASS`, `ALIAS_NAME`, `ALIAS_PASS` | Release signing (see SIGNING.md) | none |

The Kotlin package stays `io.nekohasekai.sfa` on purpose: renaming every file would
make each upstream merge conflict everywhere. Users never see it; the package name
they see (and that Android uses) is `XEROBOX_APP_ID`.

Branding lives in `app/src/main/res` (icons `mipmap-*`, `drawable/ic_launcher_*`,
colours `values/xero_colors.xml`, names in `values*/strings.xml`) and in
`compose/component/XMark.kt`.

## 3. Updates

The app asks your panel first: `GET /api/v1/update?platform=android` (set the newest
build in the dashboard, Settings → Android app updates). If the panel has nothing, it falls back
to the GitHub releases of `XEROBOX_GITHUB_REPO`. Bump `VERSION_CODE` and
`VERSION_NAME` in `version.properties` for every release you publish: Android only
installs a higher `VERSION_CODE` over an existing install, and only with the same
signing key.

## 4. Build

Requirements: JDK 17, Android SDK (platform 37, build tools), NDK 28.0.13004108, Go
(the version in `version.properties`) for the core library. On this PC
`setup-build-env.ps1` in the project root installed them.

```sh
export JAVA_HOME="C:/Users/Name/AppData/Local/AndroidBuildEnv/jdk-17"
export ANDROID_HOME="C:/Users/Name/AppData/Local/Android/Sdk"
./gradlew assembleOtherDebug        # test build, app/build/outputs/apk/other/debug/
./gradlew assembleOtherRelease      # signed release (needs the keystore settings)
```

`build-apk.ps1` wraps this and copies the APKs to the project root. Flavours:
`other` (direct APK, Android 7+, with the self-updater), `otherLegacy` (Android 5+),
`play` (Google Play: no self-updater).

## 5. Taking upstream fixes

```sh
git fetch upstream --tags
git merge 1.15.0           # or the tag you want; resolve conflicts, build, test
```

Most XeroBox code is in its own files (`panel/`, `licensing/`, `config/`, the
`*Card.kt` screens, `AccountScreen.kt`, `InviteScreen.kt`, `ReportProblemScreen.kt`),
so conflicts stay in the few upstream files we changed (navigation, settings,
dashboard, `BoxService.kt`, strings, build script). After a merge, check the core
library version (`version.properties`, `libbox`) still builds with your Go version.
