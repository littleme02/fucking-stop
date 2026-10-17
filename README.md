# Fucking Stop

An Xposed module that locks an app's launcher icon to its default.

Some apps change their own home-screen icon to get your attention. Duolingo, for example, swaps in a sad, crying or sick owl when your streak is at risk. Android has no API for redrawing an icon, so these apps ship many `activity-alias` entries (Duolingo 6.88 has about 186) and enable one while disabling the rest. Fucking Stop drops those calls, so the icon stays the one in the app's manifest.

## How it works

- For each app in the module's scope, it hooks `PackageManager.setComponentEnabledSetting` and `setComponentEnabledSettings` inside that app's process.
- Changes to the app's own launcher components (`MAIN`/`LAUNCHER`) are dropped. Other component changes go through, so the app keeps working.
- When the app starts, its launcher components go back to their manifest state. If the manifest enables no launcher by default, the module leaves the app alone instead of removing the icon.
- Every blocked attempt is recorded. Open Fucking Stop from your launcher or the LSPosed manager to see the log, with the icon each app tried to switch to.

## Install

1. You need Magisk (or KernelSU) with Zygisk, plus an Xposed framework. It was tested with [Vector](https://github.com/JingMatrix/LSPosed) (the maintained LSPosed fork) v2.2.
2. Install the APK, enable the module in the manager, and tick the apps to freeze in its scope. Duolingo is suggested by default. A reboot isn't needed; force-stop the app, or wait until it restarts.

With Vector's CLI:

```sh
su -c '/data/adb/modules/zygisk_vector/cli modules enable dev.natmon.fuckingstop'
su -c '/data/adb/modules/zygisk_vector/cli scope add dev.natmon.fuckingstop com.duolingo/0'
```

## Limitations

- An app in the scope can no longer change its icon at all, including through its own "App icon" setting (DuckDuckGo, Discord, Snapchat …). Leave such an app out of the scope if you want to use that setting.
- Home-screen widgets are a separate mechanism, and the module doesn't touch them.
- The hook runs in the app's own process. An app that calls the package manager's binder interface directly would get around it. None of the apps tested do this.

Tested on LineageOS 23.2 (Android 16) with Vector 2.2, Duolingo 6.88.2 and DuckDuckGo.

## Build

You need JDK 17 or newer and the Android SDK (platform 36). With [mise](https://mise.jdx.dev), `mise install` sets up the versions in `mise.toml`.

```sh
ANDROID_HOME=~/Android/Sdk ./gradlew assembleRelease
```

Release builds are signed with the key described in `~/.config/fucking-stop/keystore.properties` (or the file named by `$FUCKING_STOP_KEYSTORE`), and with the debug key if that file doesn't exist.

## License

GPL-3.0. See [LICENSE](LICENSE).
