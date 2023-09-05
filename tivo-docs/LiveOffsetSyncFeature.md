## Feature Summary

The ExoPlayer [Live Streaming](https://exoplayer.dev/live-streaming.html) document outlines the parameters and defines live offset.  It is defined simply:

​    **Live Offset** &mdash; The difference between the current real-time and the playback position.

The dynamic live offset target operation is described in [Low-latency live streaming with ExoPlayer](https://medium.com/google-exoplayer/low-latency-live-streaming-with-exoplayer-8552d5841060) in the *Automatic live offset adjustments* section.  This feature uses ExoPlayer Automatic live offset adjustment to maintain synchronized playback across multiple screens in a bar or health club setting. 

## Requirements

1. Xperi Android Streamers running version18.x
2. Stream with at least 1 PCM audio (AAC) track
3. Relatively robust network (any stalling will affect the feature's performance)
4. Origin Server time is synchronized with an NTP server (for DASH a UTCTime element synchronizes time)

## Setup and Operation

The following service settings turn the feature on / off and allow setting a specific value for Target Live Offset:

​      ***NOTE: The exact names of the settings and service console mechanisms for setting them is not yet finalized***

* **LIVE_OFFSET_DEFAULT** &mdash; set the *Target Live Offset* value, that is the number of seconds behind the absolute live edge (wall-clock now) the player should maintain.   This is set per-MSO, it will override their existing baked into the APK `START_OFFSET` (acutally named "LIVE_OFFSET") parameter.  Note this setting is used even if **FAST_RESYNC** is turned off. Parameter is optional, if not specified and **FAST_RESYNC** is set, the client will default to 40 seconds back.
* **LIVE_OFFSET_SYNC** &mdash; set the *Target Live Offset* value, exactly the same semantics as **LIVE_OFFSET_DEFAULT**, except this value is used only when  **FAST_RESYNC** is set to on. Parameter is optional,  If this value is not specified the **LIVE_OFFSET_DEFAULT** is used in all cases (**FAST_RESYNC** on or off).
* **FAST_RESYNC** &mdash; (boolean value, "on" or "off", default: "off") if enabled, all STB's targeted to the flag will agressively adjust playback possiton to maintain the specified *Target Live Offset*.  This value must be per-account in the service (only boxes in hospitality setting will enable it)

If the feature is turned on (**FAST_RESYNC** is "on") the player will:

1. Set aggressive adjustment speed limits (+/- 20% of nominal 1x playback speed) for playback to affect live offset adjustment to match the *Target Live Offset*
2. Select the AAC (PCM audio track, only PCM audio is supported for the feature)
3. Determine when the playback position is not at the target live offset and adjust speed gradually, within the speed limits, until the position reaches live.

In practice, after a channel change it takes a few minutes to reach the Target Live Offset.  Once reached playback stays within +/- 10 - 15ms of the target offset.  Stalls will also trigger re-adjustment and may take longer to recover then a channel change (at most the duration of the stall, in practice 10 - 20% less)

The two settings for *Target Live Offset* (**_DEFAULT** and **_SYNC**) can be different to accommodate use cases like:

1. Setting the **_SYNC** value to greater than the **_DEFAULT** may be done to reduce stalling in the hospitality situation 
2. The  **_SYNC** may be set to smaller value then the **_DEFAULT** value, in the case the hospitality setting has a controlled network and want's to prioritize smaller offset from actual live.

The Live Playback Startup section below explains how these *Target Live Offset* settings affect playback startup and buffering.

## Implementation Details

### Live Playback Startup

On startup of live playback the player processes the first DASH Manifest or HLS sub-playlist load to determine two key parameters.

1. *Start Offset* &mdash; the initial playhead position in the *Live window*, it is an offset from the start of the Live Window (0 to *Window Duration*).
2. *Target Live Offset* &mdash; the number of seconds behind the absolute live edge (wall-clock now) the player attempts to maintain.

Looking at the diagram below, we will explain the interdependence of these two parameters.

![LiveOffsetStartOffset](/Users/smayhew/opensource/ExoPlayer/tivo-docs/images/LiveOffsetStartOffset.png)

* ***Rsrvd Window*** (*Bufferable*) &mdash; this is the portion of the Live Window that the player can buffer (pre-fetch), this results from a *Start Offset* that is less than the *Window Duration*
* ***Un-Published*** &mdash; this is the portion of live media that the Origin Server has not yet transcoded and published to the *Live Window*.  This can (and does) vary with each *Live Window* update publised by the Origin Server 

For HLS, the interplay is two cases:

1. Explicit *Target Live Offset*
2. Implicit *Target Live Offset*

 In both cases, this relationship, as depicted in the diagram, holds true:

```java
Start Offset = (Window Duration + Latency) - Target Live Offset
```

In HLS there is no default value for *Target Live Offset*, so for case two it is computed by transforming the formula as follows:

```java
Target Live Offset = (Window Duration - Start Offset) + Latency
```

Since for our Origin Server vendors, HLS *Latency* varies on each playlist update, Live Sync requires an explicit *Target Live Offset* setting.

The basic guideline for a value is to:

Consider your *Bufferable* duration requirements, from this set:

```java
Target Live Offset = Required Bufferable + Latency
```

The Origin Server Latency is logged in the timeLineChanged event log as `now-endTime`:

```
EventLogger: timelineChanged [..., now-endTime: 11.62, endTime-position: 58.161000, liveOffset: 69.78]
```

Typical *Latency* values are:

- Velocix &mdash; 8 - 10 seconds (fairly constant)
- Vecima &mdash; 8 - 15 seconds (jumps around)

### Live Offset Management

The diagram and following descriptions explains times and offsets that are involved in live offset adjustment

![LiveOffsetAdjustment](/Users/smayhew/opensource/ExoPlayer/tivo-docs/images/LiveOffsetAdjustment.png)

The two actors in playback are the Origin Server and the Player.  Their actions, in the context of the diagram are described in the next two sections.

#### Origin Server Actions

The origin server publishes a rolling live playlist, of fixed duration (*Window Duration*), and with an origin server calculated Window Start Time (for HLS this is the `#EXT-X-PROGRAM-DATE-TIME`).   This implies a *Window End Time*, as simply `Window Start Time + Window Duration`.  Every window update moves the *Window Start Time* (and implicitly, the *Window End Time*) one segment (typically 5 - 6 seconds) forward in time.

#### Player Action

The player maintains *Playback Position*, this moves forward as long as there is media (*Unplayed*) to consume.  If *Unplayed* falls to 0 seconds, a stall occurs and obviously the *Playback Position* freezes.

*Current Wall-Clock Time* of course moves forward as the earth rotates, specifically it is either synchronized to an NTP server, for HLS, or using the mechanism from the [UTCTiming](https://dashif-documents.azurewebsites.net/Guidelines-TimingModel/master/Guidelines-TimingModel.html#clock-sync) element for DASH

When the player receives the each rolling live playlist from the origin server it calculates the origin latency (*Un-published*) as simply `Window End Time - Current Wall-Clock Time`, this is media content that is somewhere in the media pipeline from the source to the origin and/or buffered in the origin itself.   For example, for Vecima origin this figure is typically 10 - 12 seconds.

The player also calculates *Current Live Offset*,  this is quite simplly the formula:

```java
Current Live Offset = Current Wall-Clock TIme - (Window Start Time + Playback Position)
```

#### Player Live Offset Management

The *Target Live Offset* is specified via the service setting or from the DASH manifest.  The value must leave enough *Unplayed* window so the player can establish and maintain adequate buffer.

The player simply monitors the *Current Live Offset* and if this deviates from the *Target Live Offset* playback speed is increased or decreased until the target is reached.

## Target Live Offset Setup

### DASH

For DASH with low-latency support the *Target Live Offset* service parameter can be implemented to instruct the origin to specify the latency value in the `ServiceDescription` element in DASH manifest, example:

```xml
	<ServiceDescription id="0">
		<Latency target="3000" referenceId="7"/>
	</ServiceDescription>
```

Or from the **MPD@suggestedPresentationDelay**.   Either of these values is used by the player as *Target Live Offset*

A full example:

```xml
<ServiceDescription id="0">
  <Scope schemeIdUri="urn:dvb:dash:lowlatency:scope:2019" value="2"/>
  <Latency min="4800" max="34800" target="6800" referenceId="7"/> 
  <PlaybackRate min="0.96" max="1.04"/>
</ServiceDescription>

```

Alternately the `MPD@suggestedPresentationDelay` sets the target live offset.

### HLS

HLS has no mechanism for setting a *Target Live Offset* in the media, so it must be set via the service parameters.

