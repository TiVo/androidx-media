// Copyright 2020 TiVo Inc.  All rights reserved.
package com.tivo.exoplayer.library;

import static android.content.Context.WINDOW_SERVICE;
import static android.media.AudioManager.ACTION_HDMI_AUDIO_PLUG;
import static android.media.AudioManager.EXTRA_AUDIO_PLUG_STATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioSink;
import com.tivo.exoplayer.library.PlayerErrorRecoverable;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Monitors HDMI state and recovers from any AudioTrack write errors that occur from
 * HDMI transitions
 */
public class HdmiPlayerErrorHandler implements PlaybackExceptionRecovery {
  private static final String TAG = "HdmiPlayerErrorHandler";

  private @MonotonicNonNull PlayerErrorRecoverable errorRecoverable;
  private @MonotonicNonNull Context context;

  // True if recovered from audio track error caused by HDMI hot-plug
  // by temporarily disabling tunneling
  //
  private boolean tunnelingDisabledForRecovery;
  private @Nullable ExoPlaybackException currentError;

  // Current HDMI state
  private enum HdmiState {UNKNOWN, UNPLUGGED, PLUGGED};
  private HdmiState hdmiState = HdmiState.UNKNOWN;

  private final BroadcastReceiver hdmiHotPlugReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {

      String action = intent.getAction();
      if (ACTION_HDMI_AUDIO_PLUG.equals(action)) {
        int plugState = intent.getIntExtra(EXTRA_AUDIO_PLUG_STATE, -1);
        switch (plugState) {
          case 1:
            hdmiState = HdmiState.PLUGGED;
            Log.d(TAG, "HDMI Hotplug - plugged in - hdmiRemovedWhileTunneling: " + tunnelingDisabledForRecovery);
            WindowManager windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
            int displayFlags;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
              displayFlags = windowManager != null ? windowManager.getDefaultDisplay().getFlags() : 0;
              if ((displayFlags & (Display.FLAG_SECURE | Display.FLAG_SUPPORTS_PROTECTED_BUFFERS)) !=
                  ((Display.FLAG_SECURE | Display.FLAG_SUPPORTS_PROTECTED_BUFFERS))) {
                Log.d(TAG, "Insecure display plugged in");
              } else {
                Log.d(TAG, "Secure display plugged in - flags: " + displayFlags);
              }
            } else {
              Log.d(TAG, "Display security un-known for build version < JELLY_BEAN");
            }

            if (errorRecoverable != null && tunnelingDisabledForRecovery && ! errorRecoverable.isTunnelingMode() ) {
              Log.d(TAG, "HDMI plugged in, restoring tunneling");
              tunnelingDisabledForRecovery = false;
              errorRecoverable.setTunnelingMode(true);
            }
            break;

          case 0:
            hdmiState = HdmiState.UNPLUGGED;
            if (errorRecoverable != null) {
              Log.d(TAG, "HDMI unplugged, isTunneling: " + errorRecoverable.isTunnelingMode());
            } else {
              Log.d(TAG, "HDMI unplugged, no current player");
            }
            break;

        }
      }
    }
  };

  public HdmiPlayerErrorHandler(PlayerErrorRecoverable factory, Context context) {
    this.errorRecoverable = factory;
    this.context = context;
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_HDMI_AUDIO_PLUG);
    context.registerReceiver(hdmiHotPlugReceiver, filter);
  }

  @Override
  public boolean recoverFrom(ExoPlaybackException e) {
    boolean handled = false;
    if (e.type == ExoPlaybackException.TYPE_RENDERER) {
      Exception renderException = e.getRendererException();
      if (renderException instanceof AudioSink.WriteException) {

        // In tunneling mode, if the HDMI disconnects the AudioTrack fails.  Recover by
        // turning off tunneling.
        //
        AudioSink.WriteException writeException = (AudioSink.WriteException) renderException;
        if (writeException.errorCode == android.media.AudioTrack.ERROR_DEAD_OBJECT) {
          if (errorRecoverable.isTunnelingMode()) {
            Log.w(TAG, "Attempting to recover AudioTrack write error by disabling tunneling.");
            errorRecoverable.setTunnelingMode(false);
            tunnelingDisabledForRecovery = true;
            errorRecoverable.retryPlayback();
            handled = true;
          } else {
            Log.e(TAG, "Unexpected AudioTrack write error while not tunneling mode.", writeException);
          }
        }
      }
    }
    if (handled) {
      currentError = e;
    }
    return handled;
  }

  @Nullable
  @Override
  public ExoPlaybackException currentErrorBeingHandled() {
    return currentError;
  }

  @Override
  public boolean checkRecoveryCompleted() {
    updateRecoveryState();
    SimpleExoPlayer player = errorRecoverable.getCurrentPlayer();
    boolean recovered = player != null && player.isPlaying();
    if (recovered) {
      currentError = null;
    }
    return recovered;
  }

  @Override
  public boolean isRecoveryInProgress() {
    return currentError != null;
  }

  private void updateRecoveryState() {
    if (errorRecoverable != null) {
      Log.d(TAG, "updateRecoveryState() called, HDMI state: " + hdmiState + " tunneling state: "
          + errorRecoverable.isTunnelingMode() + " tunnelingDisabledForRecovery: " + tunnelingDisabledForRecovery);

      if (tunnelingDisabledForRecovery) {
        Log.d(TAG, "updateRecoveryState() called after tunnelingDisabledForRecovery - tunneling state: "
            + errorRecoverable.isTunnelingMode() + ", HDMI state: " + hdmiState);

        if (hdmiState == HdmiState.PLUGGED && ! errorRecoverable.isTunnelingMode()) {
          Log.d(TAG, "updateRecoveryState() called, HDMI plugged in so restarting tunneling");
          errorRecoverable.setTunnelingMode(true);
          tunnelingDisabledForRecovery = false;
        }
      }
    }
  }

  public void releaseResources() {
    context.unregisterReceiver(hdmiHotPlugReceiver);
    errorRecoverable = null;
    context = null;
  }
}
