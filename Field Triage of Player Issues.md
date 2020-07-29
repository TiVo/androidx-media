# Field Triage of Player Issues #
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
ExoPlayer logs playback state using the [EventLogger](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/util/EventLogger.html), you can refer to this java doc for specifics on all the events. The TiVo version of this class modifies it to include additional logging.

### General Format ###

````
... EventLogger: loadStarted [eventTime=4048.52, mediaPos=575.76, window=0, period=0, <additional description>]
````

Time and position are in seconds, `eventTime` is the time since playback began, `mediaPos` is the current playback position (see [Timeline](#timeline) section)

The *\<additional description\>* text is detailed under each specific log entry.

### Specific Log Entires ###

<div id="timeline" />
#### Timeline
The timeline event reports the playlist updates, the playlist defines the seek boundary.  There are three types of timelines, Live, Event and VOD.

An example VOD timeline event:

````
07-20 11:56:04.493 25219 25219 D EventLogger: timeline [eventTime=9222.35, mediaPos=0.00, window=0, periodCount=1, windowCount=1, reason=PREPARED
07-20 11:56:04.493 25219 25219 D EventLogger:   period [1807.81]
07-20 11:56:04.493 25219 25219 D EventLogger:   window [1807.81, true, false]
07-20 11:56:04.493 25219 25219 D EventLogger: ]
````

The `reason=PREPARED` indicate this is the initial timeline (for VOD this will be the only event).  The `window [1807.81, true, false]` indicates the playlist is 1807.81 seconds long and not dynamic.

Live and Event timeline events are more interesting, these must occur no less frequently then the playlist update interval (specified in the `EXT-X-TARGETDURATION` header in the playlist).  These events should happen every target duration, if not you will see error [V552](#v552).

Update here will have `reason=DYNAMIC`, an example timeline update for live:

````
07-20 12:08:28.315 25219 25219 D EventLogger: timeline [eventTime=9966.17, mediaPos=569.35, window=0, period=0, periodCount=1, windowCount=1, reason=DYNAMIC
07-20 12:08:28.315 25219 25219 D EventLogger:   period [?]
07-20 12:08:28.315 25219 25219 D EventLogger:   window [594.59, true, true]
07-20 12:08:28.315 25219 25219 D EventLogger: ]
````

For live the playback position (`mediaPos`) should remain near the edge of the window.  In the example above, it is 25.24 seconds behind live.  
````
 594.59 - 569.35 = 25.24
````

The player starts 3 segments from the window edge for live (for 6 second segments, that is 18 seconds).  The player will drift closer or further from the live edge depending on how regular the origin server updates the playlist and how fresh the edge cached copy of the playlist is.

<div  id="playback-state"/>
#### Playback State
Each time playback state changes this event is logged

````
EventLogger: state [eventTime=10729.90, mediaPos=571.45, window=0, period=0, true, READY]
````

The *\<additional description\>* text show the play/pause state (true is playing, false is paused) and the playback state ("READY" in the example).  Playback states are detailed in [Player state](https://exoplayer.dev/doc/reference-v1/com/google/android/exoplayer/ExoPlayer.html#State).  During playback it will be either "READY" (playing) or "BUFFERING" (waiting for segments to load).

<div id="segment-and-playlist-loading"/>
#### Segment and Playlist Loading

ExoPlayer logs commencement and completion events for loading playlists and segments.

The start of the segment load operation is logged by the *loadStarted* event, an example is:

````
EventLogger: loadStarted [..., uri: http://edge2.md.vod.rcn.net/ccur-session/02_353693272/LINEAR/rolling-buffer/cnnhd/cnnhd/transmux/CCURStream_cnnhd0-10_265736767.tsa]
````

The specific *\<additional description\>* simply contains the uri, allowing you to match up to the completion event.

Here is an example segment load completion:

````
EventLogger: loadCompleted[media] -  [..., trackId: 2 load-duration: 605ms codecs: mp4a.40.2,avc1.4d4020 start(dur): 1795794/6006 uri: http://live1.nokia.tivo.com/ktvu/vxfmt=dp/h_6940567a96b64b4b5f926f42211420ea/var4950000/vid/seg23997408_w1595615569.ts?device_profile=hls_hlsdrm_verimatrix]
````
The specific *\<additional description\>* text is:

* *trackId* &mdash; the playlist the segment is loaded from
* *load-duration* &mdash; measured time to download the segment
* *start(dur)* &mdash; the starting timestamp (in milliseconds) duration of the segment (example is 6.006 seconds)
* *uri* &mdash; the segment URI 

During Visual Trick Play, segments are i-Frame only segments

````
EventLogger: loadCompleted[media] -  [... trackId: iFrame-0 load-duration: 33ms codecs: avc1.640028 start(dur): 1517508/1001 offset/len: 885668/183300 uri: http://live1.nokia.tivo.com/kqedplus/vxfmt=dp/h_83302b14e5679d9fd9fa6a9b7d7a361a/trk8756000/seg4779778_w1596042051.ts?device_profile=hls]
````
For the i-Frame only segment loads, the byte offset is included

* *offset/len* &mdash; the byte-offset and length of the i-Frame data in the segment.

Of particular interest when diagnosing stalls is the load duration, this must be substantially less the duration of the segment or stalls will almost certainly occur

And a playlist load example completion:

````
EventLogger: loadCompleted - load-duration: 129ms, URI: http://live1.nokia.tivo.com/ktvu/vxfmt=dp/h_6940567a96b64b4b5f926f42211420ea/var1980000/aud1101/playlist.m3u8?device_profile=hls_hlsdrm_verimatrix [eventTime=567.11, mediaPos=1790.96, window=0]
````

Live playlist loads should trigger a [Timeline](#timeline) update event, if not the origin/transcoder is not producing new segments quickly enough which can result in stalls.

<div id="level-shift-logging"/>
#### Level Shift Logging
ExoPlayer makes level change decisions each time it starts before it starts to load a new video segment.  There are two log messages that indicate the level has changed.

1. *loadingFormatChanged* &mdash; Indicates a level shift has occurred and a new Format (variant) segment load is starting
2. *videoFormatChanged* &mdash; Indicates samples with a new format have commenced playback (delay from loadingFormatChanged is because of buffering)

Here is a sample level shift (loading format change)

````
EventLogger: loadingFormatChanged [eventTime=35.62, mediaPos=579.35, window=0, period=0, Buffered: 15283ms -- Old: 1 - 960x540@3725408 New: 2 - 1280x720@7342528]
````

The *\<additional description\>* text shows the current buffering level, and the old and new variant (spacial resolution @ bit-rate (bits per second)).  The resolution and bit-rate are from the HLS playlist.

Note ExoPlayer will not level shift up (increasing bit-rate) unless there is at least 15 seconds buffered, and it will not level shift down unless there is less than 25 seconds buffered.  For VOD playback, ExoPlayer loads until it reaches a goal of 50 seconds of buffering.  For live (and EVENT live) it will buffer everything from the live point (assuming it is less then 50 seconds)

Here is an example log message for the playing format change:

````
EventLogger: videoFormatChanged - Old: 2 - 720x480@2570336 New: 5 - 1280x720@11380768 [eventTime=17.80, mediaPos=582.19, window=0, period=0]
````

This simply always follows the loading format change.

<div id="bandwidth-estimate-logging"/>
#### Bandwidth Estimate Logging
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
#### Track Selection Logging

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

Indicate that a discontinuity or timestamp issues is the cause.  The only way really to be sure is to capture the section of rolling buffer (or the VOD asset) that caused the stall.  You can use the [m3u8_download](https://github.com/tivocorp/m3u8_utils) utility to do this or the stream validator.

Otherwise look to the sections either on [Buffering Issues](#buffering-issues) or [Live Playlist Stalls](#live-playlist-stalls) for the root cause.


<div id="live-playlist-stalls" />
##### Live Playlist Stalls
During live playback timely updates to the current active playlist are essential to maintaining an adequate media buffer.  First look at the [Timeline](#timeline) logging section.  The timeline events must happen at regular intervals with a second of the segment duration.   Each second the timeline update is late is a second of lost buffer.

These two conditions together indicate the origin / transcoder has stopped producing live content.

* playlist load completions (see [Segment and Playlist Loading](#segment-and-playlist-loading)) are occurring regularly
* timeline update events are not occurring

Periodic delays in updates can cause stalls, a persistent delay will eventually cause error [V552](#v552)


<div id="buffering-issues"/>
##### Buffering Issues

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
##### Timestamp Issues

ExoPlayer synchronizes the active media streams; video and text/captions if enabled to the audio stream. This synchronization is based on timestamps in the media segments.

Either:

1. The timestamps are monotonically increasing and in step in all the media streams
1. or, the HLS playlist must be marked with a EXT-X-DISCONTINUITY tag before the timestamps

Audio plays continuously (while not buffering or paused) at the sample rate specified in the encoding, and Android reports the current audio playback time to ExoPlayer, and ExoPlayer matches this to video frames (and text if enabled) timestamps to determine the frame to show.

<div id="discontinuity-issues" />
##### Discontinuity Issues

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

#### Enablement
Visual Trick-Play (VTP) is supported by ExoPlayer, for Hydra there is an APK customization switch to determine if it is actually enabled or not.

Two things must be true for VTP on a platform:

1. The MSO's APK must be customized to enable VTP
2. The origin server must produce an i-Frame only track.

Look at the [Track Selection Logging](#track-selection-logging) for the presence or absence of the i-Frame only track to determine if the origin support VTP.

To determine if the APK is *specialized* for the MSO to enable VTP, check the configuration in the Perforce, the [canned_specialization](https://p4web.tivo.com:1666/@md=d&cd=//d-flash/hydra/streamer-1-7/hydra/app/hydra/cmd/&cdf=//d-flash/hydra/streamer-1-7/hydra/app/hydra/cmd/canned_specializations&c=1fC@//d-flash/hydra/streamer-1-7/hydra/app/hydra/cmd/canned_specializations?ac=22) file must define the resource `USE_VISUAL_TRICKPLAY`

Alternately, if you only have the APK, look in the APK `res/values/strings.xml` for the string `USE_VISUAL_TRICKPLAY` this must be present and true.

#### Playback

During VTP playback you should see i-Frame only segment loads (see [Segment and Playlist Loading](#segment-and-playlist-loading)).  


<div id="errorcodes-and-messages"/>
## ErrorCodes and Messages ##

In this section we will explore select UI error overlays from the [TiVo Experience 4 Error Codes and Messages](https://confluence.tivo.com/display/PSRR/Mira+4.10?preview=%2F160300940%2F160300941%2FTiVoExperience4_Error_Codes_and_Messages_ALL_27FEB2020+%281%29.pdf) that playback errors and describe how to determine the root cause by examining provisioning and logs.

<div id="V549" />
### V549 - Download Error
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
### V551 - Parser Failed
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


##### Corrupted Segment
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


<div id="v552" />
### V552 - Playlist Stuck
This is the *Playlist is stuck* error.  The playlist URI is included in the analytics log message, eg:

````
02-25 13:01:44.306  6028  6062 I clientcore: [diagnostics] ILogger \{ "M" : "StreamAnalyticsLogger: processSessionError: true/PLAYER_PLAYLIST_STUCK/3205/Playlist is stuck, uri [http://204.246.15.164/wp/cdvr1.prod.cdvr.tds.net/246293/YzF0d3c5YWhzbTBqaXdteXF2dW8/vxfmt=dp/h_3bfcafdc15a5ed5e7d8e470c12ce3edc/var3960000/vid/playlist.m3u8?device_profile=hls_hlsdrm_verimatrix]", "dvrTsn" : "tsn:A8F0000081AB5F1", "loggingVersion" : "1.0.5" }\
````

This occurs because the origin server is failing to update a live playlist regularly (note a *live* playlist is any playlist without an [end tag](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-4.4.3.4)).  The player polls the playlist for updates, as long as the playlist is *live*.  It is required for one of the following to change or the playlist is considered stuck:

1. The playlist [Media Sequence Number](https://tools.ietf.org/html/draft-pantos-hls-rfc8216bis-04#section-4.4.3.2) for the new playlist is greater than the last loaded playlist (this would be live, not live event where old segments don't age out)
2. The segment count of the new playlist is greater than the last loaded playlist (this occurs for type EVENT live playlists)
3. The segment counts are equal, but the new playlist just had an end tag added (a Live playlist transitions to an ended EVENT

