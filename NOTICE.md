# Notices

## Origin

XeroBox for Android is a modified version of **sing-box for Android (SFA)**:
https://github.com/SagerNet/sing-box-for-android, Copyright (C) 2022 by nekohasekai
<contact-sagernet@sekai.icu>, licensed under GPL-3.0-or-later.

The VPN engine (`libbox`) is built from **`core/`** in this repository, a modified version of **sing-box**:
https://github.com/SagerNet/sing-box, Copyright (C) 2022 by nekohasekai, licensed under
GPL-3.0-or-later.

XeroBox is an independent project. It is not affiliated with, endorsed by or supported by
SagerNet or nekohasekai.

## Modifications (GPL-3.0 section 5a)

The XeroBox modifications were first made in October 2026 and are recorded in this repository's
Git history, starting at the commit "XeroBox: branding, accounts, sync, traffic guard, billing".
In summary:

- **Name, icons, colours and package name:** XeroBox branding, and its own package name
  (`com.xerobox.vpn`).
- **Panel integration:** sign-in with login or invitation codes; configuration sync with a
  XeroBox panel; invitations and referrals; rewarded ads; bug and crash reports.
- **Account features:** data-usage display and a traffic guard that stops the VPN when the
  account runs out; a plan card; purchase / upgrade codes checked by the panel; a QR code for the official download page.
- **Updates:** the update source moved to the XeroBox panel and this repository.
- **One build:** the upstream Play and Android 5–6 variants were removed.
- **Removed screens:** the remote-control, core-settings and privilege settings screens.

## Third-party components

The app also uses the libraries listed in `app/build.gradle.kts`, under their own licenses
(Apache-2.0 for the AndroidX and Kotlin libraries, among others).
