## Metrics Package ##

### Overview ###
The metrics package manages two metrics object, `PlaybackMetrics` and `TrickPlayMetrics`. These classes are POJO's with properties culled from the ExoPlayer Analytics `PlaybackStats`.

`PlaybackMetrics` &mdash; sampling of the `PlaybackStats` as collected for a playback session excluding activity during trick-play
`TrickPlayMetrics` &mdash; a super-set of `PlaybackMetrics` collected during a trick-play session (where a the session boundary is the duration of playback in a single `TrickMode`

### Using Metrics ###

The minimal setup creates an instance of the `Man` and initializes it.

Declare a field

````java
  private PlaybackMetricsManagerApi statsManager;
````

In the `onCreate()` or for each playback do:

````java
    statsManager = new ManagePlaybackMetrics.Builder(player, trickPlayControl)
        .setMetricsEventListener(new MetricsEventListener() {

              @Override
              public void trickPlayMetricsAvailable(TrickPlayMetrics metrics, PlaybackStats stats) {
                // Called after each trick-play mode change with the updated metrics 
              }

              @Override
              public void playbackMetricsAvailable(PlaybackMetrics playbackMetrics, String playUrl) {
                // Called when the session is ended with the updated metrics
              }
            })
        .build();
````

And in your `onStop()` and any error handlers use:

````java
       statsManager.endAllSessions();
````

This call ends the current session and calls the `playbackMetricsAvailable` callback.

Changes to the `Timeline` (channel change for example) will also end the current session and call the callback.

### Logging ###

You can choose to not implement any of the `MetricsEventListener` callbacks and the collection will still occur and log end events for each collection session. 

Examples below show the log line raw and formatted as JSON

For trick-play session end:

````js
03-15 10:47:42.962  7108  7108 I TrickPlayMetricsHelper: trick-play end stats: {"totalRebufferingTimeMs":1579,"totalElapsedTimeMs":2832,"totalCanceledLoadCount":0,"expectedTrickPlaySpeed":30,"medianFrameLoadTime":60,"avgVideoBitrate":1.3734,"renderedFramesCount":14,"endedFor":"NONE","totalPlayingTimeMs":1238,"profileShiftCount":1,"avgBandwidthMbps":30.30652,"currentMode":"FF2","prevMode":"FF1","observedTrickPlaySpeed":13.082627,"arithmeticMeanFrameLoadTime":64.17073,"initialPlaybackStartDelay":0,"rebufferCount":15,"timeInFormats":{"iFrame-0 - 1280x720@1373400":1238},"videoFramesDropped":28,"totalSeekCount":1,"totalSeekTimeMs":15,"avgFramesPerSecond":4.943503}
{
  "arithmeticMeanFrameLoadTime": 64.17073,
  "avgBandwidthMbps": 30.30652,
  "avgFramesPerSecond": 4.943503,
  "avgVideoBitrate": 1.3734,
  "currentMode": "FF2",
  "endedFor": "NONE",
  "expectedTrickPlaySpeed": 30,
  "initialPlaybackStartDelay": 0,
  "medianFrameLoadTime": 60,
  "observedTrickPlaySpeed": 13.082627,
  "prevMode": "FF1",
  "profileShiftCount": 1,
  "rebufferCount": 15,
  "renderedFramesCount": 14,
  "timeInFormats": {
    "iFrame-0 - 1280x720@1373400": 1238
  },
  "totalCanceledLoadCount": 0,
  "totalElapsedTimeMs": 2832,
  "totalPlayingTimeMs": 1238,
  "totalRebufferingTimeMs": 1579,
  "totalSeekCount": 1,
  "totalSeekTimeMs": 15,
  "videoFramesDropped": 28
}
````

And for regular playback end

````js
03-15 10:47:50.626  7108  7108 I ManagePlaybackMetrics: session end stats, URL: http://live1.nokia.tivo.com/ktvu/vxfmt=dp/playlist.m3u8?device_profile=hlsclr stats: {"totalRebufferingTimeMs":837,"totalElapsedTimeMs":62574,"avgVideoBitrate":8.562955,"endedFor":"USER_ENDED","totalPlayingTimeMs":42596,"profileShiftCount":2,"avgBandwidthMbps":51.873375,"initialPlaybackStartDelay":6442,"rebufferCount":1,"totalTimeMs":62574,"timeInFormats":{"2 - 1280x720@5060000":6184,"3 - 1280x720@8740000":36412},"videoFramesDropped":201,"trickPlayCount":3,"totalTrickPlayTimeMs":12384}
Play URL: http://live1.nokia.tivo.com/ktvu/vxfmt=dp/playlist.m3u8?device_profile=hlsclr
{
  "avgBandwidthMbps": 51.873375,
  "avgVideoBitrate": 8.562955,
  "endedFor": "USER_ENDED",
  "initialPlaybackStartDelay": 6442,
  "profileShiftCount": 2,
  "rebufferCount": 1,
  "timeInFormats": {
    "2 - 1280x720@5060000": 6184,
    "3 - 1280x720@8740000": 36412
  },
  "totalElapsedTimeMs": 62574,
  "totalPlayingTimeMs": 42596,
  "totalRebufferingTimeMs": 837,
  "totalTimeMs": 62574,
  "totalTrickPlayTimeMs": 12384,
  "trickPlayCount": 3,
  "videoFramesDropped": 201
}
````



