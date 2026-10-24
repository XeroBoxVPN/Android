# Notices

**xerobox-core** is a modified version of **sing-box** (https://github.com/SagerNet/sing-box),
Copyright (C) 2022 by nekohasekai, licensed under GPL-3.0-or-later (see LICENSE). It is the VPN
engine of the XeroBox apps and is not affiliated with or endorsed by SagerNet.

Modifications (GPL-3.0 section 5a), first made in October 2026, recorded in this repository's Git
history from the commit "XeroBox core: ENC1 config codec, license hooks, encode command":

- `common/configcodec`, `experimental/libbox/codec.go`: the ENC1 config format (obfuscation of
  config files, not encryption), read by the core and the Android library.
- `common/licensing`: license-provider hooks.
- `cmd/sing-box`: `encode` and `license` commands; Windows resources for `xerobox-core.exe`.
- `daemon/instance.go`, `experimental/libbox/config.go`: load ENC1 configs.
- Upstream CI workflows removed (they need SagerNet's credentials).

Build the Android library (used by xerobox-android):

    go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.13
    go install github.com/sagernet/gomobile/cmd/gobind@v0.1.13
    ANDROID_NDK_HOME=<sdk>/ndk/28.0.13004108 go run ./cmd/internal/build_libbox -target android

This writes `libbox.aar` and `libbox-legacy.aar`.
