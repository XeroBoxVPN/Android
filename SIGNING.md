# XeroBox Android: code signing

## How it works on Android (differs from Windows)
- Every APK must be signed, but **no paid certificate or CA is involved**. You make your own key
  (self-signed) and it costs nothing.
- The key is the app's identity. Android installs an update **only if it is signed with the same
  key** as the installed app. With the direct-APK route, **a lost key means no updates for your
  existing users, ever**: they'd have to uninstall and reinstall. Back the key up.
- Our build signs with APK Signature Scheme v2 and v3 automatically. The `otherLegacy` build
  (minSdk 21) also gets v1. Nothing to configure.
- Today's test APKs (`VPN-v1-debug-*.apk`) use the **debug key** on this PC. Testers who install
  them must uninstall before installing a release-signed build.

## The options
| # | Option | Who holds the key | Cost | Good for | Risk |
|---|---|---|---|---|---|
| 1 | **Own keystore** (`.jks` file + passwords) | You | Free | **Direct APK (our choice now)** | Lost file or password = no more updates. Stolen = someone can ship "updates" as you |
| 2 | **Play App Signing** | Google holds the app key; you hold an *upload key* | Free (Play account $25 once) | Play Store | Lost upload key can be reset by Google. Direct APKs need care, see 3 |
| 3 | **Both stores, one key**: option 1 key, then uploaded to Play ("use my own app signing key") | You + Google | Free | Direct APK now, Play later, and users can move between them | Must decide **before** the first Play upload: it can't be changed afterwards |
| 4 | **Key in a hardware vault** (HSM / cloud KMS: Google Cloud KMS, AWS CloudHSM, YubiHSM), signed with `apksigner` via PKCS#11 | Vault, never a file | ~$1–5/month (cloud KMS) | Teams, CI, "key can't be copied" | More setup; the vault account becomes the thing to protect |
| 5 | **CI signing**: key as a secret in the build server | CI secret store | Free | Automated releases | Anyone with CI admin can sign. Combine with 4 for strong protection |

**Recommendation:** start with **1** for the direct-APK test. Keep the file offline with two backups,
for example an encrypted USB stick plus a password manager. Pick **3** if Play comes later, so the
same key covers both. Move to **4** once more than one person releases.

Android also supports **key rotation** (APK Signature Scheme v3.1, `apksigner rotate`): a stolen or
weak key can be replaced while users keep updating. Do it only when needed.

## Step by step: our own key (option 1)
1. **Choose the final package name first.** `io.nekohasekai.sfa` belongs to the upstream app and
   clashes with it, for example `com.xerobox.vpn`. The package name and the key together are the app's
   identity, and neither can change after users install it. *(Your decision. Not changed yet.)*
2. **Create the key** (JDK 17 is already installed). Run it in a terminal, type the passwords yourself and **never paste them in chat**:
   ```bat
   "%LOCALAPPDATA%\AndroidBuildEnv\jdk-17\bin\keytool.exe" -genkeypair -v ^
     -keystore D:\xerobox-keys\xerobox-release.jks -alias xerobox ^
     -keyalg RSA -keysize 4096 -validity 10000 ^
     -dname "CN=XeroBox, O=<your company>, C=<country>"
   ```
   Keep the `.jks` **outside** the project folder. `*.jks` and `*.keystore` are git-ignored anyway.
3. **Tell the build where it is.** Add to `sing-box-for-android\local.properties`, which is git-ignored:
   ```properties
   KEYSTORE_FILE=D:/xerobox-keys/xerobox-release.jks
   KEYSTORE_PASS=…
   ALIAS_NAME=xerobox
   ALIAS_PASS=…
   ```
   On a CI server, put the same lines base64-encoded in the `LOCAL_PROPERTIES` secret. The build
   already reads it.
   Once `KEYSTORE_PASS` is set, **debug builds are signed with the release key too**, so test builds
   and later releases upgrade over each other.
4. **Build and check:**
   ```bash
   ./gradlew --console=plain assembleOtherRelease
   "$ANDROID_HOME/build-tools/37.0.0/apksigner.bat" verify --print-certs \
     app/build/outputs/apk/other/release/SFA-*-arm64-v8a-release.apk
   ```
   Note the **SHA-256 certificate digest**. The update system (`UPDATE_PLAN.md`, phase 4) checks
   downloads against it, and it is what you register in the next step.

## Direct APKs and Google's developer verification
Google now requires apps installed on certified Android phones to come from a **verified
developer**, sideloaded APKs included. It started in some countries in 2026 and is being extended to more. For a direct-APK app this means:
register in the **Android Developer Console**, verify your identity and register the package name
together with the key's SHA-256 digest from step 4. Without that, installs on affected devices can
be blocked. Check the current country list and dates on developer.android.com before release.

## Never
- Commit the `.jks` or the passwords, or send them by chat or email.
- Use the upstream `release.keystore` in this folder. It isn't ours, and its passwords are unknown.
- Sign customer builds with the debug key.
