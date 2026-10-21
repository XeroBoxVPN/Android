# Releasing XeroBox on Google Play (and as direct APKs)

Follow this from top to bottom. Status marks:
- ✅ already done in this repository;
- 👤 needs you (accounts, decisions, payments);
- ⚠️ a policy point that can block review.

Policies change: check the linked Play Console Help pages again before you submit.

## 0. What is already in place ✅

| Item | State |
|---|---|
| Package name | `com.xerobox.vpn` (`XEROBOX_APP_ID`) |
| Minimum Android | Play and direct builds: Android 7.0 (API 24). `otherLegacy` direct build: Android 5.0 (API 21) |
| Target SDK | 37. Play requires API 36+ for new apps and updates from 31 Aug 2026 |
| Processor types | The App Bundle contains arm64-v8a, armeabi-v7a, x86_64 and x86. Direct APKs: arm64-v8a, armeabi-v7a, x86_64 |
| 16 KB memory pages | The core library is 16 KB-aligned (checked: ELF LOAD align 0x4000; `zipalign -c -P 16` passes) |
| Play build differences | No self-updater or install permission; no "see all apps" or location permissions; no buy-outside-Play button |
| Account deletion | In the app (Settings → Account → Delete account) and on the web (`https://<your-domain>/delete-account`) |
| Privacy policy and terms | `https://<your-domain>/privacy` and `/terms`, served by your panel (fill in Settings → Privacy policy & terms) |
| Release build | `bundlePlayRelease` and `assemblePlayRelease` built and signed (with a test key), installed on the Android 17 emulator: sign-in, sync and VPN tunnel up, no crashes |

Not yet tested: real phones (do step 7), and the build signed with your real upload key.

## 1. Decide the signing setup 👤 (before the first upload, can't be changed later)

Google Play uses **Play App Signing**:
- **App signing key:** Google holds it, and it signs what users download.
- **Upload key:** you keep it, and you sign uploads with it. If you lose it, Google can reset it.

Choose one:

- **A. Let Google create the app signing key (simplest, safest).** Your direct APKs then carry a
  different signature from the Play version. A user can't switch between them without
  uninstalling first. Good if Play will be your main channel.
- **B. Use your own key for both.** Create one key and give it to Play ("Use my own key", via Google's
  PEPK tool) when you first set up signing. Direct APKs and Play builds then have the same
  signature and update each other. Good if you'll keep offering direct APKs too, for example
  where Play isn't available. You must protect this key forever.

**Recommendation:**
- Use **A** if you only publish on Play.
- Use **B** if you also distribute direct APKs.

Either way, create an upload key now (`SIGNING.md` step 2), in a terminal, typing the passwords
yourself:

```bat
"%LOCALAPPDATA%\AndroidBuildEnv\jdk-17\bin\keytool.exe" -genkeypair -v ^
  -keystore D:\xerobox-keys\xerobox-upload.jks -alias xerobox ^
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=XeroBox"
```

Back up the `.jks` file and the passwords in two places: an encrypted USB stick, and a password
manager. Never put them in the repository, in chat, or in email.

Add the key to `local.properties`:

```properties
KEYSTORE_FILE=D:/xerobox-keys/xerobox-upload.jks
KEYSTORE_PASS=...
ALIAS_NAME=xerobox
ALIAS_PASS=...
XEROBOX_SERVER_URL=https://api.your-domain.com
```

## 2. Build the release

```sh
cd sing-box-for-android            # Git Bash
export JAVA_HOME="C:/Users/Name/AppData/Local/AndroidBuildEnv/jdk-17"
export ANDROID_HOME="C:/Users/Name/AppData/Local/Android/Sdk"
# bump VERSION_CODE (always +1) and VERSION_NAME in version.properties first
./gradlew --console=plain clean bundlePlayRelease assemblePlayRelease assembleOtherRelease
```

Outputs:
- `app/build/outputs/bundle/playRelease/XeroBox-<version>-play-release.aab`: upload this to Play.
- `app/build/outputs/apk/play/release/*.apk`: the same build as APKs, for testing on phones.
- `app/build/outputs/apk/other/release/*.apk`: direct-download APKs.

Check the signature and alignment:

```sh
BT="$ANDROID_HOME/build-tools/37.0.0"
"$BT/apksigner.bat" verify --print-certs app/build/outputs/apk/play/release/XeroBox-*-play-arm64-v8a.apk
"$BT/zipalign.exe" -c -P 16 -v 4 app/build/outputs/apk/play/release/XeroBox-*-play-arm64-v8a.apk | tail -1
```

`apksigner` must print your certificate's SHA-256, and `zipalign` must end with
"Verification successful".

## 3. Google Play Console account 👤

1. Go to <https://play.google.com/console/signup>. You need a Google account and the one-time
   registration fee ($25).
2. **Personal or organization account:**
   - **Organization:** needs a D-U-N-S number for your company, free from Dun & Bradstreet but it
     can take days. It shows a company name on the listing, which builds trust for a paid VPN.
     Organization accounts don't have the 12-tester rule below.
   - **Personal:** faster. But since November 2023 new personal accounts must run a **closed test
     with at least 12 testers for 14 days in a row** before they can publish to production.
3. Verify your identity, and your phone and email, as the console asks.
4. ⚠️ Google Play can't be used by developers in every country (sanctions and Google's supported
   countries). Check that your country, and the countries you want to publish in, are
   supported.

## 4. Create the app 👤

Play Console → **Create app**:
- App name: "XeroBox VPN" (up to 30 characters).
- Default language.
- App, not game.
- Free (the app is free; plans are sold outside it, see 5.6).
- Accept the declarations.

## 5. App content (Policy → App content) 👤 ⚠️

Each item must be completed before you can publish.

1. **Privacy policy:** `https://api.your-domain.com/privacy`. First fill in your name and contact
   under dashboard → Settings → Privacy policy & terms. Read the page once and adjust anything
   that doesn't match how you run the servers (for example the VPN server logging paragraph).
2. **App access:** reviewers must be able to sign in. Choose "All or some functionality is
   restricted", and give a **test login code**: create a client "Google review" on a plan with
   working configs, and paste its code with the instructions "Open the app, enter this code,
   tap Sign in, tap the play button". Keep this account active.
3. **Ads:** "Yes, contains ads". The app shows its own in-app ads to free users.
4. **Content rating:** answer the questionnaire (a utility; no user-generated content shared
   publicly).
5. **Target audience:** 18+ (or 16+/13+, but not children). A VPN with payments should not
   target children.
6. ⚠️ **Data safety:** declare what the app collects (from `/privacy`):
   - **Device or other IDs:** collected (install ID, hashed Android ID). Purposes: app
     functionality, fraud prevention / security. Not shared.
   - **App info and performance → Crash logs, Diagnostics:** collected when a report is sent.
     Purpose: app functionality / analytics. Optional for the user (they can turn off automatic
     crash reports).
   - **Personal info:** none in the app. Telegram linking happens in Telegram.
   - **Financial info:** none in the app.
   - **Location:** not collected. The Play build has no location permission. If you list IP
     addresses as approximate location, declare it under "Location → Approximate location".
     Google treats IP-derived location as location data.
   - Data is **encrypted in transit**: yes.
   - **Users can request deletion:** yes, in the app and at `https://api.your-domain.com/delete-account`.
7. ⚠️ **VPN service declaration** (VpnService). Google requires it for every app that uses
   VpnService:
   - **Core functionality:** yes; the app is a VPN client.
   - **Traffic is encrypted** from the device to the VPN server: yes. Configs use VLESS with
     REALITY/TLS, Trojan, Shadowsocks 2022 and similar.
   - The app does not use the VPN to collect user data, change other apps' ads, or redirect
     traffic for money. Answer truthfully and keep it true.
   - Say in the store description that the app uses VpnService.
   - Policy: <https://support.google.com/googleplay/android-developer/answer/12564964>
8. **Foreground service types:** the app uses `systemExempted` (the VPN) and `specialUse`. Explain
   that the VPN must keep running while connected. Play may ask for a short video of the
   feature: record connecting and the notification.
9. **Permissions that may need justification:**
   - `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: explain it keeps the VPN connected in the background.
     If review objects, remove it from the Play build like the location permissions
     (`app/src/play/AndroidManifest.xml`).
   - `CAMERA`: scanning a QR code to import a profile.
10. **Government, financial, health and news apps:** not applicable.

### 5.6 Selling plans ⚠️

Google Play's payments policy requires Google Play Billing for digital subscriptions bought in
the app. In the US there are newer programs that allow external links and other payment
systems, with fees.

That is why the **Play build has no "Upgrade with Telegram" button** (`EXTERNAL_PURCHASE=false`).
It only shows the user's plan and its end date. Don't add links to the Telegram bot, prices, or
"buy on our website" texts to the Play build or its store listing. Options:
- Keep it this way. Users who already have a plan, or buy it outside the app on their own,
  just sign in.
- Later, add Google Play Billing for plans in the Play build (Google takes a service fee).

Policy: <https://support.google.com/googleplay/android-developer/answer/9858738>. US programs:
<https://support.google.com/googleplay/android-developer/answer/15582165>.

## 6. Store listing 👤

Main store listing:

| Field | What |
|---|---|
| App name | XeroBox VPN |
| Short description (80) | e.g. "Fast, private VPN: sign in with your code and connect in one tap." |
| Full description (4000) | What it does, how sign-in works, data limits on the free plan, that it uses Android's VpnService to create an encrypted tunnel, privacy policy link. No pricing or external purchase links (5.6). |
| App icon | 512×512 PNG, 32-bit. Export from `brand/` |
| Feature graphic | 1024×500 PNG/JPG |
| Phone screenshots | 2–8, PNG/JPG, 16:9 or 9:16, at least 320 px. Take them from a phone or the emulator (`adb exec-out screencap -p > shot.png`). Show: sign-in, protected dashboard, profiles, account |
| Category | Tools |
| Contact email | required, shown publicly |
| Website | optional |

Don't use the names "sing-box" or "SFA", or their logos, anywhere in the listing (license
condition, see README).

## 7. Testing tracks 👤

1. **Internal testing** (up to 100 testers, available within minutes):
   - Release → Testing → Internal testing → Create release → upload the `.aab` → add testers'
     Gmail addresses → copy the opt-in link to them.
   - First upload: choose the signing option from step 1.
2. **Pre-launch report:** after the upload, Google runs the app on real devices automatically.
   - It uses the App access login code, so make sure that code works.
   - Read the report (Release → Testing → Pre-launch report) for crashes, security and
     accessibility warnings.
3. **Closed testing:**
   - Create a track, add at least **12 testers** (better 15–20, in case some drop out), and
     keep them opted in for **14 days in a row**. Needed for new personal accounts.
   - Then apply for production access on the dashboard.
4. **Your own checks on real phones** (at least 3):
   - one new phone, Android 14–16;
   - one older, Android 9–11;
   - one cheap 32-bit or low-RAM phone.

   On each, check:
   - **Install and sign in** from Play.
   - **Sync** shows your configs.
   - **Connect:** a browser works through the VPN.
   - **Traffic guard:** use up a small allowance, and the app disconnects.
   - **Reconnect** after you add data in the dashboard.
   - **Sign out and back in.**
   - **Delete account** with a test account.
   - **Rotation, dark mode, and a large font size.**

## 8. Production 👤

Release → Production → Create release → choose the tested release → **staged rollout**:
start at 10–20%, watch Android vitals (crashes, ANRs) for 1–2 days, then 50%, then 100%.

## 9. Updates

1. Bump `VERSION_CODE` (+1) and `VERSION_NAME`.
2. Build (step 2), upload to internal testing, test, promote to production with a staged rollout.
3. Every year Google raises the required target SDK (API 36 from Aug 2026). Raise `targetSdk`,
   build, and test on the newest Android before the deadline in Play Console → Policy status.
4. Watch **Android vitals**. Google lowers visibility of apps whose user-perceived crash rate
   is above ~1.1% or ANR rate above ~0.47%.

## 10. Direct APKs (outside Play)

- **Where:** publish the `other` release APKs on GitHub Releases. The tag workflow
  (`.github/workflows/build.yml`) does this with `SHA256SUMS.txt`. You can also offer them
  through the panel's update feature (dashboard → Settings → Android app updates).
- ⚠️ **Developer verification:** from 30 Sep 2026, certified Android phones in Brazil, Indonesia,
  Singapore and Thailand will only install apps from **verified developers**, with every
  country following in 2027. Register in the Android Developer Console, verify your identity,
  and register `com.xerobox.vpn` with your signing key's SHA-256 so direct APKs keep
  installing normally. See <https://developer.android.com/developer-verification>.

## 11. Play Protect: avoiding warnings legitimately

Play Protect scans apps from Play and from other sources. Never ask users to turn it off, and
don't try to evade it. Do this instead:

- **Official channels:** distribute through Google Play, which is scanned and trusted. Register
  direct APKs with developer verification (section 10).
- **Signing:** always sign with the same release key. Never ship debug-signed builds to users.
- **Target the current SDK:** apps targeting old Android versions get "built for an older
  version" warnings.
- **Behaviour that triggers warnings, which this app avoids:**
  - loading code downloaded at runtime;
  - asking for accessibility, SMS or call-log permissions without need;
  - hiding the app icon;
  - unusual obfuscation;
  - bundling unknown SDKs.
- **A legitimate warning versus a false positive:**
  - **Legitimate:** a warning that names harmful behaviour your app really has, for example a
    third-party SDK that collects data. Fix the app.
  - **False positive:** a warning about a clean, properly signed build. To investigate:
    1. Check the exact APK users install (SHA-256 against `SHA256SUMS.txt`).
    2. Scan it on VirusTotal.
    3. Review the SDKs and permissions you added since the last clean version.
    4. Check the Play Console "Policy status" and pre-launch report.
    5. If it is still flagged, file an appeal through Google's Play Protect appeals form, from
       Play Console help, with the package name, version and SHA-256.

**Limits to expect:**
- **Device coverage:** no app is guaranteed to work on every Android device. Some
  manufacturer-modified systems restrict background VPNs or battery use, and very old phones
  may lack the memory for the VPN core. Testing on several real devices, the pre-launch report
  and staged rollouts reduce problems, but can't remove them.
- **Warnings:** no developer can guarantee that a warning will never appear. Following the steps
  above prevents the avoidable ones.
