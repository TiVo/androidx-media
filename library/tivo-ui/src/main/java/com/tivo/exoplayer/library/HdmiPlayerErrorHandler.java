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
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.audio.AudioSink;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Monitors HDMI state and recovers from any AudioTrack write errors that occur from
 * HDMI transitions
 */
public class HdmiPlayerErrorHandler implements DefaultExoPlayerErrorHandler.PlaybackExceptionRecovery {
  private static final String TAG = "HdmiPlayerErrorHandler";

  private @MonotonicNonNull PlayerErrorRecoverable factory;
  private @MonotonicNonNull Context context;

  // True if recovered from audio track error caused by HDMI hot-plug
  // by temporarily disabling tunneling
  //
  private boolean tunnelingDisabledForRecovery;

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

            if (factory != null && tunnelingDisabledForRecovery && ! factory.isTunnelingMode() ) {
              Log.d(TAG, "HDMI plugged in, restoring tunneling");
              tunnelingDisabledForRecovery = false;
              factory.setTunnelingMode(true);
            }
            break;

          case 0:
            hdmiState = HdmiState.UNPLUGGED;
            if (factory != null) {
              Log.d(TAG, "HDMI unplugged, isTunneling: " + factory.isTunnelingMode());
            } else {
              Log.d(TAG, "HDMI unplugged, no current player");
            }
            break;

        }
      }
    }
  };

  public HdmiPlayerErrorHandler(PlayerErrorRecoverable factory, Context context) {
    this.factory = factory;
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
          if (factory.isTunnelingMode()) {
            Log.w(TAG, "Attempting to recover AudioTrack write error by disabling tunneling.");
            factory.setTunnelingMode(false);
            tunnelingDisabledForRecovery = true;
            factory.retryPlayback();
            handled = true;
          } else {
            Log.e(TAG, "Unexpected AudioTrack write error while not tunneling mode.", writeException);
          }
        }
      }
    }
    return handled;
  }

  @Override
  public void recoveryComplete() {
    if (factory != null) {
      Log.d(TAG, "recoveryComplete() called, HDMI state: " + hdmiState + " tunneling state: "
          + factory.isTunnelingMode() + " tunnelingDisabledForRecovery: " + tunnelingDisabledForRecovery);

      if (tunnelingDisabledForRecovery) {
        Log.d(TAG, "recoveryComplete() called after tunnelingDisabledForRecovery - tunneling state: "
            + factory.isTunnelingMode() + ", HDMI state: " + hdmiState);

        if (hdmiState == HdmiState.PLUGGED && ! factory.isTunnelingMode()) {
          Log.d(TAG, "recoveryComplete() called, HDMI plugged in so restarting tunneling");
          factory.setTunnelingMode(true);
          tunnelingDisabledForRecovery = false;
        }
      }
    }
  }

  public void releaseResources() {
    context.unregisterReceiver(hdmiHotPlugReceiver);
    factory = null;
    context = null;
  }
}
