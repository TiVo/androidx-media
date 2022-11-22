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

#### Playlist Live Edge Offset

This value is simply measured, it is entirely the result of the Origin / Edge server video encode latency plus playlist load network latency. On playlist initial load the player determines the *Playlist Live Edge Offset*.  This is set as the time difference between real-time and the window end, using variables from the diagram above:

```java
   Current real-time - (Window.windowStartTimeMs + Window.getDuratioMs())
```

Visually the time after the end of the blue live window in the figure.

#### Target Live Offset

The value the first value set on the initial playlist update.  It is determined by one of two cases:

1. Explicitly from user setting `LiveConfiguration.targetOffsetMs`
2. Derived from the *Playlist Live Edge Offset* and Default position (from playlist parameters)

For the second case the *Target Live Offset* is derived from *start position* defined by the playlist parameters from the origin server, in this priority order:

1. Explicit start offset ([EXT-X-START](https://datatracker.ietf.org/doc/html/draft-pantos-hls-rfc8216bis-12#section-4.4.2.2)) using the `TIME-OFFSET`  directly, ignoring `PRECISE`
2. `PART-HOLD-BACK` value from [Server Control](https://datatracker.ietf.org/doc/html/draft-pantos-hls-rfc8216bis-12#section-4.4.3.8).
3. `HOLD-BACK` value from [Server Control](https://datatracker.ietf.org/doc/html/draft-pantos-hls-rfc8216bis-12#section-4.4.3.8).
4. Fallback to 3 x EXT-X-TARGET-DURATION.

The *Target Live Offset* is computed as this *start position* plus the measured *Playlist Live Edge Offset* (described above)

**Note** prior to ExoPlayer 2.15.1 the fallback was 3 segments, not 3 * target duration.

#### Default Start Position

This value sets the initial position in the Live window on start of live window.  The value is set from either:

1. The [EXT-X-START](https://datatracker.ietf.org/doc/html/draft-pantos-hls-rfc8216bis#section-4.4.2.2), if `PRECISE` is specified as `PRECISE=YES` the TIME-OFFSET value is taken literally, the player fetches segment containing the time offset value and skips samples until it is reached, other wise it is the nearest partial or completed segment containing the time offset value
2. The Target Live Offset either derived or explicitly set in the previous section.

The second case derives the Default start by subtracting out the *Playlist Live Edge Offset* from the computed or explicitly set *Target Live Offset* then selecting the start time of the nerest partial or completed segment containing this position. 

### Live Edge Adjustment

The Live Edge is adjusted by varying playback speed very slightly faster or slower to adjust the playback position to match the *Current Target Live Offset*.  This *Current Target Live Offset* value starts at the initial *Target Live Offset* and may be increased by observed rebuffering. 

The parameters for this are described in the [Configuring live playback parameters](https://exoplayer.dev/live-streaming.html#configuring-live-playback-parameters) section of the ExoPlayer Live Streaming document.  

Recommended values for these are T.B.S.



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

