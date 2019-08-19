package com.google.android.exoplayer2.trickplay;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.util.Log;

public class AdaptiveLoadControl implements LoadControl, TrickPlayEventListener {

  private final TrickPlayControlInternal trickPlayController;
  private final LoadControl delegate;

  public AdaptiveLoadControl(TrickPlayControlInternal controller, LoadControl delegate) {
    trickPlayController = controller;
    this.delegate = delegate;
    trickPlayController.addEventListenerInternal(this);
  }

  @Override
  public void onPrepared() {
    delegate.onPrepared();
  }

  @Override
  public void onTracksSelected(Renderer[] renderers, TrackGroupArray trackGroups,
      TrackSelectionArray trackSelections) {
    delegate.onTracksSelected(renderers, trackGroups, trackSelections);
  }

  @Override
  public void onStopped() {
    delegate.onStopped();
  }

  @Override
  public void onReleased() {
    delegate.onReleased();
  }

  @Override
  public Allocator getAllocator() {
    return delegate.getAllocator();
  }

  @Override
  public long getBackBufferDurationUs() {
    TrickPlayControl.TrickMode mode = trickPlayController.getCurrentTrickMode();
    switch (mode) {
      case NORMAL:
        return delegate.getBackBufferDurationUs();

      default:    // TOOD might be more cases for this..
        return 0;
    }
  }

  @Override
  public boolean retainBackBufferFromKeyframe() {
    return delegate.retainBackBufferFromKeyframe();
  }

  @Override
  public boolean shouldContinueLoading(long bufferedDurationUs, float playbackSpeed) {
    boolean minForIframePlayback = bufferedDurationUs < 2 * 1000 * 1000;
    boolean shouldContinue = delegate.shouldContinueLoading(bufferedDurationUs, 1.0f);

    if (shouldContinue) {
      Log.d("TRICK-PLAY", "shouldContinueLoading -  speed: " + playbackSpeed + " buffer ms: " + C.usToMs(bufferedDurationUs));
    }
    return shouldContinue;
  }

  @Override
  public boolean shouldStartPlayback(long bufferedDurationUs, float playbackSpeed, boolean rebuffering) {
    boolean defaultShouldStart = delegate.shouldStartPlayback(bufferedDurationUs, 1.0f, rebuffering);

    if (! defaultShouldStart) {
      Log.d("TRICK-PLAY", "shouldStartPlayback false - speed: " + playbackSpeed + " buffered: " + bufferedDurationUs + " rebuffer: " + rebuffering);
    }

    return defaultShouldStart;
  }

  @Override
  public void smoothPlayStateChanged(boolean isSmoothPlaySupported) {

  }

  @Override
  public void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode) {

  }
}
