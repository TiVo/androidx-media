package com.tivo.exoplayer.library.tracks;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities.AdaptiveSupport;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.common.primitives.Ints;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class StressBasedTrackSelector extends DefaultTrackSelector {

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({PLAYER_STRESS_NONE, PLAYER_STRESS_MODERATE, PLAYER_STRESS_SEVERE, PLAYER_STRESS_CRITICAL})
  public @interface PlayerStressLevel {}
  public static final int PLAYER_STRESS_NONE = 0;
  public static final int PLAYER_STRESS_MODERATE = 1;
  public static final int PLAYER_STRESS_SEVERE = 2;
  public static final int PLAYER_STRESS_CRITICAL = 3;

  @PlayerStressLevel private int currentStressLevel = PLAYER_STRESS_NONE;

  public StressBasedTrackSelector(Context context) {
    super(context);
  }

  public StressBasedTrackSelector(Context context, TrackSelection.Factory trackSelectionFactory) {
    super(context, trackSelectionFactory);
  }

  public StressBasedTrackSelector(Parameters parameters, TrackSelection.Factory trackSelectionFactory) {
    super(parameters, trackSelectionFactory);
  }

  /**
   * Gets the current stress level
   * @return the current stress level
   */
  @PlayerStressLevel int getCurrentStressLevel() {
    return currentStressLevel;
  }

  /**
   * Sets the current stress level
   * @param currentStressLevel the current stress level
   */
  public void setCurrentStressLevel(@PlayerStressLevel int currentStressLevel) {
    this.currentStressLevel = currentStressLevel;
  }

  @Override
  @Nullable
  protected TrackSelection.Definition selectVideoTrack(
          TrackGroupArray groups,
          @Capabilities int[][] formatSupport,
          @AdaptiveSupport int mixedMimeTypeAdaptationSupports,
          Parameters params,
          boolean enableAdaptiveTrackSelection)
          throws ExoPlaybackException {

    TrackSelection.Definition definition = super.selectVideoTrack(
            groups,
            formatSupport,
            mixedMimeTypeAdaptationSupports,
            params,
            enableAdaptiveTrackSelection);

    if (definition == null) {
      return null;
    }

    List<Integer> filteredTracks = new ArrayList<>();

    switch (currentStressLevel) {
      case PLAYER_STRESS_NONE:
        return definition;
      case PLAYER_STRESS_MODERATE:
        // Filter Content over 2k @ 60fps (allows 4k@30fps)
        for (int track : definition.tracks) {
          Format format = definition.group.getFormat(track);
          if (format.frameRate <= 30 || (format.width <= 2560 && format.height <= 1440)) {
            filteredTracks.add(track);
          }
        }
        break;
      case PLAYER_STRESS_SEVERE:
        // Filter Content over 1080p @ 60fps (allows 4k@30fps and 2k@30fps)
        for (int track : definition.tracks) {
          Format format = definition.group.getFormat(track);
          if (format.frameRate <= 30 || (format.width <= 1920 && format.height <= 1080)) {
            filteredTracks.add(track);
          }
        }
        break;
      case PLAYER_STRESS_CRITICAL:
        // Filter Content over 30fps
        for (int track : definition.tracks) {
          Format format = definition.group.getFormat(track);
          if (format.frameRate <= 30) {
            filteredTracks.add(track);
          }
        }
        break;
    }

    // If filtering leaves us with nothing, we are better off doing nothing
    if (filteredTracks.size() == 0) {
      return definition;
    }

    return new TrackSelection.Definition(definition.group, Ints.toArray(filteredTracks), definition.reason, definition.data);
  }

}
