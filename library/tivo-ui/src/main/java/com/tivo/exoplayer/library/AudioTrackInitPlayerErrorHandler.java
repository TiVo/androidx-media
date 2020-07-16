package com.tivo.exoplayer.library;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;

public class AudioTrackInitPlayerErrorHandler implements DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery {

  private final PlayerErrorRecoverable playerErrorRecoverable;

  /**
   * Number of attempts to recover from an error, reset when {@link #recoveryComplete()} is called or
   * playback error is determined to be fatal (retries exceeded)
   */
  int errorRetryCount;

  public AudioTrackInitPlayerErrorHandler(PlayerErrorRecoverable playerErrorRecoverable) {
    this.playerErrorRecoverable = playerErrorRecoverable;
  }

  @Override
  public void recoveryComplete() {
    if (errorRetryCount > 0) {
      Log.d(SimpleExoPlayerFactory.TAG,
          "recovery completed, resetting error retry count from " + errorRetryCount + " to zero");
      errorRetryCount = 0;
    }
  }

  @Override
  public boolean recoverFrom(ExoPlaybackException e) {
    boolean handled = false;

    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception renderException = e.getRendererException();
      if (renderException instanceof AudioSink.InitializationException) {
        AudioSink.InitializationException initException = (AudioSink.InitializationException) renderException;

        if (initException.audioTrackState == 0 && e.rendererFormat != null && Util
            .areEqual(e.rendererFormat.sampleMimeType, "audio/ac3")) {
          if (++errorRetryCount > 3) {
            Log.w(SimpleExoPlayerFactory.TAG,
                "Retry count exceeded, failing for AudioSink.InitializationException for AC3 audio.",
                initException);
          } else {
            Log.w(SimpleExoPlayerFactory.TAG,
                "Attempting to recover AudioSink.InitializationException for AC3 audio. retry count: "
                    + errorRetryCount, initException);
            if (playerErrorRecoverable.isTunnelingMode()) {
              playerErrorRecoverable.setTunnelingMode(false);
            }
            playerErrorRecoverable.retryPlayback();
            handled = true;
          }
        }
      }
    }

    return handled;
  }
}