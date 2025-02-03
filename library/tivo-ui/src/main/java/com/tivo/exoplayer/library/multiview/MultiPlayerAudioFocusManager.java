package com.tivo.exoplayer.library.multiview;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.util.Log;

/**
 * Manages audio focus for multiple players.
 * 
 * <p>To uses this class, create an instance and call {@link #setSelectedPlayer(SimpleExoPlayer)} to select the player
 * that will first play audio.  That player should start with playWhenReady false so it will not 
 * begin audio playback until focus is gained.</p>
 * 
 * <p>The stock SimpleExoPlayer audio focus manager pauses any player that loses focus, it cannot handle multiple 
 * players sharing focus in an orderly manner.  To disable SimpleExoPlayer's internal audio focus manager, set
 *  {@link SimpleExoPlayer.Builder#setHandleAudioBecomingNoisy(boolean)} to false and
 *  {@link SimpleExoPlayer.Builder#setAudioAttributes(com.google.android.exoplayer2.audio.AudioAttributes, boolean)} to DEFAULT, false.
 *  </p>
 */
public class MultiPlayerAudioFocusManager {

  public static final String TAG = "MultiPlayerAudioFocusManager";
  private final AudioManager audioManager;
  private AudioFocusRequest audioFocusRequest;
  private boolean hasAudioFocus = false;

  private @Nullable SimpleExoPlayer selectedPlayer;
  
  public MultiPlayerAudioFocusManager(Context context) {
    this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    initAudioFocusRequest();
  }

  private void initAudioFocusRequest() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
          .setAudioAttributes(new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
              .build())
          .setOnAudioFocusChangeListener(this::onAudioFocusChange)
          .build();
    }
  }

  /**
   * Set the currently selected player.  The selected player is the one that
   * will play audio, the remaining players will be "muted" (play with no audio).
   *
   * <p>Once there is no selected player, audio focus is released.</p>
   * @param selectedPlayer The player to select, or null to release audio focus.
   */
  public void setSelectedPlayer(@Nullable SimpleExoPlayer selectedPlayer) {
    this.selectedPlayer = selectedPlayer;
    if (selectedPlayer != null) {
      requestAudioFocus();
    } else {
      abandonAudioFocus();
    }
  }

  private boolean requestAudioFocus() {
    int result;
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      result = audioManager.requestAudioFocus(audioFocusRequest);
    } else {
      result = audioManager.requestAudioFocus(
          this::onAudioFocusChange,
          AudioManager.STREAM_MUSIC,
          AudioManager.AUDIOFOCUS_GAIN
      );
    }

    Log.d(TAG, "requestAudioFocus: " + result);

    hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    if (hasAudioFocus) {
      selectedPlayer.setPlayWhenReady(true);
    }
    return hasAudioFocus;
  }

  private void abandonAudioFocus() {
    Log.d(TAG, "abandonAudioFocus - hasAudioFocus: " + hasAudioFocus + " selectedPlayer: " + selectedPlayer);
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      audioManager.abandonAudioFocusRequest(audioFocusRequest);
    } else {
      audioManager.abandonAudioFocus(this::onAudioFocusChange);
    }
    hasAudioFocus = false;
    if (selectedPlayer != null) {
      selectedPlayer.setPlayWhenReady(false);
    }
  }

  private void onAudioFocusChange(int focusChange) {
    Log.d(TAG, "onAudioFocusChange: " + focusChange + ", hasAudioFocus=" + hasAudioFocus + ", selectedPlayer=" + selectedPlayer);
    if (selectedPlayer != null) {
      switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
          hasAudioFocus = true;
          selectedPlayer.setPlayWhenReady(true);
          break;

        case AudioManager.AUDIOFOCUS_LOSS:
          hasAudioFocus = false;
          selectedPlayer.setPlayWhenReady(false);
          break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
          selectedPlayer.setPlayWhenReady(false);
          break;

        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
          // Lower the volume if focus is temporarily lost but can duck
          selectedPlayer.setVolume(0.2f);
          break;

        default:
          break;
      }
    }
  }

  public boolean hasAudioFocus() {
    return hasAudioFocus;
  }
}