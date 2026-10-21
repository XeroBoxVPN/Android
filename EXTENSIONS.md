# XeroBox Android: adding your library (add-ons)

This guide is for teams who ship a library (`.aar` / `.jar`) that should run inside the XeroBox
Android app, for example analytics, crash reporting or an ad network. You don't need to understand
the app. You write **one class** and add **one line**.

## The 3 steps
1. **Drop the file** into `app/libs/`, named `ext-<yourname>.aar` (or `.jar`).
   Gradle picks up every `ext-*` file automatically (`app/build.gradle.kts`, "Add-on libraries").
   A local `.aar` does **not** bring its own dependencies. List them in the same block, or give us
   a Maven coordinate instead.
2. **Write an adapter**: a class that extends `io.nekohasekai.sfa.ext.XeroBoxExtension` and
   overrides only the callbacks you need. It can live in your library or next to
   `app/src/main/java/io/nekohasekai/sfa/ext/SampleExtension.kt`.
3. **Register it**: add one line in `app/src/main/java/io/nekohasekai/sfa/ext/InstalledExtensions.kt`:
   ```kotlin
   add(com.partner.analytics.PartnerAnalyticsExtension())
   ```
   To switch your library off later, delete that line. Nothing else changes.

## Kotlin example
```kotlin
class PartnerAnalyticsExtension : XeroBoxExtension() {
    override val id = "partner.analytics"            // never change once shipped

    override fun onAppStart(context: Context) = PartnerSdk.init(context, apiKey = "…")
    override fun onVpnStateChanged(state: ExtVpnState, profileName: String) {
        if (state == ExtVpnState.Connected) PartnerSdk.track("vpn_connected")
    }
    override fun onAdClicked(ad: ExtAd) = PartnerSdk.track("ad_click", ad.id)

    // Sent to the XeroBox panel with every sync; shown on the client's page.
    override fun syncPayload() = JSONObject().put("sessions", PartnerSdk.sessionCount())
}
```

## Java example
```java
public final class PartnerCrashExtension extends XeroBoxExtension {
  @Override public String getId() { return "partner.crash"; }
  @Override public void onAppStart(Context context) { PartnerCrash.install(context); }
}
```

## Where the app calls you (every place is marked `ADD-ON HOOK` in the code)
| App section | Callback | Code location |
|---|---|---|
| App start | `onAppStart(context)`, then `onSignedIn` if already signed in | `Application.kt:84` |
| Account: sign-in with a login code | `onSignedIn(account)` | `panel/PanelSync.kt:132` |
| Account: sign-out / device removed by the server | `onSignedOut()` | `panel/PanelSync.kt:283` |
| Server sync: sending | `syncPayload()`, your data goes to the panel | `panel/PanelSync.kt:202` |
| Server sync: finished | `onSyncFinished(result)` | `panel/PanelSync.kt:207` |
| VPN connect / disconnect | `onVpnStateChanged(state, profileName)` | `bg/BoxService.kt:123` |
| Dashboard ad card shown | `onAdShown(ad)` | `compose/screen/dashboard/AdCard.kt:78` |
| Dashboard ad card tapped | `onAdClicked(ad)` | `compose/screen/dashboard/AdCard.kt:86` |

All paths are under `app/src/main/java/io/nekohasekai/sfa/`.

## Sending data to the server
Return a `JSONObject` from `syncPayload()`. On every sync it goes to the panel as
`"extensions": {"<your id>": {…}}`. It's stored on that device, and the admin sees it on the client's page
under **Add-on data**. Limits: 4 KB per add-on and 16 add-ons. Anything bigger is dropped, never a
sync error. To see it working right away, debug builds include `SampleExtension` (id `sample.mock`), which sends
mock counters.

## Rules (the app enforces them)
- Callbacks run **one at a time on a background thread**, never on the main thread. Don't block
  for long. Use your own thread for heavy work.
- If your code throws, the app catches and logs it (`adb logcat -s Extensions`) and carries on.
- `syncPayload()` has **1 second**. If it's slower, that sync goes without your data.
- You only get the types in `ext/ExtensionModels.kt`. You never get VPN configs, server
  addresses, login codes or tokens.
- **Privacy:** this is a VPN app. Don't collect browsing data, DNS queries or IP addresses of
  sites. Ask the XeroBox team before collecting anything personal. The Play Store data-safety form
  has to list it.
- **Permissions:** your `.aar` manifest merges into the app's manifest. Every permission it
  adds must be agreed with the XeroBox team. We check the merged manifest before each release.
- **ProGuard / R8:** put your keep rules in your `.aar` (`consumer-proguard-rules.pro`). Release
  builds are minified.

## Need another hook?
Ask the XeroBox team. New callbacks are added as new methods with empty defaults, so existing add-ons
keep compiling and working.
