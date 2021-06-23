# Field Triage of Player Issues #

[TOC]


## Summary ##

This guide is intended to support the TiVo PSO team efforts to diagnose video playback issues with the ExoPlayer player in the TiVo managed streamer application.

In this guide we will explore each of the following areas:

* Log Analysis
* Playback Issues
* ErrorCodes and Messages

The reader is assumed to:

1. understand how to connect to the streamer device with ADB and recover log files with bugreport and/or logcat.  
2. have a good grasp of HLS playback, as defined in the [Pantos Spec](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04)

Start with the [Log Analysis](#log-analysis) section to broadly understand ExoPlayer logging, for specific error codes you can skip to the [ErrorCodes and Messages](#errorcodes-and-messages) section or refer to the section on [Playback Issues](#playback-issues) for issues that are not reported specifically as an error

<div id="log-analysis"/>

## Log Analysis ##

ExoPlayer logs playback state using the [EventLogger](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/util/EventLogger.html), you can refer to this java doc for specifics on all the events. The TiVo version of this class modifies it to include additional logging.  For MR releases of Hydra the default logging level for ExoPlayer is set to INFO, the only EventLogger events logged at INFO or higher are `loadFailed` and [Playback State](#playback-state)

### Enabling Logging ###

As stated above the default logging level for Hydra MR releases is INFO, so much of the EventLogger events are turned off.  

In order to turn these on you need to push a marker file to the streamer box with `ADB` to override the logging level to ALL.  The file is located in the App's private external storage area `/sdcard/Android/data/<package-id>/files`  For example, for the Hydra app that is

```
/sdcard/Android/data/com.tivo.hydra.app/files
```

To set logging level to all, connect to the device shell with `adb shell` and use:

```
mkdir -p /sdcard/Android/data/com.tivo.hydra.app/files
cat > exo.properties
debugLevel=0
^D
```

The level values are:

```java
  /** Log level to log all messages. */
  public static final int LOG_LEVEL_ALL = 0;
  /** Log level to only log informative, warning and error messages. */
  public static final int LOG_LEVEL_INFO = 1;
  /** Log level to only log warning and error messages. */
  public static final int LOG_LEVEL_WARNING = 2;
  /** Log level to only log error messages. */
  public static final int LOG_LEVEL_ERROR = 3;
  /** Log level to disable all logging. */
  public static final int LOG_LEVEL_OFF = Integer.MAX_VALUE;
```


### General Format ###

````
... loadStarted [eventTime=664.63, mediaPos=1776.94, buffered=17.74, window=0, period=0, ...]
````

Time, position and buffered valuesare in seconds.

* `eventTime` is the time since playback began.
* `mediaPos` is the current playback position (see [Timeline](#timeline) section)
* `buffered` is the number of seconds of media currently buffered

The *\<additional description\>* text is detailed under each specific log entry.

### Specific Log Entires ###

<div id="timeline" />

#### Timeline ####

The timeline event reports the playlist updates, the playlist defines the seek boundary.  There are three types of timelines, Live, Event and VOD.

An example VOD timeline event:

````
07-20 11:56:04.493 25219 25219 D EventLogger: timeline [eventTime=9222.35, mediaPos=0.00, buffered=0.0, window=0, periodCount=1, windowCount=1, reason=PREPARED
07-20 11:56:04.493 25219 25219 D EventLogger:   period [1807.81]
07-20 11:56:04.493 25219 25219 D EventLogger:   window [1807.81, true, false]
07-20 11:56:04.493 25219 25219 D EventLogger: ]
````

The `reason=PREPARED` indicate this is the initial timeline (for VOD this will be the only event).  The `window [1807.81, true, false]` indicates the playlist is 1807.81 seconds long and not dynamic.

Live and Event timeline events are more interesting, these must occur no less frequently then the playlist update interval (specified in the `EXT-X-TARGETDURATION` header in the playlist).  These events should happen every target duration, if not you will see error [V552](#v552).

Update here will have `reason=DYNAMIC`, an example timeline update for live:

````
07-20 12:08:28.315 25219 25219 D EventLogger: timeline [eventTime=9966.17, mediaPos=569.35, buffered=13.65, window=0, period=0, periodCount=1, windowCount=1, reason=DYNAMIC
07-20 12:08:28.315 25219 25219 D EventLogger:   period [?]
07-20 12:08:28.315 25219 25219 D EventLogger:   window [594.59, true, true]
07-20 12:08:28.315 25219 25219 D EventLogger: ]
````

For live the playback position (`mediaPos`) should remain near the edge of the window.  In the example above, it is 25.24 seconds behind live.  
````
 594.59 - 569.35 = 25.24
````

The player starts 3 segments from the window edge for live (for 6 second segments, that is 18 seconds).  The player will drift closer or further from the live edge depending on how regular the origin server updates the playlist and how fresh the edge cached copy of the playlist is.

<div id="playback-state"/>

#### Playback State ####

Each time playback state changes this event is logged

````
EventLogger: state [eventTime=10729.90, mediaPos=571.45, buffered=13.65, window=0, period=0, true, READY]
````

The *\<additional description\>* text show the play/pause state (true is playing, false is paused) and the playback state ("READY" in the example).  Playback states are detailed in [Player state](https://exoplayer.dev/doc/reference-v1/com/google/android/exoplayer/ExoPlayer.html#State).  During playback it will be either "READY" (playing) or "BUFFERING" (waiting for segments to load).

<div id="segment-and-playlist-loading"/>

#### Segment and Playlist Loading ####

ExoPlayer logs commencement and completion events for loading playlists and segments.

The start of the segment load operation is logged by the *loadStarted* event, an example is:

````
EventLogger: loadStarted [..., uri: http://edge2.md.vod.rcn.net/ccur-session/02_353693272/LINEAR/rolling-buffer/cnnhd/cnnhd/transmux/CCURStream_cnnhd0-10_265736767.tsa]
````

The specific *\<additional description\>* simply contains the uri, allowing you to match up to the completion event.

Here is an example segment load completion:

````
06-23 10:43:52.109 18336 18336 D EventLogger: loadCompletedMedia [eventTime=6.50, mediaPos=3579.90, buffered=1.68, window=0, period=0, trackId: frumos.tivo.com-multi-audio:eng-Surround load-duration: 1493ms codecs: ac-3 start(dur): 3579576/6006 uri: http://frumos.tivo.com/ccur-session/01_3669909912/rolling-buffer/media/kgo/kgo/transmux/CCURStream_MultiPortMulticast1-10_T1624470195351822~D6006000.tsa]

````
The specific *\<additional description\>* text is:

* *trackId* &mdash; the playlist the segment is loaded from
* *load-duration* &mdash; measured time to download the segment
* *start(dur)* &mdash; the starting timestamp (in milliseconds) duration of the segment (example is 6.006 seconds)
* *uri* &mdash; the segment URI 

During Visual Trick Play, segments are i-Frame only segments

````
06-23 10:58:15.883 18336 18336 D EventLogger: loadCompletedMedia [eventTime=870.28, mediaPos=3299.41, buffered=174.06, window=0, period=0, trackId: iFrame-0 load-duration: 48ms codecs: avc1.640020 start(dur): 4344468/14014 offset/len: 1682600/29892 uri: http://frumos.tivo.com/ccur-session/01_3669909912/rolling-buffer/media/kgo/kgo/transmux/CCURStream_video_MultiPortMulticast2_1624470900-CCUR_iframe.tsv]
````

For the i-Frame only segment loads, the byte offset is included

* *offset/len* &mdash; the byte-offset and length of the i-Frame data in the segment.

Of particular interest when diagnosing stalls is the load duration, this must be substantially less the duration of the segment or stalls will almost certainly occur

And a playlist load example completion:

````
06-23 10:58:15.883 18336 18336 D EventLogger: loadCompletedMedia [eventTime=870.28, mediaPos=3299.41, buffered=174.06, window=0, period=0, trackId: iFrame-0 load-duration: 48ms codecs: avc1.640020 start(dur): 4344468/14014 offset/len: 1682600/29892 uri: http://frumos.tivo.com/ccur-session/01_3669909912/rolling-buffer/media/kgo/kgo/transmux/CCURStream_video_MultiPortMulticast2_1624470900-CCUR_iframe.tsv]
````

Live playlist loads should trigger a [Timeline](#timeline) update event, if not the origin/transcoder is not producing new segments quickly enough which can result in stalls.

<div id="load-error"/>

#### Load Error Logging ####

The `EventLogger` logs all load errors at ERROR logging level, both fatal and non-fatal.  This is a multi-line log that includes the exception traceback with the error that caused the load to fail.  An example for a 404 error on an audio segment load is:

````
11-24 13:30:09.898  9690  9690 E EventLogger: internalError [eventTime=237.33, mediaPos=208.30, buffered=49.99, window=0, period=0, loadError - URL: http://stevemasmacbook.attlocal.net/video/SingleProfile/AUDIO_eng-Surround_def_YES/CCURStream_CMTHD0-10_T1596069935460000~D6006000.tsa
11-24 13:30:09.898  9690  9690 E EventLogger:   com.google.android.exoplayer2.upstream.HttpDataSource$InvalidResponseCodeException: Response code: 404
11-24 13:30:09.898  9690  9690 E EventLogger:       at com.google.android.exoplayer2.upstream.DefaultHttpDataSource.open(DefaultHttpDataSource.java:300)
````

The `InvalidResponseCodeException` is the error for all non-200 HTTP status returns.  Note the buffered level here is > 0 so this error will be re-tried by ExoPlayer until there is no buffer left (playback has stalled).   ExoPlayer (unlike VisualOn) does not skip segments that fail to load, if continued retries still fail to load a segment playback will stop, turning the load error into a fatal error, once the player has played out all of the buffered data.

The retry does a backoff delay based on the count of retries, the algorithm is:

````java
delayMs = Math.min((errorCount - 1) * 1000, 5000);
````

So sequence is 0s, 1s, 2s, ..., 5s, 5s

It is possible for ExoPlayer to continue retrying when it is in the buffering state depending on the setting for [getMinimumLoadableRetryCount()](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/upstream/LoadErrorHandlingPolicy.html#getMinimumLoadableRetryCount-int-), this value is 3 by default, at least 3 retries will be done for a request.  Assuming the error occurs in 1 second from making the request that sequence would take 6 seconds:

````Java 
 firstRequest = 1000                    // 1000
 secondRequest = (2 - 1) * 1000 + 1000  // 2000
 secondRequest = (3 - 1) * 1000 + 1000  // 3000
````

<div id="level-shift-logging"/>

#### Level Shift Logging ####

ExoPlayer makes level change decisions each time it starts before it starts to load a new video segment.  There are two log messages that indicate the level has changed.

1. *initialLoadingFormat* &mdash; Shows the starting level
1. *loadingFormatChanged* &mdash; Indicates a level shift has occurred and a new Format (variant) segment load is starting
1. *videoFormatChanged* &mdash; Indicates samples with a new format have commenced playback (delay from loadingFormatChanged is because of buffering)

Here is a sample level shift (loading format change)

````
06-23 10:58:18.622 18336 18336 D EventLogger: loadingFormatChanged [eventTime=873.02, mediaPos=3376.54, buffered=221.05, window=0, period=0, Old: iFrame_7 - 1280x720@428571, New: 4 - 1280x720@5512160]

````

This is the initial format:

````
06-23 11:21:22.658 22685 22685 D EventLogger: initialLoadingFormat [eventTime=15.85, mediaPos=3465.46, buffered=0.00, window=0, period=0, 4 - 1280x720@5512160]
````

The *\<additional description\>* text shows the current buffering level, and the old and new variant (spacial resolution @ bit-rate (bits per second)).  The resolution and bit-rate are from the HLS playlist.

Note ExoPlayer will not level shift up (increasing bit-rate) unless there is at least 15 seconds buffered, and it will not level shift down unless there is less than 25 seconds buffered.  For VOD playback, ExoPlayer loads until it reaches a goal of 50 seconds of buffering.  For live (and EVENT live) it will buffer everything from the live point (assuming it is less then 50 seconds)

Here is an example log message for the playing format change:

````
EventLogger: videoFormatChanged - Old: 2 - 720x480@2570336 New: 5 - 1280x720@11380768 [eventTime=17.80, mediaPos=582.19, window=0, period=0]
````

This simply always follows the loading format change.

<div id="bandwidth-estimate-logging"/>

#### Bandwidth Estimate Logging ####

The bandwidth estimation algorithm uses a weighted moving average (average is actually the median of the sample data set) of samples to measure the available bandwidth.  The samples are weighted by size (so video has more effect then audio for example) and time decayed (newer take precedence over older).  

An example log messages is shown below:

````
EventLogger: bandwidthEstimate [10390.22, 1777.39, window=0, period=0, Received BW Estimate.  Loaded Bytes: 107728, sample: 4.683826(Mbps), estimate: 4.399772(Mbps)]
````

The bandwidth estimate log messages *\<additional description>* text contains:

* *Loaded Bytes* &mdash; the size of the sample, this affects the weighting
* *sample* &mdash; measured bandwidth for the sample (in megabits per-second)
* *estimate* &mdash; the updated bandwidth estimate after adding the sample to the sample data set.  This is the number ExoPlayer uses for level selection.

<div id="track-selection-logging"/>

#### Track Selection Logging ####

Track selection logging shows the current set tracks that are available from the playlist and which are enabled.

The tracks are grouped by renderer (Video, Audio, Text, Id3 Metadata).  Track selections occur when:

1. Initial track selection on playback startup
2. User changes audio or text tracks
3. Visual Trick Play starts or ends, VTP disables all non video tracks while active.

Tracks marked with an `[X]` are selected, if the track set is adaptive_supported then the level shifting algorithm selects from the enabled tracks.

````
07-28 07:36:45.311  8410  8410 D EventLogger: tracks [eventTime=1190.45, mediaPos=18.03, window=0, period=0
07-28 07:36:45.311  8410  8410 D EventLogger:   Renderer:0 [
07-28 07:36:45.311  8410  8410 D EventLogger:     Group:0, adaptive_supported=YES [
07-28 07:36:45.311  8410  8410 D EventLogger:       [X] Track:0, id=0, mimeType=video/avc, bitrate=860000, codecs=avc1.4d401e, res=480x360, supported=YES
07-28 07:36:45.311  8410  8410 D EventLogger:       [X] Track:1, id=1, mimeType=video/avc, bitrate=1400000, codecs=avc1.4d401f, res=640x480, supported=YES
07-28 07:36:45.311  8410  8410 D EventLogger:       [ ] Track:2, id=iFrame-0, mimeType=video/avc, bitrate=43167, codecs=avc1.4d401e, res=480x360, supported=YES
07-28 07:36:45.311  8410  8410 D EventLogger:     ]
07-28 07:36:45.311  8410  8410 D EventLogger:     Metadata [
07-28 07:36:45.311  8410  8410 D EventLogger:       HlsTrackMetadataEntry
07-28 07:36:45.311  8410  8410 D EventLogger:     ]
07-28 07:36:45.311  8410  8410 D EventLogger:   ]
07-28 07:36:45.311  8410  8410 D EventLogger:   Renderer:1 [
07-28 07:36:45.311  8410  8410 D EventLogger:     Group:0, adaptive_supported=N/A [
07-28 07:36:45.312  8410  8410 D EventLogger:       [X] Track:0, id=audio-aac:Undetermined, mimeType=audio/mp4a-latm, codecs=mp4a.40.2, channels=2, sample_rate=48000, language=un, label=Undetermined, supported=YES
07-28 07:36:45.312  8410  8410 D EventLogger:     ]
07-28 07:36:45.312  8410  8410 D EventLogger:     Metadata [
07-28 07:36:45.312  8410  8410 D EventLogger:       HlsTrackMetadataEntry [audio-aac, Undetermined]
07-28 07:36:45.312  8410  8410 D EventLogger:     ]
07-28 07:36:45.312  8410  8410 D EventLogger:   ]
07-28 07:36:45.312  8410  8410 D EventLogger:   Renderer:2 [
07-28 07:36:45.312  8410  8410 D EventLogger:     Group:0, adaptive_supported=N/A [
07-28 07:36:45.312  8410  8410 D EventLogger:       [X] Track:0, id=1/8219, mimeType=application/cea-608, supported=YES
07-28 07:36:45.312  8410  8410 D EventLogger:     ]
07-28 07:36:45.312  8410  8410 D EventLogger:   ]
07-28 07:36:45.312  8410  8410 D EventLogger:   Renderer:3 [
07-28 07:36:45.312  8410  8410 D EventLogger:     Group:0, adaptive_supported=N/A [
07-28 07:36:45.312  8410  8410 D EventLogger:       [X] Track:0, id=null, mimeType=application/id3, supported=YES
07-28 07:36:45.312  8410  8410 D EventLogger:     ]
07-28 07:36:45.312  8410  8410 D EventLogger:     Group:1, adaptive_supported=N/A [
07-28 07:36:45.312  8410  8410 D EventLogger:       [ ] Track:0, id=null, mimeType=application/id3, supported=YES
07-28 07:36:45.312  8410  8410 D EventLogger:     ]
07-28 07:36:45.312  8410  8410 D EventLogger:   ]
07-28 07:36:45.312  8410  8410 D EventLogger: ]
````


<div id="playback-issues"/>

## Playback Issues ##

Issues with playback are grouped into the following categories:

1. *Pre-flight Fail* &mdash; Failure to start playback 
2. *Freezes and Stalls* &mdash; Playback stops, with or without a loading spinner
3. *Video Quality* &mdash; Frequent level shifts, A/V sync issues
4. *Visual Trick-Play* &mdash; Checking for enablement, performance

### Pre-Flight Fails ###
Pre-Flight is from the channel change or playback request until the first frame of video shows.  A pre-flight failure is failure to render the first frame of video.  Pre-flight issues are most always one of these categories:

1. Provisioning
2. Network 
3. Origin Server

See the [ErrorCodes and Messages](#errorcodes-and-messages) section, all Pre-Flight fails should eventually end up with an error message, if not then treat them just like [Freezes And Stalls](#freezes-and-stalls).

<div id="freezes-and-stalls" />

### Freezes And Stalls ###

ExoPlayer playback depends on:

1. Continuous stream of sufficient (arriving in time to match playback speed) video and audio samples
2. Consistent and contiguous timestamps 

Playback will stop or stutter if either of these conditions are not met.  To start, gather all the relevant data:

1. The transitions of playback state (see the [Playback State](#playback-state)]
2. Most recent bandwidth measurements (see the [Bandwidth Estimate Logging](#bandwidth-estimate-logging) section)
3. Segment and Playlist load completions (see the [Segment and Playlist Loading](#segment-and-playlist-loading) section)
4. Timeline updates (see the [Timeline](#timeline) section)

These conditions taken together:

* playback state is READY
* segment fetching is still happening, or
* segment fetching has stopped but the playlist is still updating or there is already 50 seconds of playback buffer

Indicate that a discontinuity or timestamp issues is the cause.  The only way really to be sure is to capture the section of rolling buffer (or the VOD asset) that caused the stall.  You can use the [m3u8_download](https://github.com/tivocorp/m3u8_utils) utility to do this. As an alternative, the [Stream Validator](https://github.com/tivocorp/hls-stream-validator) can be used.

Otherwise look to the sections either on [Buffering Issues](#buffering-issues) or [Live Playlist Stalls](#live-playlist-stalls) for the root cause.


<div id="live-playlist-stalls" />

##### Live Playlist Stalls #####

During live playback timely updates to the current active playlist are essential to maintaining an adequate media buffer.  First look at the [Timeline](#timeline) logging section.  The timeline events must happen at regular intervals with a second of the segment duration.   Each second the timeline update is late is a second of lost buffer.

These two conditions together indicate the origin / transcoder has stopped producing live content.

* playlist load completions (see [Segment and Playlist Loading](#segment-and-playlist-loading)) are occurring regularly
* timeline update events are not occurring

Periodic delays in updates can cause stalls, a persistent delay will eventually cause error [V552](#v552)


<div id="buffering-issues"/>

##### Buffering Issues #####

Quite simply, playback stalls when there are no buffered samples.  This occurs for one of two reasons:

1. One or more segments (audio or video) have taken longer to load then their duration
2. For live or live Event, the playlist is not updating quickly enough (thus delaying segment fetching)

Look in the logs at the load durations for segments ([Segment and Playlist Loading](#segment-and-playlist-loading) section) and frequency of playlist updates ([Timeline](#timeline) event logging section)

This is common on playback startup (when, of course, there is no buffer to start with) but if it occurs frequently during playback it is an indication that either:

1. The client's network is slow or has serious wireless issues
1. The origin server or CDN (Edge) has load issues
1. There is congestion on a long delay WAN path to the client

These are in order of most likely to least likely to occur, the third case is only really possible if the MSO covers to wide a geography or has chosen connectivity partners poorly.

<div id="timestamp-issues" />

##### Timestamp Issues #####

ExoPlayer synchronizes the active media streams; video and text/captions if enabled to the audio stream. This synchronization is based on timestamps in the media segments.

Either:

1. The timestamps are monotonically increasing and in step in all the media streams
1. or, the HLS playlist must be marked with a EXT-X-DISCONTINUITY tag before the timestamps

Audio plays continuously (while not buffering or paused) at the sample rate specified in the encoding, and Android reports the current audio playback time to ExoPlayer, and ExoPlayer matches this to video frames (and text if enabled) timestamps to determine the frame to show.

<div id="discontinuity-issues" />

##### Discontinuity Issues #####

Un-reported timestamp discontinuities are extremely likely to cause playback freezes.  For example, consider the following sequence of events:

1. An audio sample jumps 50 or more seconds into the future
2. The audio codec plays this and reports the timestamp 
3. ExoPlayer waits for the matching video segment, but because the buffering limit is 50 seconds it will never load it.


### Video Quality ###

Video Quality issues fall into theses groups:

1. Poor picture quality
2. Stuttering or A/V Sync
3. Macroblocking or tiling

For picture quality issues, check the [Level Shift Logging](#level-shift-logging) section for how to determine the variant that is playing, if this is not the highest level variant then there are bandwidth issues, check the [Bandwidth Estimate Logging](#bandwidth-estimate-logging) section) against the expected bandwidth.  Otherwise, the issue is likely with the transcoding.

Stuttering or A/V Sync issues are almost always have [Timestamp Issues](#timestamp-issues) as the root cause.

Macroblocking is a video source issue, their is nothing the player can do to correct this.


### Visual Trick-Play ###

#### Enablement ####

Visual Trick-Play (VTP) is supported by ExoPlayer, for Hydra there is an APK customization switch to determine if it is actually enabled or not.

Two things must be true for VTP on a platform:

1. The MSO's APK must be customized to enable VTP
2. The origin server must produce an i-Frame only track.

Look at the [Track Selection Logging](#track-selection-logging) for the presence or absence of the i-Frame only track to determine if the origin support VTP.

To determine if the APK is *specialized* for the MSO to enable VTP, check the configuration in the Perforce, the [canned_specialization](https://p4web.tivo.com:1666/@md=d&cd=//d-flash/hydra/streamer-1-7/hydra/app/hydra/cmd/&cdf=//d-flash/hydra/streamer-1-7/hydra/app/hydra/cmd/canned_specializations&c=1fC@//d-flash/hydra/streamer-1-7/hydra/app/hydra/cmd/canned_specializations?ac=22) file must define the resource `USE_VISUAL_TRICKPLAY`

Alternately, if you only have the APK, look in the APK `res/values/strings.xml` for the string `USE_VISUAL_TRICKPLAY` this must be present and true.

#### Playback ####

During VTP playback you should see i-Frame only segment loads (see [Segment and Playlist Loading](#segment-and-playlist-loading)) in both forward and reverse mode.  

In forward mode the player attempts to display frames at a target frame rate of approximately 7 - 10 frames per second to support this the client should be able to download i-Frames with a load duration of under 100ms.  If this is not achieved playback will not reach the speed requested (because of network stalls are delays to produce the content), the Hydra UI does not provide any visual feedback of this buffering.

For reverse mode, the player is unable to take advantage of any buffering of segments, so while time movement will proceed at the requested rate, fewer (if any) frames will be displayed if the load duration of the i-Frame segments is > 100ms.

#### Optimizing Origin Configuration ####

Apple's [HLS Authoring Spec](https://developer.apple.com/documentation/http_live_streaming/hls_authoring_specification_for_apple_devices) Section 6 Trick Play contains information on this.  What follows is discussion on each of the sections with focus on the constraints of the current supported origin servers.

> 6.6. You SHOULD provide multiple I-frame Media Playlists with different bit rates.

The current supported origin servers (Vecima / Velocix) do not support multiple i-Frame only playlists so this is not possible.  As such it is critical to pick a resolution and compression level that is compatible with the network capabilities to the clients.

> 6.3. Alternatively, you MAY use the I-frames from your normal content, but trick play performance is improved with a higher density of I-frames.

This is true to a point, Apple optimizes the spec to Apple TV (where there is only a single fast forward rate that is 7x).  For the speeds TiVo supports 2-3 second GOP interval is as small as you want, longer inter-iframe durations may be required if network performance will not support.  Again, with only one i-Frame only playlist possible, this choice will be a tradeoff.

> 6.4. If you provide multiple bit rates at the same spatial resolution for your regular video, you SHOULD create the I-frame playlist for that resolution from the same source used for the lowest bit rate in that group.

This is a MUST for TiVo trick-play.  There is no benefit to higher bit rate i-Frames, in fact if the desired performance cannot be achieved with an HD (720 or higher) variant it is better to choose a lower resolution

> 6.5. The bit rate of I-frame playlists SHOULD be the bit rate of a normal playlist of the same resolution times the fps of the I-frame playlist divided by eight. (See I-Frame Bit Rates Versus Normal Bit Rates.)

This allows the player to confidently switch to the I-Frame playlist from the same spacial resolution normal playlist it is currently playing without risk of exceeded the measured bandwidth (the 1/8 is based on the Apple trick play rate, so for TiVo it should be even less!)  

<div id="errorcodes-and-messages"/>

## ErrorCodes and Messages ##

In this section we will explore select UI error overlays from the [TiVo Experience 4 Error Codes and Messages](https://confluence.tivo.com/display/PSRR/Mira+4.10?preview=%2F160300940%2F160300941%2FTiVoExperience4_Error_Codes_and_Messages_ALL_27FEB2020+%281%29.pdf) that playback errors and describe how to determine the root cause by examining provisioning and logs.

<div id="V549" />

### V549 - Download Error ###

This error is reported pre-flight if:

1. The master playlist fails to load.  
1. There is an issue downloading a key URL

This error is reported in-flight if:

2. Segment download fails after a number of retries

For the first case, look in the logs for the specific HTTP response code, body of the failed response and URI.  For example, here is a 404 Error log:

````
06-22 17:25:42.710  4147  4147 E ExoPlayerPlayer: HTTP error is due to invalid response code: 404
06-22 17:25:42.710  4147  4147 E ExoPlayerPlayer: Response message: Not Found
...
06-22 17:25:42.713  4147  4147 E ExoPlayerPlayer: For uri [http://live1.nokia.tivo.com/movies_e/vxfmt=dp/playlist.m3u8?device_profile=hlsvmx]
````
Here it is likely the origin server has issues or with TiVo service, the channel map is incorrect.

You can validate the URL manually with curl to make sure the host connects and a playlist is returned.

For the second case, you will see logs like this:

````
HttpDataSource$HttpDataSourceException: Unable to connect to https://acsm.telus.uat.verspective.net/CAB/keyfile?s=816&r=232008200017&t=DTV&p=1587745590&kc=fda0b18a85c211ea85bc1244edf4b922&kd=69254a9c25f83e996c095665c292784b
04-24 11:44:25.147  4050  4050 E EventLogger: Caused by: javax.net.ssl.SSLHandshakeException: Unacceptable certificate: EMAILADDRESS=SUBCA.dltcodavpipelineops@telus.com, CN=SUBCA.verimatrix.com, OU=VCAS, O=TELUS, ST=AB, C=CA
````
This indicates a VCAS encrypted channel was not correctly marked in the 

<div id="v551" />

### V551 - Parser Failed ###

ExoPlayer reports this error when the player throws a `ParserException.`  This error is always the result of a failure in the origin server and/or the stream.

The origin server produces and ExoPlayer parses:

1. Playlists (HLS / DASH)
1. Packaged media streams (fMP4 and mp2ts)

Issues in the format of any of these will cause ExoPlayer to stop playback and report this error.

To find the root cause, look for the actual `ParserException` in the log file.  For example, the error:
  
>  *Cannot find sync byte. Most likely not a Transport Stream.*

Indicates the segment is corrupted, this is either the result of an incorrect decryption key or initialization vector or the origin server truncated the segment.

The error:
>  *Failed to parse the playlist, could not identify any tags*

Indicates the HLS playlist returned is invalid, use curl to capture the playlist, (look at the section [Segment and Playlist Loading](#segment-and-playlist-loading) for how to find the playlist URL.


##### Corrupted Segment #####

Search back in the log from the line where the exception was thrown (e.g.):

````
07-02 00:22:58.915  3952  3952 E EventLogger: com.google.android.exoplayer2.ParserException: Cannot find sync byte. Most likely not a Transport Stream.
````

You are looking to find the *loadStarted* event just prior to this (there will not be a loadCompleted event as the load failed).  See [Segment and Playlist Loading](#segment-and-playlist-loading) section for this event.  Here you will find the URI to report back in the bug.  Verify if the segment loads (for VOD, otherwise you will need to go back in the rolling buffer to find it) and is non-zero length, if so the issue is a decryption problem.

Decryption problems for VCAS are either VCAS/Origin Server issues:

1. The VCAS / Origin server delivered the wrong key or a corrupted key.
2. The encryption initialization vector was wrong. The initialization vector is determined as described in [Pantos Section 5.2](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-5.2).

Or (less likely, but possible) a bug in the VCAS Android Client

In all of these cases, determine the playlist that contained the bad segment and use curl to load a copy of the playlist to include in the bug.


<div id="V475" />

### V475 - DRM Error &mdash; Invalid reply ###

This issue is caused by VCAS client returning error code 6 ("Bad Reply") to a key request (encrypt).

Check in the VCAS console if the CA Device ID for the client has been disabled.


<div id="V479" />

### V479 - DRM Error &mdash; Device Not Entitled ###

The issue is that the CA Device ID is not in VCAS (note this is a legacy VisualOn error code, but ExoPlayer uses the same message).

You should see in the logs something like:

````
09-21 11:09:29.815 24861 25141 E VerimatrixDataSourceFactoryNative: ConnectAndProvisionDevice failed: 10
09-21 11:09:29.816 24861 25141 E VerimatrixDataSourceFactoryNative: Connect/provision failure for uniqueIdentifier 0cfcda5d-57ae-369b-98aa-3a80aa661cad, vcasBootAddress devvcas02.tivo.com:8042, keyUri https://devvcas02.tivo.com/CAB/keyfile?s=866&r=ktvu&t=DTV&p=1600711200&Kd=AE5110F31B4F5DD42FB0318165F26292&Kc=6F17C025FB9D11EA83AD005056B79EEA, dataLen 66192: 5010
09-21 11:09:29.816 24861 25141 E VerimatrixDataSourceFactory: Failed in Verimatrix provisioning with code: 5010
````

The __5010__ is broken down as 10 is the VCAS error code (10 NotEntitled &mdash; Device not entitled) and _5000_ is the VCAS API call:

Code  | VCAS API
------------- | -------------
1000 | InitializeCommonResources()
2000 | VerifyHandshake()
3000 | SetUniqueIdentifier()
4000 | SetVCASCommunicationHandlerSettings()
5000 | ConnectAndProvisionDevice()

Look at the VCAS server logs searching for the IBI "Boot Request" lines for your device searching by CA Device ID (in this example: `oDQflbF+K8aCEqDSo5JiL6+FWiEcdoqUKIdP/jZcTCgM0PBuUTnwLPBNVAEqcCiu` (aka Network Device ID))

````
2020-10-06T13:01:39.428000-07:00 devvcas02.tivo.com IBI[http-nio-8042-exec-6] DEBUG | InternetTVBootInterfaceImpl.sendBootRequest(-1) | Boot url: /CAB/BOOT?
2020-10-06T13:01:39.429000-07:00 devvcas02.tivo.com IBI[http-nio-8042-exec-6] DEBUG | BootResponse.validate(-1) | Content length: 1024
2020-10-06T13:01:39.437000-07:00 devvcas02.tivo.com IBI[http-nio-8042-exec-6] DEBUG | BootResponse.validate(-1) | Decrypted boot body: 168    /CAB/BOOT?n=69544060&version=1,2,3,4,5,7,8&vdis=[PD:<Network Device Id>{oDQflbF+K8aCEqDSo5JiL6+FWiEcdoqUKIdP/jZcTCgM0PBuUTnwLPBNVAEqcCiu}PD]&client=Verimatrix-ViewRight-Web-4.3.5.0-VMK.PI20.2-csl2-web_android- ...

````

Verimatrix IBI will call OTT "StoreDeviceInfo" You should then see lines like this:

````
2020-10-06T13:01:39.441939-07:00 devvcas02 OTT[14205]: DEBUG | Process(VCAS041153) | S:005056B79EEA - Started processing the Store Device Info Request
...
2020-10-06T13:01:39.450330-07:00 devvcas02 OTT[14205]: INFO | GetNetworkInfo(VCAS002852) | S:005056B79EEA - Found Network for device [PD:<Network Device Id>{oDQflbF+K8aCEqDSo5JiL6+FWiEcdoqUKIdP/jZcTCgM0PBuUTnwLPBNVAEqcCiu}PD]
2020-10-06T13:01:39.450561-07:00 devvcas02 OTT[14205]: DEBUG | SetDeviceInfo(VCAS041280) | S:005056B79EEA - Successfully processed the Store Device Info request
````

If you do not see the device is found and in the network then the error is in service.


<div id="v511" />

### V511 - DRM library key file not entitled ###

This is caused when VCAS error code 32 (KeyFileNotEntitled &mdash; Key file not entitled) is returned by the streamer player client's decrypt call into VCAS.  Basically VCAS gets an error from the server when it attempts to decrypt content.  The client side log contains something like:

````
09-09 16:23:23.973  4168  7024 E VerimatrixDataSourceFactoryNative: Decrypt failure for uniqueIdentifier ea7fbfec-df51-3b69-9440-66aee8d0e731, vcasBootAddress acsm.vmx.cdvr.tds.net:8042, keyUri https://204.246.13.3/CAB/keyfile?s=22806&r=209999901&t=DTV&p=1599693443&kc=f9fd429cf23111eaa9d220677cd75cf4&kd=a164d80b400a8ef14598fab7fd416d67, dataLen 66176: 32
09-09 16:23:23.975  4168  7024 I VMK     : vmk_enalbe: 0
09-09 16:23:23.975  4168  7024 I VMK     : vmk_close
09-09 16:23:23.975  4168  7024 E VerimatrixDataSourceFactoryNative: Deleting /data/user/0/com.tivo.hydra.app//vstore.dat
````
The issue is caused by lack of content entitlement data in VCAS, that is likely to mean there is a mismatch between what TiVo service has for the client device and Verimatrix server.  To triage look at the Vermiatrix logs

Here look at the *keyUri* the value for _r_ (209999901) is the VCAS *networkContentId*.  In the Vermatrix.log on the VCAS server you search for this specific key request and evaluate if there are failures:

````
12/30/2016 15:42:18.091 - vcas - OTT - VCAS040034 - I - S:080027DAB7AF - [5196]Execute: URL path: /CAB/keyfile, Query: r=1234&t=DTV&p=0&wowzasessionid=1743785732&v=2cIkmtqkTAaRVfg3eq1mrLrT6C8%3d
````

If the key request succeeded expect to see lines like this logged for the request by Vermiatrix OTT 

````
12/30/2016 15:42:18.095 - vcas - OTT - VCAS000414 - I - C:2cIkmtqkTAaRVfg3eq1mrLrT6C8= - [5196]AuthorizeDTV: The OTT device is authorized for Content ID: 1234
````

<div id="v526" />

### V526 - DRM Error &mdash; Global Security Policy ###

This error is reported when the device makes it "Boot Request" to Verimatrix and IBI determines the device is Jailbroken.

For this VCAS global security policy has been set to not allow jailbroken devices, this is the default for VCAS:

> Global Policy is configured in /opt/vcas/internetTV/etc/OTT/OTT.INI file in ACSM server. It is sent to the ViewRight Web Client Library for iOS / Android upon boot request.
> 
> 0 = disallow rooted / jailbroken devices (default)
> 1 = allow rooted / jailbroken devices

<div id="v527" />

### V527 - DRM Error &mdash; Asset Policy ###

This error is reported if VCAS is configured to allow Jailbroken devices (unlikely) and a specific asset is set to require non-jail broken devices.  This is VCAS error code 48 AssetPolicySecurityError

Similar to the [V511](#v511) error this is reported on a key request, you should see:

````
09-09 16:23:23.973  4168  7024 E VerimatrixDataSourceFactoryNative: Decrypt failure for uniqueIdentifier ea7fbfec-df51-3b69-9440-66aee8d0e731, vcasBootAddress acsm.vmx.cdvr.tds.net:8042, keyUri https://204.246.13.3/CAB/keyfile?s=22806&r=209999901&t=DTV&p=1599693443&kc=f9fd429cf23111eaa9d220677cd75cf4&kd=a164d80b400a8ef14598fab7fd416d67, dataLen 66176: 48
````

As in V511, look at the *keyUri* the value for _r_ (209999901) is the VCAS *networkContentId*.  Look in VCAS OMI console (GUI) to see if the per-asset security policy is set for this content.

<div id="v529" />

### V529 - Can't Play &mdash; Bad State ###

This error is reported if VCAS client fails in the call to store the VCAS Communication settings (`SetVCASCommunicationHandlerSettings()`).

The client log will be:

````
09-21 11:09:29.815 24861 25141 E VerimatrixDataSourceFactoryNative: Failed to set communication handler settings to devvcas02.tivo.com:8042, /sdcard/VR -- 50
09-21 11:09:29.816 24861 25141 E VerimatrixDataSourceFactory: Failed in Verimatrix provisioning with code: 4050
````
The older (streamer-1.7) versions of the VCAS client code do blanket retry on failures.  The VCAS client code will return this error if the client re-trying an error returned previously from `SetVCASCommunicationHandlerSettings()`.  The `SetVCASCommunicationHandlerSettings()` checks for rooted boxes and returns error [V526](#v526) or [V527](#v527), if the app retries on this error it will this V529 error on each subsequent retry.  


<div id="v552" />

### V552 - Playlist Stuck ###

This is the *Playlist is stuck* error.  The playlist URI is included in the analytics log message, eg:

````
02-25 13:01:44.306  6028  6062 I clientcore: [diagnostics] ILogger \{ "M" : "StreamAnalyticsLogger: processSessionError: true/PLAYER_PLAYLIST_STUCK/3205/Playlist is stuck, uri [http://204.246.15.164/wp/cdvr1.prod.cdvr.tds.net/246293/YzF0d3c5YWhzbTBqaXdteXF2dW8/vxfmt=dp/h_3bfcafdc15a5ed5e7d8e470c12ce3edc/var3960000/vid/playlist.m3u8?device_profile=hls_hlsdrm_verimatrix]", "dvrTsn" : "tsn:A8F0000081AB5F1", "loggingVersion" : "1.0.5" }\
````

This occurs because the origin server is failing to update a live playlist regularly (note a *live* playlist is any playlist without an [EXT-X-END](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-4.4.3.4)).  The player considers the following variables:

1. The *Media Sequence Number* (MSN), is used to keep track of what segments have been removed from the live playlist, the origin responsibility is defined in [Section 6.2.2 Live Playlists](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-6.2.2) first paragraph
2. The segment count (SegCnt) (this will increase for type EVENT live playlists, for regular live it stays the same as window size)
3. If the live or EVENT playback is ended (when origin adds an <code>EXT-X-END</code> to a live playlist)

The player polls the playlist for updates (according to the rules in [Pantos Section 6.4.4](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-6.3.4) and checks if the playlist updated.  Three factors imply a reloaded playlist was an update:


Each time the player re-loads the playlist it check for *playlist updated*, that is one of these three case must be true:

1. MSN<sub>Reloaded Playlist</sub> > MSN<sub>Previous Playlist</sub>
2. SegCnt<sub>Reloaded Playlist</sub> > SegCnt<sub>Previous Playlist</sub>
3. EXT-X-END<sub>Reloaded Playlist</sub> is present and EXT-X-END<sub>Previous Playlist</sub> was not 

Case 1 is the normal live case (the window slides, old segments are removed),   Case 2 only occurs for live "EVENT", in that case old segments don't age out.  Case 3 is when the live EVENT ends.   Note, after case 3 the player stops reloading the playlist (so trivially it cannot be "stuck" after this)


<div id="v553" />

### V553 - Playlist Reset ###

This issue occurs during live playback when the player throws a [PlaylistResetException](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/source/hls/playlist/HlsPlaylistTracker.PlaylistResetException.html), the playlist URI is included in the analytics log message.   The StreamAnalyticsLogger log message is similar to playlist stuck, except the event is PLAYER\_PLAYLIST\_RESET

Similar to [V552 Playlist Stuck](#v552) error, except in this case the Origin Server has reset the [Media Sequence Number](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-4.4.3.2) (MSN) during live playback so that it does not follow the behavior required by [Section 6.2.2 Live Playlists](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-6.2.2).  

The player reports this when it determines after a playlist re-load where the playlist did not  update (see [V552 Playlist Stuck](#v552)) and the following condition is true

> MSN<sub>Reloaded Playlist</sub> + SegCnt<sub>Reloaded Playlist</sub> < MSN<sub>Previous Playlist</sub> 

Basically, the MSN jumped backward.

If this error occurs report it to the Origin Server vendor including the time and playlist URL from the log.


<div id="v554" />

### V554 - Sample Queue Mapping Failed ###

This issue occurs when there is a mismatch between the metadata in the HLS playlist (EXT-X-MEDIA and CODECS).  Only ExoPlayer versions that enable [chunkless prepare support](https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6) for faster channel change can report this error.  The error will include a `SampleQueueMappingException` in the logs that reports the mime type that is missing from the media.

Example with missing audio (audio/mp4a-latm) source data

````
com.google.android.exoplayer2.source.hls.SampleQueueMappingException: Unable to bind a sample queue to TrackGroup with mime type audio/mp4a-latm.
at com.google.android.exoplayer2.source.hls.HlsSampleStream.maybeThrowError(HlsSampleStream.java:64)
at com.google.android.exoplayer2.BaseRenderer.maybeThrowStreamError(BaseRenderer.java:134)
at com.google.android.exoplayer2.ExoPlayerImplInternal.doSomeWork(ExoPlayerImplInternal.java:584)
at com.google.android.exoplayer2.ExoPlayerImplInternal.handleMessage(ExoPlayerImplInternal.java:326)
at android.os.Handler.dispatchMessage(Handler.java:102)
at android.os.Looper.loop(Looper.java:193)
at android.os.HandlerThread.run(HandlerThread.java:65)

````

To fix this issue the Origin Server must either:

1. Remove the audio codec CODECS attribute in the variants (mp4a-latm is AAC audio, codec mp4a.40.x, [Mp4 Object Types](https://mp4ra.org/#/object_types))
2. Add an EXT-X-MEDIA with a source playlist for the missing mime type

Note this can also occur if the playlist is improperly authored for multiple audio streams, see details in [ExoPlayer Issue 7877](https://github.com/google/ExoPlayer/issues/7877)

<div id="v555" />

### V555 - Source Error ###

This is a catch all for any ExoPlayer playback exception that is not covered by one of the V5xx errors.

You must look in the log files to determine the specific root cause.  The most common presentation of this error is a failure to download a playlist or segment (after multiple retries).  In this case the logs will include the URL, eg

````
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834): HTTP error is due to invalid response code: 404
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834): Response message: Not Found
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Access-Control-Allow-Credentials: true
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Access-Control-Allow-Headers: *
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Access-Control-Allow-Methods: GET, HEAD, OPTIONS
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Access-Control-Allow-Origin: *
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Access-Control-Expose-Headers: Server,range,Content-Length,Content-Range
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Connection: keep-alive
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Content-Length: 345
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Content-Type: text/html
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Date: Tue, 31 Mar 2020 16:35:21 GMT
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Server: ATS/7.1.4
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    Set-Cookie: TRACKID=f1e293589e33d52c86ab0a428bb0850b; Path=/; Version=1
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    X-Android-Received-Millis: 1585672516957
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    X-Android-Response-Source: NETWORK 404
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    X-Android-Selected-Protocol: http/1.1
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    X-Android-Sent-Millis: 1585672516378
03-31 12:35:16.968 E/ExoPlayerPlayer( 6834):    null: HTTP/1.1 404 Not Found
03-31 12:35:16.969 E/ExoPlayerPlayer( 6834): For uri [http://rr.vod.rcn.net:8080/LINEAR/rolling-buffer/hgtvhd__tp1-1008007-hgtvhd-216188244416653010-66692689-1585098000-158509992000000001/hgtvhd__tp1-1008007-hgtvhd-216188244416653010-66692689-1585098000-158509992000000001.m4m/transmux/index.m3u8?ccur_st=0&ccur_et=1860&ccur_svc_type=rec&eprefix=lts&source_channel_id=hgtvhd]: onPlayerError (source):
````


<div id="v556" />

### V556 - Audio Configuration Error ###

This error is most likely a mismatch between the audio tracks the origin server presents and what the streamer STB supports.

Match the playlist presented by the origin server with the encoding spec, if it is compliant file a bug with the STB platform vendor


<div id="v557" />

### V557 - Audio Track Initialization Error ###

This error is an STB platform error, it is reported when ExoPlayer fails to initialize the AudioTrack (may occur at any point during playback if there is an error writing audio).

The TiVO ExoPlayer shared error recovery attempts to recover from some of these by re-tyring the initialization.  Here is an example log entry:

````
04-27 13:17:46.593 4155 7254 E IAudioFlinger: createTrack returned error -38
04-27 13:17:46.593 4155 7254 E AudioTrack: AudioFlinger could not create track, status: -38 output 0
04-27 13:17:46.594 4155 7254 E AudioTrack-JNI: Error -38 initializing AudioTrack
04-27 13:17:46.594 4155 7254 D AudioTrack: no metrics gathered, track status=-38
04-27 13:17:46.598 4155 7254 E android.media.AudioTrack: Error code -20 when initializing AudioTrack.
04-27 13:17:46.622 3125 3975 I bcm-audio: Audio output stream closed, stream = 0xaea36c00
04-27 13:17:46.636 4155 7254 E ExoPlayerImplInternal: Playback error.
04-27 13:17:46.636 4155 7254 E ExoPlayerImplInternal: com.google.android.exoplayer2.ExoPlaybackException: com.google.android.exoplayer2.audio.AudioSink$InitializationException: AudioTrack init failed: 0, Config(48000, 12, 40000)
04-27 13:17:46.636 4155 7254 E ExoPlayerImplInternal: at com.google.android.exoplayer2.audio.MediaCodecAudioRenderer.processOutputBuffer(MediaCodecAudioRenderer.java:738)
04-27 13:17:46.636 4155 7254 E ExoPlayerImplInternal
````
This error is caused by issues in the underlying STB platform implementation of Android's MediaCodec, report the issue to the STB vendor

<div id="v558" />

### V558 - Audio Write Error ###

This error occurs when the HDMI connection is lost (hot plug) and audio playback is in tunneled mode on the Broadcom STB platforms.  

The player should recover and retry with tunneling disabled  until the HDMI is online.

Jira issue [PARTDEFECT-1874](https://jira.tivo.com/browse/PARTDEFECT-1874) details the resolution steps for this issue.
