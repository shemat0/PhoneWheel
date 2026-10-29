param([switch]$AndroidOnly)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:JAVA_HOME = (Resolve-Path 'build-tools/jdk-17.0.20.1+1').Path
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$bt = (Resolve-Path 'build-tools/android-15').Path
$jar = 'build-tools/android-35/android.jar'
New-Item -ItemType Directory -Force build-tools/classes,build-tools/dex,build-tools/java-tests,dist | Out-Null
$pureSources = @(Get-ChildItem android/src/com/orchestr/phonewheel -Filter '*.java' | Where-Object Name -ne 'MainActivity.java' | ForEach-Object FullName)
$javaTests = @(Get-ChildItem tests -Filter '*.java' | ForEach-Object FullName)
& javac --release 8 -encoding UTF-8 -d build-tools/java-tests $pureSources $javaTests
if ($LASTEXITCODE) { throw 'Steering test compile failed' }
& java -cp build-tools/java-tests SteeringTest
if ($LASTEXITCODE) { throw 'Steering tests failed' }
& java -cp build-tools/java-tests SendTimingTest
if ($LASTEXITCODE) { throw 'SendTiming tests failed' }
& java -cp build-tools/java-tests AckTrackerTest
if ($LASTEXITCODE) { throw 'RTT matching tests failed' }
& java -cp build-tools/java-tests ControlModelTest
if ($LASTEXITCODE) { throw 'Layout and pointer tests failed' }
& java -cp build-tools/java-tests LinkHealthTest
if ($LASTEXITCODE) { throw 'Link freshness tests failed' }
& java -cp build-tools/java-tests AckLineReaderTest
if ($LASTEXITCODE) { throw 'ACK reader lifecycle tests failed' }
& java -cp build-tools/java-tests DiagramTapTest
if ($LASTEXITCODE) { throw 'Diagram tap tests failed' }
& java -cp build-tools/java-tests JoystickTest
if ($LASTEXITCODE) { throw 'Joystick mode tests failed' }

& "$bt/aapt2.exe" link -I $jar --manifest android/AndroidManifest.xml -o build-tools/base.apk
if ($LASTEXITCODE) { throw 'aapt2 failed' }
$sources = @(Get-ChildItem android/src/com/orchestr/phonewheel -Filter '*.java' | ForEach-Object FullName)
& javac --release 8 -encoding UTF-8 -classpath $jar -d build-tools/classes $sources
if ($LASTEXITCODE) { throw 'javac failed' }
$classes = @(Get-ChildItem build-tools/classes -Recurse -Filter '*.class' | ForEach-Object FullName)
& "$bt/d8.bat" --lib $jar --min-api 23 --output build-tools/dex $classes
if ($LASTEXITCODE) { throw 'd8 failed' }
Copy-Item build-tools/base.apk build-tools/unsigned.apk -Force
Push-Location build-tools/dex
& jar uf ../unsigned.apk classes.dex
Pop-Location
& "$bt/zipalign.exe" -f 4 build-tools/unsigned.apk build-tools/aligned.apk
if ($LASTEXITCODE) { throw 'zipalign failed' }
if (!(Test-Path build-tools/development.jks)) {
 & keytool -genkeypair -keystore build-tools/development.jks -storepass android -keypass android -alias phonewheel -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Phone Wheel Development'
 if ($LASTEXITCODE) { throw 'keytool failed' }
}
& "$bt/apksigner.bat" sign --ks build-tools/development.jks --ks-pass pass:android --key-pass pass:android --out dist/PhoneWheel.apk build-tools/aligned.apk
if ($LASTEXITCODE) { throw 'APK signing failed' }
& "$bt/apksigner.bat" verify --verbose dist/PhoneWheel.apk
if ($LASTEXITCODE) { throw 'APK verification failed' }
if (!$AndroidOnly) {
 $gamepadDll = (Resolve-Path 'build-tools/venv/Lib/site-packages/vgamepad/win/vigem/client/x64/ViGEmClient.dll').Path
 $licenseFolder = (Resolve-Path 'dist/licenses').Path
 & build-tools/venv/Scripts/python.exe -m PyInstaller --noconfirm --onefile --windowed --name PhoneWheelReceiver --distpath dist --workpath build-tools/pyinstaller --specpath build-tools --hidden-import vgamepad --hidden-import vgamepad.win.virtual_gamepad --hidden-import vgamepad.win.vigem_client --add-binary "$gamepadDll;vgamepad/win/vigem/client/x64" --add-data "$licenseFolder;licenses" receiver/receiver.py
 if ($LASTEXITCODE) { throw 'EXE build failed' }
}
& python -m unittest discover -s tests -v
if ($LASTEXITCODE) { throw 'Tests failed' }
