# Fucking Stop

Stop Duolingo (and other apps) from changing their launcher icon. Fucking Stop is an Xposed module that does app icon switch blocking: the icon stays the default one.

Apps like Duolingo change their icon by enabling and disabling hidden alternative launcher entries. This module blocks those changes and puts the default icon back when the app starts. It keeps a log of every blocked attempt, which you can see by opening the app.

## Requirements

- A rooted phone with Magisk or KernelSU, and Zygisk enabled
- An Xposed framework. Tested with [Vector](https://github.com/JingMatrix/LSPosed) 2.2 (a fork of LSPosed).

## Install

1. Install the APK.
2. Enable the module in the Vector/LSPosed manager.
3. Add the apps whose icon should stay the same to the module's scope. Duolingo is suggested by default.
4. Restart those apps.

## Notes

- Apps in the scope can't change their icon at all, so their own "app icon" setting won't work either.
- Widgets are not affected.
- Tested on LineageOS 23.2 (Android 16) with Duolingo and DuckDuckGo.

## Build

```sh
ANDROID_HOME=~/Android/Sdk ./gradlew assembleRelease
```

Needs JDK 17+ and Android SDK platform 36. Release builds are signed with the key described in `~/.config/fucking-stop/keystore.properties`. If that file doesn't exist, the debug key is used.

## License

GPL-3.0
