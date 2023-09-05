## Live Offset In ExoPlayer 2.15.1

ExoPlayer 2.15.1 includess upport for LL-HLS (Low Latency HLS) and DASH Ultra-Low Latency.  Along with these features comes the ability to dynamically adjust the Live Offset to match a target. 

### Summary

The [Live Streaming](https://exoplayer.dev/live-streaming.html) document outlines the parameters and defines live offset.  It is defined simply:

​    **Live Offset** &mdash; The difference between the current real-time and the playback position.

The dynamic live offset target operation is described in [Low-latency live streaming with ExoPlayer](https://medium.com/google-exoplayer/low-latency-live-streaming-with-exoplayer-8552d5841060) in the *Automatic live offset adjustments* section.  This matching live offset to a fixed time enables features like synchronized playback in a bar or health club setting. 

For 2.15.1 ExoPlayer adds the [LiveConfiguration](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/MediaItem.LiveConfiguration.html) to allow clients to control the Live Offset management on a per playback  session.  The  [targetOffsetMs](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/MediaItem.LiveConfiguration.html#targetOffsetMs) in this can be set to explicity set a target for offset from actual live otherwise the target value is computed based on start position.

### Live Playback Startup

There are these variables instrumental in controlling ExoPlayer 2.15.1 live playback:

1. Default Start Position &mdash; determined on first playlist load, sets initial playhead position in the Live window.
2. Target Live Offset &mdash; new to ExoPlayer 2.15.1, either derived on playlist update or set explicity by user (`LiveConfiguration.targetOffsetMs`) 

The figure from the Live Streaming document is helpful to visualize these variables and reference when reading the following sections that describe how the variables are initialized and updated:



![Live Window](https://exoplayer.dev/images/live-window.png)

- **windowStartTimeMs** &mdash; time since the Unix epoch, this comes form the EXT-X-PROGRAM-DATE-TIME for each playlist referesh (`onTimelineChanged()` event with `TIMELINE_CHANGE_REASON_SOURCE_UPDATE`).  This moves (obviously) as segments are removed from a sliding live window.  It is in server time, as it's metadata in the manifest

- **Window.getDefaultPositionMs()** &mdash; window relative position to start playback, this is set by the rules above.

- **Player.getCurrentPosition()** &mdash; playback posistion in duration of the relative to the Live window start, so 0 to `Window.getDurationMs()`

- **Player.getCurrentLiveOffset()** &mdash; the difference between playback posistion converted to time since the Unix epoch and Current real-time

The table below summarizes how the two parameters, *Default Start Position* and *Target Live Offset*, interact depending if they are specified explicity (server or user configuration) or defaulted.

| Start Position | Target Live Offset | Description                                                  |
| -------------- | ------------------ | ------------------------------------------------------------ |
| specified      | specified          | **Both** values are used, if they are incongruent adjustment will work to<br />move position to the specifed *Target Live Offset*. |
| specified      | defaulted          | *Target Live Offset* is derived from explicit start position |
| defaulted      | specified          | Start position is derived from *Target Live Offset*:<br /> `Start = Target Live Offset - Playlist Live Edge Offset` |
| defaulted      | defaulted          | *Target Live Offset* is derived from *implicit* start position (`3 * targetDuration`) |

The following two sections go into greater detail on the steps to arrive at the final two values.

#### Playlist Live Edge Offset

This value is simply measured, it is **entirely** the result of the Origin / Edge server video encode latency plus playlist load network latency, there is nothing the client can do to affect this. 

On playlist initial load the player measures the *Playlist Live Edge Offset*.  This is set as the time difference between real-time and the window end, using variables from the diagram above:

```java
   Current real-time - (Window.windowStartTimeMs + Window.getDuratioMs())
```

Visually the time after the end of the blue live window in the figure.

#### Live Playback Start Position

This is where the player sets the initial seek position, in the *Live Window*, when starting live playback, the *Default position* in the diagram above.  In 2.15.1 the player first determines a *Target Live Offset*, then uses this to determine the best start position in the window.

##### Target Live Offset

This is the initial value the player will use as the target Live Offset goal, it also determines the *Default position*.  It is set on the first playlist update of live playback session. The player first measures the *Playlist Live Edge Offset* then computes the Target Live Offset as, in priority order.

1. Explicitly from user setting `LiveConfiguration.targetOffsetMs`
1. Explicit start offset ([EXT-X-START](https://datatracker.ietf.org/doc/html/draft-pantos-hls-rfc8216bis-12#section-4.4.2.2)) using the `TIME-OFFSET`.  Formula is: `(window duration - startOffset) + Playlist Live Edge Offset`
1. Default fallback to 3 x EXT-X-TARGET-DURATION. Formula is: `(3 * targetDuration) + Playlist Live Edge Offset`

This computed Target Live Offset value is constrained to be within the live window duration, however chosing an explict value that falls very close to the duration of the live window will lead to excessive rebuffering.

**Note** prior to ExoPlayer 2.15.1 the fallback was 3 segments, not 3 * target duration.

##### Default Start Position

This value sets the initial position in the Live window on start of live window, this is the seek position for live playback startup.  The value is set from either:

1. The [EXT-X-START](https://datatracker.ietf.org/doc/html/draft-pantos-hls-rfc8216bis#section-4.4.2.2), if `PRECISE` is specified as `PRECISE=YES` the TIME-OFFSET value is taken literally, the player fetches segment containing the time offset value and skips samples until it is reached, otherwise the value is rounded up to the nearest segment start (e.g. TIME-OFFSET:-20.0, with 6 second segments will round up to 24 seconds (4 segments back))
2. Derived from the *Target Live Offset* from the previous section,  formula is `Target Live Offset - Playlist Live Edge Offset`

**Note** if both an explicit `LiveConfiguration.targetOffsetMs` and a server requested `EXT-X-START` value are specified this can force an initial adjustment condition from the start position, that is no matter where the player starts playback it will attempt to adjust the the requested `LiveConfiguration.targetOffsetMs`

### Live Edge Adjustment

Live Edge Adjustment varies the playback speed very slightly faster or slower to adjust the playback position to match the *Current Target Live Offset*.  This *Current Target Live Offset* value starts at the initial *Target Live Offset* and may be adjusted by the player.

The parameters for this are described in the [Configuring live playback parameters](https://exoplayer.dev/live-streaming.html#configuring-live-playback-parameters) section of the ExoPlayer Live Streaming document.  

Adjustments to *Current Target Live Offset* occur under the following circumstances:

1. Rebuffering causes an increase (further back from live) to the  *Current Target Live Offset*.  This adjusment is the value set for [setTargetLiveOffsetIncrementOnRebufferMs()](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/DefaultLivePlaybackSpeedControl.Builder.html#setTargetLiveOffsetIncrementOnRebufferMs(long)) for each rebuffering event.  The default is 500ms.
2. During playback the player keeps track of the current *Live Offset* and buffering level to calculate a minimum possible live offset.   Changes in this value are smoothed (see the [setMinPossibleLiveOffsetSmoothingFactor()](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/DefaultLivePlaybackSpeedControl.Builder.html#setMinPossibleLiveOffsetSmoothingFactor(float)) description).  This can decrease the  *Current Target Live Offset* if the buffering level increases or increase it if the value is approaching the minimum possible live offset.

With the default settings the player will:

1. Compute the initial *Target Live Offset* based on the default start position (3 * TARGET_DURATION) and start *Current Target Live Offset* at this initial value.
2. Adjust playback speed by at most +/- 3% when *Live Offset* is not *Current Target Live Offset*
3. Increase the *Current Target Live Offset* by t

While the player is adjusting the *Live Offset* with playback speed it is also adjusting the *Current Target Live Offset* based on a smoothed function of the mimimum possible live offset.  See the [setMinPossibleLiveOffsetSmoothingFactor()](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/DefaultLivePlaybackSpeedControl.Builder.html#setMinPossibleLiveOffsetSmoothingFactor(float)) description, the [Defaullt Value](https://exoplayer.dev/doc/reference/constant-values.html#com.google.android.exoplayer2.DefaultLivePlaybackSpeedControl.DEFAULT_MIN_POSSIBLE_LIVE_OFFSET_SMOOTHING_FACTOR) sets to 3 stddev away (the 99 %tile)

### Recommended Settings

There are two use cases for Live Offset Adjustment:

1. **Network Adaptation** &mdash; Adjusting the target setback from the live edge to balance minimal glass to glass time with the quality of the clients network.
2. **Hospitality** &mdash; Maintaining a consistent  playback position for multiple displays in close proximity.

These use cases require different approaches to adjustment.

There are two sets of parameters available for adjustment, `LiveConfiguration` (per channel adjustable) and the more global `DefaultLivePlaybackSpeedControl` values.  The Live Configuration parameters are decribed in [Configuring live playback parameters](https://exoplayer.dev/live-streaming.html#configuring-live-playback-parameters). The Java doc for [DefaultLivePlaybackSpeedControl](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/DefaultLivePlaybackSpeedControl.Builder.html) describes the global values for the algorithm, and the section [Customizing the playback speed adjustment algorithm](https://exoplayer.dev/live-streaming.html#customizing-the-playback-speed-adjustment-algorithm), in the ExoPlayer Live Streaming document gives a more simplified description.

#### Network Adaption

For this use case we expect live offset to migrate futher back (larger offset) in the case of adverse network conditions and only decrease (move closer to live) after a period of increased buffering.  The parameter settings to achvie this are detailed in the following two sections

##### Live Configuration

- `targetOffsetMs` &mdash; Should be left to default value (unset) then the *Target Live Offset* is derived from the *Live Playback Start Position*, as described in the previous sections Live Playback Startup.   The target for Live Offset will **never** be set less then this value (explicit or dervied)
- `minOffsetMs` &mdash; Best left to the default (not set), all this does is set a minimum on the *targetOffsetMs* 
- `maxOffsetMs` &mdash; See the description of *minOffsetMs*, again leave this as default of unset. 
- `min/maxPlaybackSpeed` &mdash; If unset the default fallback values in  `DefaultLivePlaybackSpeedControl` are used to control how quickly the player adjusts playback to achieve the Target Live Offset

##### Live Playback Speed Control

The default values are not very agressive in adpating to network conditions.   The parameter suggestions are as follows:

- `fallbackMin/MaxPlaybackSpeed` &mdash; fallback defaults if the per channel values are not set.  The defaults are +/- 3% which is practially unnoticable and should be sufficient for the Network Adaption use case.
- `proportionalControlFactor` &mdash; Changing this from the default is not recommended, it makes the speed changes much more noticeable.
- `targetLiveOffsetIncrementOnRebufferMs`: This value is added to the target live offset whenever a rebuffer occurs.  The default is 500ms, a more agressive setting (larger value) would cause the live offset to be more reactive to bad network conditions.
- `minPossibleLiveOffsetSmoothingFactor`: An exponential smoothing factor that is used to track the minimum possible live offset based on the currently buffered media. A value very close to 1 means that the estimation is more cautious and may take longer to adjust to improved network conditions, whereas a lower value means the estimation will adjust faster at a higher risk of running into rebuffers.  The default value is 0.999f so it is very conservative in reducing the live offset when more buffer is available.

#### Hospitality 

T.B.S.

### Examples

The descriptions above of the algorithm are complicated enough to warrant some examples.   Each of the following example calculations assumes the following pre-conditions:

* Playlist Live Edge Offset = 11.329 seconds (measured)
* Duration = 1,795.794 seconds (299 segments)
* Segments durations = 6.006 seconds

#### Default Settings

With default `LiveConfiguration` and no other playlist metadata the target duration drives the calculations.

For example, `EXT-X-TARGETDURATION:7`, start offset is 21 seconds (3 * 7), so start is the closest segment containing 21 seconds back from the end of the playlist, from this we compute:

* Target Live Edge = 32.329 seconds (21 + 11.329)

- Default Start Position = 1,771.77 seconds (4 segments back is nearest start > 21 seconds back)

#### Specified Start (EXT-X-START)

Here the tag is `#EXT-X-START:-30`, that is start from the start of the closest segment containing the time 30 seconds from the end of the playlist. With this set of values, we compute:

- Target Live Offset = 41.329 (30 + 11.329)
- Default Start Position = 1,765.764 seconds (5 segments back is -30.03, nearest start > 30 seconds back)

#### Specified targetLiveOffset

In this example the `LiveConfiguration.targetLiveOffsetMs` is set to 15,000.  So this implies a start position, 15.0 seconds - *Playlist Live Edge Offset* = 3.671 seconds.  Assuming the same preconditions we compute:

- Target Live Offset = 15.0 seconds (since `targetLiveOffsetMs` is > Live Edge Offset it is used directly)
- Default Start Position = 1,789.788 seconds (1 segments back is -6.06, nearest start > 3.671 seconds back)

In this example,  a 15 second target, with a 11.329 second Playlist Live Edge Offset presents an agressive target.

#### Specified targetLiveOffset with EXT-X-START:-30

Again `LiveConfiguration.targetLiveOffsetMs` is set to 15,000.  However the origin server configuration sets a 30 second start set back.   Assuming the same preconditions we now compute:

- Target Live Offset = 15.0 seconds (since `targetLiveOffsetMs` is > Live Edge Offset it is used directly)
- Default Start Position = 1,765.764 seconds (5 segments back is -30.03, nearest start > 30 seconds back)

In this case, the set *Live Offset* target is difficult to impossible to reach.

### Logging

This section describes the detailed debug logging for a sample session.

#### Startup

In this example, all the default settings for Live Offset are used, the playlist has 6.006 second segments and a 7 second target duration.  On startup (first timeline update) the target live offset is set from these values:

```
03-21 09:49:09.734  9184  9220 I LoggingLivePlaybackSpeedControl: setLiveConfiguration() init idealTargetLiveOffsetUs: 33.50
```

The startOffset is 3 * target duration (or 21 seconds), rounded up to the segment boundary, so  24.024 seconds.  At the time this logline printed the *Playlist Live Edge Offset* was 8.96 seconds, so the *Target Live Edge Offset* is set to 33.50 (21 + 12.5)

```
03-21 09:49:09.751  9184  9184 D EventLogger: timelineInitialized [eventTime=1.09, mediaPos=570.57, buffered=0.00, window=0, period=0, Live - duration: 594.594000, endTime: 2023-03-21 09:48:57.211 (1679417337211), now-endTime: 12.54, startOffset: 24.024]
```

Later the event for the first timeline update is delivered to the applicaiton thread, by then the *Playlist Live Edge Offset* (`now-endTime`) has changed.  This value will change depending on where we are in the playlist refresh cycle.

#### Adjustment

Adjustment begins with the actual live offset (46.37) way behind the current target (33.50) and very little buffer.  So two things are adjusted:

1. The current target live offset is increased to 41.83
2. Playback speed is increased to the max allowed (3%)

```
03-21 09:49:19.571  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 33.50 to: 41.83 (delta 8.33), liveOffset: 46.37, playbackSpeed 1.030x, idealTargetLiveOffset 33.50, buffer   4.5
03-21 09:49:19.573  9184  9220 I LoggingLivePlaybackSpeedControl: intiating fast playback (1.030x) while liveOffset 46.37 > targetLiveOffset 41.83, idealTargetLiveOffset 33.50, buffer   4.5
```

##### Increasing Current Target Live Offset

While live adjustment is in progress, every [setMinUpdateIntervalMs()](https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/DefaultLivePlaybackSpeedControl.Builder.html#setMinUpdateIntervalMs(long)) (default is 1 second) the current target live offset is re-evalutated based on the current mimimum possible live offset (liveOffset - buffered), here the target is being gradually increased (logs are printed when the delta reaches > 3 seconds) .

In this example, the minimum possible live offset is 22.05 (44.45 - 22.4) So, in this case we can see the initial target live offset (21.59) was determined to be not reachable.

```
03-21 09:50:41.926  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 45.49 to: 42.46 (delta -3.04), liveOffset: 44.45, playbackSpeed 1.030x, idealTargetLiveOffset 33.50, buffer  22.4
```
This adjustment contiunes, slowly, as the buffering condition remains favorable.  Note the *Target Live Offset* will never be less then the *Ideal Target Live Offset*.

```
03-21 09:51:33.155  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 42.46 to: 39.40 (delta -3.06), liveOffset: 42.91, playbackSpeed 1.030x, idealTargetLiveOffset 33.50, buffer  19.2
03-21 09:52:24.401  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 39.40 to: 36.34 (delta -3.06), liveOffset: 41.38, playbackSpeed 1.030x, idealTargetLiveOffset 33.50, buffer  18.9
```

##### Adjustment Ends

At some point the *Live Offset* reaches *Current Target Live Offset* within the tolerance values and adjustment stops, the logging for this looks like:

```
03-21 09:56:20.614  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 36.34 to: 34.45 (delta -1.88), liveOffset: 34.46, playbackSpeed 1.000x, idealTargetLiveOffset 33.50, buffer  14.2
03-21 09:56:20.614  9184  9220 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 34.46, targetLiveOffset 34.45, idealTargetLiveOffset 33.50, buffer  14.2
```

Note the process took about 7 minutes in this example and *Current Target Live Offset* is still greater then *Ideal Target Live Offset*.  Further favorable buffering conditions will reduce *Current Target Live Offset* to the *Ideal Target Live Offset* if possible, these next logging sequences show this:

```
03-21 09:56:27.651  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 34.45 to: 34.43 (delta -0.02), liveOffset: 34.46, playbackSpeed 1.003x, idealTargetLiveOffset 33.50, buffer  15.0
03-21 09:56:27.653  9184  9220 I LoggingLivePlaybackSpeedControl: intiating fast playback (1.003x) while liveOffset 34.46 > targetLiveOffset 34.43, idealTargetLiveOffset 33.50, buffer  15.0
03-21 09:57:12.873  9184  9220 I LoggingLivePlaybackSpeedControl: changed targetLiveOffset from: 34.43 to: 33.50 (delta -0.94), liveOffset: 33.51, playbackSpeed 1.000x, idealTargetLiveOffset 33.50, buffer  10.9
03-21 09:57:12.874  9184  9220 I LoggingLivePlaybackSpeedControl: Adjustment stopped with liveOffset 33.51, targetLiveOffset 33.50, idealTargetLiveOffset 33.50, buffer  10.9
```

##### Reducing Current Target Live Offset

To start, simulate a way to agressive choice for start position by seeking forward to passed a safe point in the window:

````
03-21 12:16:09.814 10390 10390 D EventLogger: positionDiscontinuity [eventTime=6000.66, mediaPos=590.00, buffered=4.61, window=0, period=0, reason=SEEK, PositionInfo:old [window=0, period=0, pos=573312], PositionInfo:new [window=0, period=0, pos=590000]]
03-21 12:16:09.853 10390 10641 D LoggingLivePlaybackSpeedControl: setTargetLiveOffsetOverrideUs(26.97), idealTargetLiveOffsetUs was: 35.13s
03-21 12:16:15.386 10390 10641 I LoggingLivePlaybackSpeedControl: on rebuffer move targetLiveOffset - previous: 26.97, current 27.47, delta 0.50
````

The seek sets the *Current Target Live Offset* directly with the `setTargetLiveOffsetOverrideUs()` method.

Here we see the rebuffer moves the target back 500ms.

