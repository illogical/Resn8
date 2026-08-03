# Antigravity Android Development Setup

This guide configures Antigravity agents with the Android command-line access needed to build, test, install, run, and diagnose Resn8 on Android 14 through Android 16.

## Verified local baseline

The following tools and targets were detected on August 2, 2026:

| Component | Detected state |
| --- | --- |
| Android SDK | `C:\Users\thewm\AppData\Local\Android\Sdk` |
| Android Studio JDK | `C:\Program Files\Android\Android Studio\jbr` (JDK 25.0.2) |
| Gradle wrapper | Gradle 9.5.0; launches successfully with the Android Studio JDK |
| Platform Tools | `adb` 37.0.1 |
| SDK Platform | Android SDK 37 installed |
| Build Tools | 36.0.0 (`aapt2`, `d8`, `apksigner`, and `zipalign`) |
| Physical device | TCL T807M, Android 16 / API 36, connected and authorized through wireless ADB |
| Virtual device | Pixel 9a, Android 15 / API 35, Google Play x86_64 image |
| Resn8 SDK configuration | `minSdk 34`, `targetSdk 37`, `compileSdk 37`, AGP 9.3.1 |

The SDK is usable through absolute paths, but the Antigravity agent environment does not currently expose `JAVA_HOME`, `ANDROID_HOME`, or the Android CLI directories through `PATH`. The Android SDK Command-Line Tools package is also absent, so `sdkmanager`, `avdmanager`, and `apkanalyzer` are not currently available.

## 1. Configure Windows environment variables

Open **Windows Settings**, search for **Edit environment variables for your account**, and open **Environment Variables**.

Create or update these user variables:

| Variable | Value |
| --- | --- |
| `JAVA_HOME` | `C:\Program Files\Android\Android Studio\jbr` |
| `ANDROID_HOME` | `C:\Users\thewm\AppData\Local\Android\Sdk` |

Edit the user `Path` and add these entries:

```text
%JAVA_HOME%\bin
%ANDROID_HOME%\platform-tools
%ANDROID_HOME%\emulator
%ANDROID_HOME%\cmdline-tools\latest\bin
%ANDROID_HOME%\build-tools\36.0.0
```

`ANDROID_SDK_ROOT` is deprecated. Prefer `ANDROID_HOME`. If an older tool requires both variables, set both to the same SDK directory. See the [official Android environment-variable reference](https://developer.android.com/tools/variables).

Close every Antigravity window and start Antigravity again so its agent and terminal processes inherit the new environment.

### Verify the environment

Open a fresh Antigravity terminal and run:

```powershell
$env:JAVA_HOME
$env:ANDROID_HOME
java -version
adb version
emulator -list-avds
```

Expected results:

- `JAVA_HOME` points to Android Studio's `jbr` directory.
- `ANDROID_HOME` points to the local Android SDK.
- `java` reports the Android Studio JDK rather than the legacy Oracle Java 8 shim.
- `adb` and `emulator` resolve without absolute paths.
- `Pixel_9a` appears in the AVD list.

## 2. Install the Android SDK Command-Line Tools

In Android Studio:

1. Open **Tools > SDK Manager**.
2. Select the **SDK Tools** tab.
3. Enable **Show Package Details** if needed.
4. Select **Android SDK Command-line Tools (latest)**.
5. Confirm that **Android SDK Platform-Tools**, **Android Emulator**, and **Android SDK Build-Tools** are also selected.
6. Choose **Apply**, accept the licenses, and wait for installation to finish.
7. Restart Antigravity.

Verify the newly installed tools:

```powershell
sdkmanager --version
sdkmanager --list_installed
avdmanager list avd
apkanalyzer --help
```

The Command-Line Tools package provides `sdkmanager` and `avdmanager`; see the [official sdkmanager documentation](https://developer.android.com/tools/sdkmanager).

## 3. Install an Android 14-16 test matrix

Resn8 supports API 34 and newer. Keep repeatable emulator targets for each supported Android release:

| Android release | API | Recommended target |
| --- | ---: | --- |
| Android 14 | 34 | Add an emulator |
| Android 15 | 35 | Existing `Pixel_9a` emulator |
| Android 16 | 36 | Add an emulator and retain the connected physical TCL device |

In Android Studio:

1. Open **Tools > SDK Manager > SDK Platforms**.
2. Enable **Show Package Details**.
3. Install the Android 14/API 34, Android 15/API 35, and Android 16/API 36 SDK platforms and sources.
4. Open **Tools > Device Manager**.
5. Create a Pixel-class AVD using an API 34 Google APIs or Google Play image.
6. Create another Pixel-class AVD using an API 36 Google APIs or Google Play image.
7. Keep the existing API 35 `Pixel_9a` AVD.
8. Boot each AVD once and finish its first-run initialization.

Use the physical API 36 device for behavior that emulators do not reproduce faithfully, especially:

- Storage Access Framework and removable/document-provider access
- Background Media3 playback and media notifications
- Lock-screen, headset, Bluetooth, and audio-focus controls
- Process death, power management, and real-device lifecycle behavior

List available and connected targets with:

```powershell
emulator -list-avds
adb devices -l
```

If an emulator is listed as `offline`, close the stale emulator process and cold-boot that AVD from Device Manager. Do not use **Wipe Data** unless discarding that AVD's installed apps and state is intentional.

## 4. Configure Antigravity's Windows shell

The managed shell currently resolves the WindowsApps `pwsh.exe` shim, which fails with Windows error 1920. Configure Antigravity to use a concrete PowerShell executable.

1. Open Antigravity **Settings**.
2. Search for **Terminal: Integrated Default Profile Windows**.
3. Select a working PowerShell profile whose executable is one of:

```text
C:\Program Files\PowerShell\7\pwsh.exe
C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe
```

4. Prefer the PowerShell 7 path when PowerShell 7 is installed and working; otherwise use the built-in Windows PowerShell path.
5. Open a new terminal and run `$PSVersionTable` and `Get-Command adb`.

If the profile is not listed, define an explicit terminal profile in Antigravity's JSON settings using the concrete executable path, then select it as the default Windows profile.

## 5. Configure Antigravity agent permissions

The SDK and JDK are outside the Resn8 workspace. In Antigravity's agent settings:

1. Allow the agent to access the exact Android SDK and Android Studio JDK locations when prompted.
2. Keep terminal commands in review mode, or add narrow allow rules for routine commands such as `gradlew.bat`, `adb`, and emulator inspection.
3. Retain manual review for device- or data-destructive operations, including app-data clearing, uninstalling, AVD data wipes, signing-key changes, and SDK package removal.

Antigravity restricts non-workspace access by default and supports configurable terminal execution policies; see the [official Antigravity agent settings](https://www.antigravity.google/docs/agent-settings).

## 6. Verify Resn8 end to end

From the repository root, run the required automated checks:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

Then verify device access:

```powershell
adb devices -l
adb -s <device-serial> shell getprop ro.build.version.release
adb -s <device-serial> shell getprop ro.build.version.sdk
```

Install the debug APK on a specifically selected device:

```powershell
adb -s <device-serial> install -r .\app\build\outputs\apk\debug\app-debug.apk
```

When more than one device or emulator is connected, always pass `-s <device-serial>` to avoid operating on the wrong target.

Useful diagnostic commands include:

```powershell
adb -s <device-serial> logcat
adb -s <device-serial> shell dumpsys media_session
adb -s <device-serial> shell dumpsys package com.app.resn8
```

After this setup, an Antigravity agent can build and lint Resn8, run JVM and instrumentation tests, manage emulators, install and launch APKs, inspect logs and system services, capture device evidence, and verify Android 14-16 behavior through the same underlying Android toolchain available to other IDE agents.
