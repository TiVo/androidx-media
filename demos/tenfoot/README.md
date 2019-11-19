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

Start with an initial position, `start_at` (value is in milliseconds, 0 is start of the playlist or window)

````
adb shell am start -n com.tivo.exoplayer.demo/.ViewActivity -a com.tivo.exoplayer.action.VIEW  --ei start_at 1152000 -d  "'http://rr.vod.rcn.net:8080/rolling-buffer/wusahd__tp1-1008007-wusahd-216219168175125654-7614237-1573182000-157318560000000001/wusahd__tp1-1008007-wusahd-216219168175125654-7614237-1573182000-157318560000000001.m4m/transmux/index.m3u8?ccur_st=0&ccur_et=3600&ccur_svc_type=rec&eprefix=lts&source_channel_id=wusahd'"
````



