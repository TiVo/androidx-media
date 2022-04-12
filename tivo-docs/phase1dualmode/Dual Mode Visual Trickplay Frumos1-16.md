# Dual Mode Visual Trickplay

Dual Mode Visual Trick-Play is a method to optimize the Visual Trick-Play (VTP) user experience for streaming video delivery.  This document provides an overview of the patent applied for method, describes the implementation for the Frumos 1-16 release and describes a future roadmap for a full Dual Mode VTP implementation.

## Overview

The "Dual Mode" solution described in the patent describes the two modes as well as methods for curating the I-Frame only playlist.  The curation portion alone is useful for supporting iFrame only playlist adaption in scan mode, this portion alone is used for the initial customer delivery in Frumos 1-16.  It helps to understand the entire picture, the sections below do this.

### What is "Dual Mode"?

Dual Mode is switching between two densities of iFrame only playlists based on user interaction to facility quickly and accurately locating a program section.

##### The Problem

1. The single, high frame density, constant frame rate HLS I-Frame only playlist does not facilitate very high speed visual trick-play to scan quickly through a long program, but does allow very accurate location of a program transition
2. Skipping frames from the dense I-Frame only playlist does not provide a realistic *fast forward* experience, but can be fetched and played very efficiently over WAN.


##### Solution

Dual Mode VTP introduces a method that:

1. Curates the single high frame density I-Frame only playlist to produce a less dense variant (the *Scan Mode* playlist[s])
2. Adapts between the these two playlist based on user interaction

The Dual Modes are:

1. Scrub Mode &mdash; allows fine grained frame level stepping with user input (scrub bar, track pad, remote keys, etc) 
2. Scan Mode &mdash; automatically plays selected frames in high speed forward or reverse at a moderate frame rate (3 - 6 FPS).  The traditional TiVo trick-play.

The patent describes multiple methods for curating the high frame density playlist to produce the *Scan Mode* playlist.  These methods aim to cull the list to only interesting frames (scene changes, etc.) with less weighting on regular frame duration intervals.  The idea is that *Scan Mode* gets you very close to the frame you want to start on and *Scrub Mode* allows you to locate the exact frame.

The solution switches between the two modes seamlessly based on user interaction.

## Frumos 1-16 Implementation

### Changes From 1-15

Frumos 1-16 includes an new fast-forward mechanism and fixes the low frame rate issues with rewind.  Specifically:

1. Incorporates dual-mode fast forward, allowing buffering tailored to the speed/frame rate
2. Optimize the performance of rewind to produce 2-4 frames per second rewind speed, even with somewhat  > 100ms iFrame download times.
3. Rewind performance degrades to lower frame rates when faced with higher download times

### Dual Mode VTP in Frumos 1-16

This first release of Dual Mode for Frumos supports only traditional TiVo Visual Trick-Play forward and reverse using only "Scan Mode" of VTP

The I-Frame curation algorithm for *Scan Mode* playback provides a better experience forward VTP.  allowing the normal forward buffering of samples to match to a display frame rate for the selected scan speed.

#### Reverse Trick-Play

Reverse playback issues repeated seek operations in paused state, using the i-Frame only track, in order to move backwards in the timeline.  In paused state, ExoPlayer renders "first frame" after each seek operation.  

Reverse playback implements these 3 playback speeds and target frame rates:

| Speed | Target Frame Rate |
| ----- | ----------------- |
| -15x  | 3                 |
| -30x  | 4                 |
| -60x  | 5                 |

Reverse uses the highest density original source i-Frame track and sets target frame rate by timing the seek operations.   Frame rate may deviate sigificantly from these targets if:

1. The iFrame only track has a lower then 0.33 FPS native frame rate
2. iFrame download times  exceed 150ms
3. The Android platform's video CODEC fails to render frames (frame drops)

Because reverse playback does not buffer iFrame downloads long download latencies simply result in lower displayed frame-rates. 

#### Forward Trick-Play

Forward playback turns off the audio track and plays video only at the requested fast playback speed.  The load source for frames is either the original source iFrame only track or one of the curated tracks.  The selected track is dynamically adjusted via dynamic frame rate adaptation, or AFR.  AFR is like traditional Adaptive Bit Rate algorithms with one key difference, it criteria is frame rate targets not video quality.  Like ABR, the selection is  based on the clients observed network performance.

 The algorithm:

1. Buffers agressively to match the download speed and target frame-rate (e.g. the 50second buffer target becomes 1,500 seconds at 30x)
2. AFR adjusts to select iFrame track based on playback speed changes (to set target frame rate)
3. AFR down shifts iFrame track based on bandwidth changes

Forward playback implements these playback speeds, frame rates are expected values assuming:

1. Playback starts in 15x speed and progresses up through the speeds
2. The player is able to fully buffer.

Target Frame Rate for forward mode is somewhat faster then rewind by design (UX dictates humans are confused by high frame rates more easily in reverse playback mode).   The possible Target Frame Rate values are a function of the source iFrame only track's frame rate.   Assuming 2 second iFrame duration source (0.5 FPS frame rate), the following targets are set:



| Speed | Target Frame Rate |
| ----- | ----------------- |
| 15x   | 4                 |
| 30x   | 8                 |
| 60x   | 10                |



### Tunneling Mode

T.B.S. - comming in Frumos 1-17

### Understanding and Tuning Performance

The following playback metrics are collected and logged at each speed transition in a trick-play session, that is metrics only cover a single speed. They are key to understanding the performance of trick-play and as input for the tuning parameters.  The log is a single logcat line:

```
I TrickPlayMetricsHelper: trick-play end stats: <json metrics>
```

These logs are also captured and reported as OI data.

#### User Experience Metrics

First we have the user experience metrics. These are the values most critical and visible to the end user. The metrics are:



| **UX Metric**            | **Description**                |
| ------------------------- | --------------------------- |
|initialPlaybackStartDelay|This tells how long it takes from exiting normal playback till trick-play begins basically time to first displayed trick-play frame.|
|observedTrickPlaySpeed   | The actual multiple of real-time experienced by the viewer, if this is 30.0 then playback is 30x real-time |
|expectedTrickPlaySpeed    | The multiple of real-time set by the trick-play mode, this is fixed for the session. The closer this is to observedTrickPlaySpeed the more accurate the experience is to what was designed. |
|avgFramesPerSecond        | User experienced frame-rate, the arithmetic mean of the frames per-second over the duration of the trick-play session. |

#### Base Metrics

Next up the base metrics. The user experience metrics are derivative values from the base metrics, that is base metrics factors cause the changes in user experience. These metrics include:

| **Base Metric**   | ** Description** |
|---------------------------------------------------|-------------|
|avgBandwidthMbps            |Measured bandwidth of the trick-play frame downloads, the trend for this over time is a direct indication of how good the network is to the client. |
|medianFrameLoadTime         |Average (median value) of the observed I-Frame download times in milli-seconds. |
|arithmeticMeanFrameLoadTime |Average (mean value) of the observed I-Frame download times in milli-seconds. If the mean value differs significantly from the median then there are outliers caused by bursts of poor network performance. |
|totalRebufferingTimeMs      |This is buffering after the initial startup delay. Any buffering during forward trick-play degrades the experience and will result in lower observedTrickPlaySpeed values. |
|videoFramesDropped          |For forward playback this can result from platform limitations or more significant re-buffering causing seriously late frames. |
|renderedFramesCount         |Total number of frames rendered during the trick-play. The avgFramesPerSecond is basically this divided by total time |
|totalElapsedTimeMs          |The wall clock time the trick-play session ran for. This value times the observedTrickPlaySpeed is the total media time skipped. |
|currentMode                 |The trick-play mode these metrics cover, values are FF1, FF2, FF3, FR1, FR2, FR3, SCAN. It is meaningful to aggregate metrics that belong to the same mode. |

#### Rules Of Thumb

1. With ping times of < 100ms, 80k average iFrame size, and 1 second GOP interval it should be possible to sustain 60x playback. This should yield average iFrame downloads < 200ms

2. The centrality measurements of frame load time directly affect the avgFramesPerSecond. If the medianFrameLoadTime is 200ms it will not be possible to consistently achieve 5 frames per-second.

3. Forward playback aggressively buffers. If you see high totalRebufferingTimeMs values then one of these factors is at play:

   * The duration of the trick-play sessions is so short it is not possible to buffer (note buffering persists across speed changes)

   * The network has excessive delay periods, that last longer than the buffered period

   *  The resolution or compression of the selected I-Frame track is producing too large an I-Frame

4. High network loss (and the associated retransmissions) will reduce the bandwidth utilization greater than any other factor/

#### Real World Examples

Here are some metrics from real world examples.  First very low latency (TiVo CableCo11 from a FTH home network on an SEI500).   Forward mode:

```JavaScript
{
  "arithmeticMeanFrameLoadTime": 49.86207,
  "avgAudioBitrate": -1e-06,
  "avgBandwidthMbps": 28.215132,
  "avgFramesPerSecond": 3.5423017,
  "avgVideoBitrate": 1.05,
  "currentMode": "FF1",
  "expectedTrickPlaySpeed": 15,
  "initialPlaybackStartDelay": 265,
  "medianFrameLoadTime": 37,
  "observedTrickPlaySpeed": 14.015405,
  "prevMode": "NORMAL",
  "profileShiftCount": 1,
  "rebufferCount": 0,
  "renderedFramesCount": 43,
  "timeInFormats": {
    "iFrame_2 - 960x540@1050000": 11873
  },
  "totalCanceledLoadCount": 0,
  "totalElapsedTimeMs": 12139,
  "totalSeekCount": 0,
  "totalSeekTimeMs": 0,
  "videoFramesDropped": 0
}
```



And reverse:

```javascript
{
  "arithmeticMeanFrameLoadTime": 80.38461,
  "avgAudioBitrate": -1e-06,
  "avgBandwidthMbps": 39.71657,
  "avgFramesPerSecond": 2.787307,
  "avgVideoBitrate": -1e-06,
  "currentMode": "FR1",
  "expectedTrickPlaySpeed": -15,
  "initialPlaybackStartDelay": 200,
  "medianFrameLoadTime": 67,
  "observedTrickPlaySpeed": -14.873284,
  "prevMode": "NORMAL",
  "profileShiftCount": 1,
  "rebufferCount": 1,
  "renderedFramesCount": 26,
  "timeInFormats": {
    "iFrame_org - 720x480@1800000": 55
  },
  "totalCanceledLoadCount": 0,
  "totalElapsedTimeMs": 9328,
  "totalSeekCount": 1,
  "totalSeekTimeMs": 8992,
  "videoFramesDropped": 0
}
```





And second, from an cross US coast (75ms ping time) source, nearly full achieving the target playback speed and frame rates.  First forward:

```` javascript
{
  "arithmeticMeanFrameLoadTime": 82.79775,
  "avgAudioBitrate": -1e-06,
  "avgBandwidthMbps": 13.623744,
  "avgFramesPerSecond": 3.0947776,
  "avgVideoBitrate": 0.3,
  "currentMode": "FF1",
  "expectedTrickPlaySpeed": 15,
  "initialPlaybackStartDelay": 812,
  "medianFrameLoadTime": 81,
  "observedTrickPlaySpeed": 12.481238,
  "prevMode": "NORMAL",
  "profileShiftCount": 1,
  "rebufferCount": 0,
  "renderedFramesCount": 24,
  "timeInFormats": {
    "iFrame_2 - 640x360@300000": 6943
  },
  "totalCanceledLoadCount": 0,
  "totalElapsedTimeMs": 7755,
  "totalSeekCount": 0,
  "totalSeekTimeMs": 0,
  "videoFramesDropped": 0
}

````



And reverse:

```javascript
{
  "arithmeticMeanFrameLoadTime": 163.80646,
  "avgAudioBitrate": -1e-06,
  "avgBandwidthMbps": 30.73071,
  "avgFramesPerSecond": 2.8126757,
  "avgVideoBitrate": -1e-06,
  "currentMode": "FR1",
  "expectedTrickPlaySpeed": -15,
  "initialPlaybackStartDelay": 538,
  "medianFrameLoadTime": 163,
  "observedTrickPlaySpeed": -14.819427,
  "prevMode": "NORMAL",
  "profileShiftCount": 1,
  "rebufferCount": 1,
  "renderedFramesCount": 30,
  "timeInFormats": {
    "iFrame_org - 640x360@600000": 64
  },
  "totalCanceledLoadCount": 0,
  "totalElapsedTimeMs": 10666,
  "totalSeekCount": 1,
  "totalSeekTimeMs": 10333,
  "videoFramesDropped": 0
}
```



## Known Issues

These know issues are considered S3 bugs by QE, it is very possible these will be fixed post FA before acutal ship of Frumos 1-16 Dual Mode VTP:

1. Frame rate transitions in forward mode do not follow smoothly with speed changes
2. Forward trickplay does not have the ability to cancel stuck segment loads

## Future Roadmap 

The next followon efforts will address these features

Phase 3 - Faster Transitions and support for tunneling mode

1. *switch into iFrame mode without rebuffering* &mdash; the track switch to the iFrame only track will be seemless, using existing buffered content
2. *support for tunneled mode trickplay* &mdash; Broadcom platforms require tunneled mode for 4k playback.  Trickplay will accomidate this gracefully switching into tunneled trickplay mode.

Phase 4 - Full Dual Mode with Scrub and Scan mode

1. *implement Scrub in Hydra* &mdash; using the D-Pad to scrub frames step by step.
2. *Updated UX in Hydra* &mdash; using the D-Pad only to control major trickplay functions.
