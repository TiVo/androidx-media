# ANDROID DEV AND INTELLIJ FOR TIVO DUMMIES
This step-by-step guide is for Tivo folks who are new to Android development. It shows
from scratch how to install an Android dev environment, build ExoPLayer project and fire
off the player on the Android device 
## 1) Create directory: ~/Android/Sdk
## 2) Install Android SDK tools
* Go to: https://developer.android.com/studio and download `sdk-tools-linux-xxx.zip`
  file (xxx is the latest revision)
* Unpack this file in `~/Android/Sdk`. This should create `tools` directory
* Set the following env vars:
```
$ export ANDROID_SDK_ROOT=/home/<user>/Android/Sdk
$ export ANDROID_HOME=$ANDROID_SDK_ROOT
$ export PATH=$ANDROID_HOME/tools:$ANDROID_HOME/tools/bin:$PATH
```
## 3) Install platform and build tools:
```
$ sdkmanager "platforms;android-28" "build-tools;28.0.3"
```
* Accept license transfer for all packages
 ```
$ yes | sdkmanager --licenses
```
* Install/update git
```
$ sudo apt-get install git-core
```
* Install/update gradle
Gradle is bootstrap installed by the Gradle Wrapper that is part of the ExoPlayer (and
many other) projects.  When you first run `./gradlew` it downloads the correct gradle
version for the project you are working locally for the project.

## 4) Install adb
The Android development tools are part of the sdk platform tools.  This includes adb,
fastboot, and others.

Update your path to include it, this is the path (for build-tools 28.0.3)
 `$ANDROID_SDK/platform-tools/`

* Turn on Android device and connect it over USB. Make sure developer options are turned on
* Check if adb sees the device:
```
$ adb devices
List of devices attached
18762280030474	device
```
## 5) Create a developer account on Github.com
* Follow the steps in: https://wiki.tivo.com/wiki/Github_Tool_Integration

## 6) Install IntelliJ Ultimate edition
* Go to: https://www.jetbrains.com/idea/download/#section=linux and get
  the `ideaIU-20xx.x.x.tar.gz` file.
* Unpack the file in a convenient location (home dir is good)
* Go to: `~/idea-IU-xxx.xxx.xx/bin` and execute `./idea.sh`
* First time, the IntelliJ will go over configs/prefs. Make sure that Android, Gradle and
  Git plugins are enabled. Also, it may ask for the IDEA license. Tivo developers use this
  URL: http://sjc1engjbls01:80

## 7) Get the project from repository
* From main menu select *Get From Version Control*
* In this example I used: https://github.com/tivocorp/exoplayerprvt
* Make sure you filled *Github* user name and password
* If 2FA was enabled (recommended by Tivo), IntelliJ will also ask for SMS code
* At this point IntelliJ will start loading the project.

## 8) Build and run
* Use target `demo-tenfoot` which builds ready-to-play HLS player
### SW versions used in this example
```
- Linux 4.15.0-74-generic amd64 #83~16.04.1-Ubuntu
- sdk-tools-linux-4333796
- git version 2.7.4
- gradle 2.10
- JVM: 1.8.0_242 (Private Build 25.242-b08)
- Android Debug Bridge version 1.0.32
- idea-IU-193.6015.39
```
