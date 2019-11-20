# Ten Foot UI ExoPlayer Demo

This is a very simple player demo that show the use of ExoPlayer with remote key and intent only UI, it doesn't (yet) have a main activity.

## Building

To build and install it on your Android device (connect with adb first) then simply:

````
./gradlew demo-tenfoot:build
./gradlew demo-tenfoot:installDebug

````

## Using 
To launch it to play a single URL use: 

````
adb shell am start -n com.tivo.exoplayer.demo/.ViewActivity -a com.tivo.exoplayer.action.VIEW -d  "http://live1.nokia.tivo.com/ktvu/vxfmt=dp/playlist.m3u8?device_profile=hlsclr"
````

## More
KeyPad transport controls work for the Amino remote.  There are also numeric keypad events:

* Num 5 - launches the audio track selection dialog
* Num 6 - show the text caption selection dialog
* Num 0 - Launches ExoPlayer's `library-ui` track selection dialog

Other intents supported, show/hide stats for geeks overlay:

````
adb shell am start -n com.tivo.exoplayer.demo/.ViewActivity -a com.tivo.exoplayer.action.GEEK_STATS
````
##UI
To see more urls in the the drop down list update `video_urls` array in `arrays.xml`
or you can enter the url at runtime into the input field


