# Building PhoneWheel locally

The checked-in Android sources and Windows receiver can be rebuilt with `build.ps1` from this directory on Windows. The script expects a local toolchain under `build-tools/` with these exact paths:

- `jdk-17.0.20.1+1/` (JDK 17, including `javac`, `java`, `jar`, and `keytool`)
- `android-15/` (Android build tools including `aapt2.exe`, `d8.bat`, `zipalign.exe`, and `apksigner.bat`)
- `android-35/android.jar` (Android API platform jar)
- `venv/` (Python environment with PyInstaller and the `vgamepad` dependency)

`build.ps1` uses these local directory names directly. The toolchain is intentionally not included in this repository; provide compatible local files at those paths before building. The script produces `dist/PhoneWheel.apk` and `dist/PhoneWheelReceiver.exe` and runs the included test suites.

The checked-in APK is a development build. The build script creates a local development keystore when one is absent; do not use that keystore or its credentials to sign a release. Store any private release signing key outside the repository.
