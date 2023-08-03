### Purpose

This document describes how to use the `liveoffset_test.py` python script to test the Live Offset Sync feature.

### Setup

You will need:

1. The live offset test python script
2. ExoPlayer tenfoot demo from the `t-live-point-sync` branch
3. Mac or Linux box to run the script (windows is not supported)

You can either use a pre-built APK or checkout the branch and use `./gradlew demo-tenfoot:installDebug` to build and install the demo ExoPlayer on the attached devices.

The script is located in the ExoPlayer git repository in [tivo-testing/liveoffset_test.py](../tivo-testing/liveoffset_test.py).  To run the script you need:

* python3
* packages in standard python 3.11 release

### Running 

The script uses the output of `adb devices -l` to determine the set of devices to test, so you must connect to each device first.

Then simply run the script to start playback of a URL,  for example:

```bash
python3 tivo-testing/liveoffset_test.py --verbose INFO -o 30 -t 10 http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr
```

You can stop the script at any point with control-C, playback will continue.

To run the script to monitor existing playback, use the command without a URL, for example:

```bash
python3 tivo-testing/liveoffset_test.py --verbose INFO -o 30 -t 3
```

In this example the max offset allowed before logging a WARNING is 3 ms (`-t 3`), un-realisitc in practice but useful for showing the output.

### Script Output

#### Adjustment Phase

In the adjustment stage, the script list the devices and runs the `adb` commands to start playback of the URL, it will log `adjust` lines as it watches Live Offset adjustment converge the devices to the same offset,  example:

```bash
INFO:root:adjust 10.100.43.23:5555: 08-02 10:37:59.883   379   461 I LoggingLivePlaybackSpeedControl: intiating fast playback (1.125x) while liveOffset 40.17 > targetLiveOffset 38.93, idealTargetLiveOffset 30.00, buffer   1.2
```

One all devices have reached the Target Live Offset (the `-o 30` parameter) the script will start watching the actual offset.   The log lines like below:

```bash
INFO:root:device 10.100.43.23:5555 live offset ended, 08-02 10:39:36.349   379   461 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.02, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  13.4
```

Indicate the device has reached the target and stopped adjustment.  Once all devices are at the target the script goes to monitor stage, the following is logged:

```bash
INFO:root:all devices synced live position, entering Measurement Phase
```

#### Measurement Phase

In this stage, the script looks for a once a second log line from the player that has the syncronized `now()` time and the playback position.   Note these log lines may be collected at any time, so the algorithm normalizes the time to produce an offset of offsets.

Here is an example log like with two devices:

```bash
WARNING:root:08-03 11:56:59 - [device: 192.168.5.157:5555 pos: 11:56:29.16 off: 30.02 tar: 30.02] vs [device: 192.168.5.211:5555 pos: 11:56:29.184 off: 30.0 tar: 30.01]: live delta: 19 (delta_now: 149 delta_pos: 168)
```



The collected time was 11:56:59, at this time the .157 device was ~20ms behind the 30 second live target and the .184 device was behind ~10ms.   They are 19ms from each other (*live delta*).   The log lines compare one device to another (first device in the list is compared to each of the other devices), each device logs:

* **device** &mdash; the device ID from `adb devices -l`
* **pos** &mdash; playback position to the nearest second **AT THE TIME** the log was collected
* **off** &mdash; device's current offset from live, in seconds
* **pos** &mdash; live offset target, in seconds.  This should be the same for every device

The computed *live delta* is how much the playback positions on each pair of devices differs in milliseconds.  This is done by normalizing the playback position from each device to the time (synchronized via NTP) the playback position was collected via the following formula:

```java
live delta = abs(abs(device1.now - deviceN.now) - abs(device1.position - deviceN.position))
```

#### Sample Run

Here's an example run with an explanation of the logging, the run command is:

```bash
$ python3 tivo-testing/liveoffset_test.py --verbose INFO -o 30  http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr
```

This starts playback of KQED plus on the attached devices, the commands are logged info level:

```bash
INFO:root:adb devices -l
INFO:root:adb -s 192.168.5.157:5555 logcat -c
INFO:root:adb -s 192.168.5.157:5555 shell am force-stop com.tivo.exoplayer.demo
INFO:root:adb -s 192.168.5.157:5555 shell am start -n com.tivo.exoplayer.demo/.ViewActivity --ef live_offset 30.0 --ef fast_resync 20.0  -a com.tivo.exoplayer.action.VIEW http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr
INFO:root:adb -s 192.168.5.157:5555 shell pidof -s com.tivo.exoplayer.demo
INFO:root:adb -s 192.168.5.157:5555 shell pidof -s com.tivo.exoplayer.demo
INFO:root:adb -s 192.168.5.157:5555 logcat --pid 10899
INFO:root:adb -s 192.168.5.211:5555 logcat -c
INFO:root:adb -s 192.168.5.211:5555 shell am force-stop com.tivo.exoplayer.demo
INFO:root:adb -s 192.168.5.211:5555 shell am start -n com.tivo.exoplayer.demo/.ViewActivity --ef live_offset 30.0 --ef fast_resync 20.0  -a com.tivo.exoplayer.action.VIEW http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr
INFO:root:adb -s 192.168.5.211:5555 shell pidof -s com.tivo.exoplayer.demo
INFO:root:adb -s 192.168.5.211:5555 shell pidof -s com.tivo.exoplayer.demo
INFO:root:adb -s 192.168.5.211:5555 logcat --pid 18179
```

This section of logging output is the Adjustment Phase

```bash
INFO:root:all devices are not synced to live position, entering Adjustment Phase
INFO:root:device: 192.168.5.157:5555 log_line: 08-03 11:57:15.191 10899 10899 I ExoDemo : ExoPlayer Version: ExoPlayerLib/2.15.1-1.3, Build Number: local, Git Hash: e396bfd385
INFO:root:device: 192.168.5.157:5555 log_line: 08-03 11:57:16.911 10899 10899 D ExoDemo : playUri() playUri: 'http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr' - chunkless: false initialPos: -1 playWhenReady: true
INFO:root:adjust 192.168.5.157:5555: 08-03 11:57:17.817 10899 10945 I LoggingLivePlaybackSpeedControl: setLiveConfiguration() init idealTargetLiveOffsetUs: 30.00
INFO:root:device: 192.168.5.211:5555 log_line: 08-03 11:57:14.394 18179 18179 I ExoDemo : ExoPlayer Version: ExoPlayerLib/2.15.1-1.3, Build Number: local, Git Hash: e396bfd385
INFO:root:device: 192.168.5.211:5555 log_line: 08-03 11:57:15.446 18179 18179 D ExoDemo : playUri() playUri: 'http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr' - chunkless: false initialPos: -1 playWhenReady: true
INFO:root:adjust 192.168.5.157:5555: 08-03 11:57:19.989 10899 10945 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 30.00 to: 37.18 (delta 7.18), liveOffset: 38.23, playbackSpeed 1.105x, idealTargetLiveOffset 30.00, buffer   1.1
INFO:root:adjust 192.168.5.157:5555: 08-03 11:57:19.993 10899 10945 I LoggingLivePlaybackSpeedControl: intiating fast playback (1.105x) while liveOffset 38.23 > targetLiveOffset 37.18, idealTargetLiveOffset 30.00, buffer   1.1
INFO:root:adjust 192.168.5.211:5555: 08-03 11:57:16.401 18179 18245 I LoggingLivePlaybackSpeedControl: setLiveConfiguration() init idealTargetLiveOffsetUs: 30.00
INFO:root:adjust 192.168.5.211:5555: 08-03 11:57:17.918 18179 18245 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 30.00 to: 38.80 (delta 8.80), liveOffset: 40.32, playbackSpeed 1.152x, idealTargetLiveOffset 30.00, buffer   1.5
INFO:root:adjust 192.168.5.211:5555: 08-03 11:57:17.920 18179 18245 I LoggingLivePlaybackSpeedControl: intiating fast playback (1.152x) while liveOffset 40.32 > targetLiveOffset 38.80, idealTargetLiveOffset 30.00, buffer   1.5
INFO:root:adjust 192.168.5.157:5555: 08-03 11:57:53.182 10899 10945 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 37.18 to: 33.88 (delta -3.30), liveOffset: 36.17, playbackSpeed 1.200x, idealTargetLiveOffset 30.00, buffer  18.8
INFO:root:adjust 192.168.5.211:5555: 08-03 11:57:51.081 18179 18245 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 38.80 to: 35.42 (delta -3.39), liveOffset: 37.78, playbackSpeed 1.200x, idealTargetLiveOffset 30.00, buffer  24.3
INFO:root:adjust 192.168.5.157:5555: 08-03 11:58:01.220 10899 10945 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 33.88 to: 30.68 (delta -3.20), liveOffset: 34.59, playbackSpeed 1.200x, idealTargetLiveOffset 30.00, buffer  21.1
INFO:root:adjust 192.168.5.211:5555: 08-03 11:57:59.110 18179 18245 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 35.42 to: 32.22 (delta -3.20), liveOffset: 36.17, playbackSpeed 1.200x, idealTargetLiveOffset 30.00, buffer  20.7
INFO:root:adjust 192.168.5.157:5555: 08-03 11:58:45.434 10899 10945 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 30.68 to: 30.00 (delta -0.68), liveOffset: 30.01, playbackSpeed 1.000x, idealTargetLiveOffset 30.00, buffer  15.4
INFO:root:device 192.168.5.157:5555 live offset ended, 08-03 11:58:45.436 10899 10945 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.01, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  15.4
INFO:root:adjust 192.168.5.157:5555: 08-03 11:58:45.436 10899 10945 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.01, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  15.4
INFO:root:adjust 192.168.5.211:5555: 08-03 11:58:50.375 18179 18245 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 32.22 to: 30.00 (delta -2.22), liveOffset: 30.02, playbackSpeed 1.000x, idealTargetLiveOffset 30.00, buffer  18.3
INFO:root:device 192.168.5.211:5555 live offset ended, 08-03 11:58:50.375 18179 18245 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.02, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  18.3
INFO:root:adjust 192.168.5.211:5555: 08-03 11:58:50.375 18179 18245 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.02, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  18.3

```

Finally, a single log line is printed indictating the devices have all finished initial adjustment, and now Measurement Phase begins.   No info level logging is emitted during Measurement Phase:

```bash
INFO:root:all devices synced live position, entering Measurement Phase
```

Lastly, you can restart playback of a channel on one device to verify it re-syncs correctly, you can copy the command outputed from the script to start playback in order to do this:

```bash
adb -s 192.168.5.211:5555 shell am start -n com.tivo.exoplayer.demo/.ViewActivity --ef live_offset 30.0 --ef fast_resync 20.0  -a com.tivo.exoplayer.action.VIEW
```

This should output logging that we stop and restart playback then return to Adjustment Phase, example:

```bash
INFO:root:device: 192.168.5.211:5555 log_line: 08-03 12:20:01.981 19199 19199 D ExoDemo : Stopping playback with player in state: 3
INFO:root:device: 192.168.5.211:5555 log_line: 08-03 12:20:02.007 19199 19199 D ExoDemo : playUri() playUri: 'http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/playlist.m3u8?device_profile=hlsclr' - chunkless: false initialPos: -1 playWhenReady: true
INFO:root:device 192.168.5.211:5555 live offset started, 08-03 12:20:02.935 19199 19239 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 30.00 to: 35.35 (delta 5.35), liveOffset: 35.35, playbackSpeed 1.000x, idealTargetLiveOffset 30.00, buffer   1.2
INFO:root:adjust 192.168.5.211:5555: 08-03 12:20:02.935 19199 19239 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 30.00 to: 35.35 (delta 5.35), liveOffset: 35.35, playbackSpeed 1.000x, idealTargetLiveOffset 30.00, buffer   1.2
WARNING:root:device 192.168.5.211:5555 lost live sync, exiting Measurement Phase
INFO:root:all devices are not synced to live position, entering Adjustment Phase
```

After the device adjusts to the correct live edge, you'll see this logging:

```bash
INFO:root:all devices are not synced to live position, entering Adjustment Phase
INFO:root:adjust 192.168.5.211:5555: 08-03 12:20:24.017 19199 19239 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 35.35 to: 35.36 (delta 0.01), liveOffset: 35.45, playbackSpeed 1.009x, idealTargetLiveOffset 30.00, buffer  21.0
INFO:root:adjust 192.168.5.211:5555: 08-03 12:20:24.018 19199 19239 I LoggingLivePlaybackSpeedControl: intiating fast playback (1.009x) while liveOffset 35.45 > targetLiveOffset 35.36, idealTargetLiveOffset 30.00, buffer  21.0
INFO:root:adjust 192.168.5.211:5555: 08-03 12:20:34.061 19199 19239 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 35.36 to: 32.34 (delta -3.02), liveOffset: 34.49, playbackSpeed 1.200x, idealTargetLiveOffset 30.00, buffer  22.0
INFO:root:adjust 192.168.5.211:5555: 08-03 12:21:17.292 19199 19239 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 32.34 to: 30.00 (delta -2.34), liveOffset: 30.02, playbackSpeed 1.000x, idealTargetLiveOffset 30.00, buffer  16.4
INFO:root:device 192.168.5.211:5555 live offset ended, 08-03 12:21:17.292 19199 19239 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.02, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  16.4
INFO:root:adjust 192.168.5.211:5555: 08-03 12:21:17.292 19199 19239 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 30.02, targetLiveOffset 30.00, idealTargetLiveOffset 30.00, buffer  16.4
INFO:root:all devices synced live position, entering Measurement Phase
```

