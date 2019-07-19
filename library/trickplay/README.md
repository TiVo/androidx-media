# ExoPlayer Trick-Play library module #

## Summary

Provides support for forward and reverse full screen high speed trick-play playback.  Higher rates are supported with HLS iFrame only playlists

## Usage

Include `library-trickplay` in your gradle build as a dependency (or download the aar file from the T.B.S. artifcatory and manually extact the class.jar)

`TrickPlayControl` is the main interface to the trickplay functionality.  

~~~java

TrickPlayControlFactory trickPlayControlFactory = new TrickPlayControlFactory();   
TrickPlayControl trickPlayControl = trickPlayControlFactory.createTrickPlayControl(trackSelector);
SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(...);
trickPlayControl.setPlayer(player);

~~~

You can now call the trickPlayControl methods after the MediaSource has been prepared (comming soon callback for changes via `TrickPlayEventListener`

The `TrackSelector` must be a `DefaultTrackSelector` instance or subclass thereof.