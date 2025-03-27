package com.tivo.exoplayer.library.multiview;

import static java.util.Arrays.sort;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.Log;
import com.google.common.primitives.Ints;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Manages ExoPlayer selected tracks for the players in the multiview set.
 *
 * <p>The selection includes:</p>
 * <ol>
 *   <li>Audio tracks - constrained as required for sync video, and possible restrict to non-pass thru to allow
 *   volume control to manage focus</li>
 *   <li>Video tracks - constrained to the lowest bandwidth track that is closest to the optimal video size</li>
 * </ol>
 */
class MultiViewTrackSelector extends SyncVideoTrackSelector {
  public static final String TAG = "MultiViewTrackSelector";

  // Default frame rate to use if not specified in the format
  private static float DEFAULT_FRAME_RATE = 30.0f;

  // Default bitrate to use if not specified in the format (note, in practice this should not be possible
  private static int DEFAULT_BITRATE = 3_000_000;

  // Minimum number of tracks to keep, if all tracks do not meet the optimal video size
  private int minTracksToKeep = 2;

  private @Nullable OptimalVideoSize optimalVideoSize;

  private @Nullable GridLocation gridLocation;

  /**
   * Constructor with user specified minimum number of tracks to keep.
   *
   * <p>The MultiViewTrackSelector optimizes the set of video and audio tracks to be selected by the player for
   * use in a MultiView set.  The video tracks are selected based on the optimal video size, and the audio tracks are set to
   * prefer PCM audio formats for sync video playback.  The track selection is further constrained to keep a minimum number of tracks
   * no matter what the optimal video size is.</p>
   *
   * <p>The goals are to:</p>
   * <ol>
   *   <li>Attempt to keep the bandwidth under 1/4 the bandwidth for regular full screen playback</li>
   *   <li>Limit the peak bitrate/framerate to a "reasonable" target to not overload the decoders</li>
   * </ol>
   * @param context android context
   * @param trackSelectionFactory factory for track selection
   */
  public MultiViewTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
    super(context, trackSelectionFactory);
  }

  public static boolean isSupportedAudioFormatForVolumeMute(Format audioFormat) {
    return audioFormat != null && isSupportedAudioFormatForSyncVideo(audioFormat);
  }


  /**
   * Set the minimum number of tracks to keep if no tracks meet the optimal video size.
   *
   * @param minTracksToKeep the minimum number of tracks to keep, default is 2
   */
  synchronized void setMinTracksToKeep(int minTracksToKeep) {
    this.minTracksToKeep = minTracksToKeep;
  }

  /**
   * Set the optimal video size to use for track selection.  The caller will
   * need to call this method once the Android View that will contain the
   * {@link com.google.android.exoplayer2.ui.PlayerView} is measured and the
   * size is determined.
   *
   * @param optimalVideoSize the optimal video size to use for track selection
   */
  synchronized void setOptimalVideoSize(OptimalVideoSize optimalVideoSize) {
    this.optimalVideoSize = optimalVideoSize;
  }

  /**
   * Used for debug logging to identify the grid location of the player
   *
   * @param gridLocation the grid location of the player
   */
  synchronized void setGridLocation(@Nullable GridLocation gridLocation) {
    this.gridLocation = gridLocation;
  }

  @Nullable
  @Override
  protected ExoTrackSelection.Definition selectVideoTrack(@NonNull TrackGroupArray groups, @NonNull int[][] formatSupport,
      int mixedMimeTypeAdaptationSupports, @NonNull Parameters params, boolean enableAdaptiveTrackSelection) throws ExoPlaybackException {
    ExoTrackSelection.Definition definition = super.selectVideoTrack(groups, formatSupport, mixedMimeTypeAdaptationSupports, params,
        enableAdaptiveTrackSelection);

    if (definition != null) {
      List<Integer> filteredTrackIndices = filterAndSortTracks(definition);

      // Filter out tracks that do not meet the optimal video size
      List<Integer> optimalTrackIndexes =  new ArrayList<>(filteredTrackIndices);
      Iterator<Integer> iterator = optimalTrackIndexes.iterator();
      while (iterator.hasNext()) {
        Format format = definition.group.getFormat(iterator.next());
        OptimalVideoSize optimalVideoSize = getOptimalVideoSize();
        if (optimalVideoSize != null && !optimalVideoSize.meetsOptimalSize(format)) {
          iterator.remove();
        }
      }

      // If no tracks meet the optimal video size, keep the minimum number of tracks
      int minTracksToKeep = getMinTracksToKeep();
      if (optimalTrackIndexes.isEmpty() && filteredTrackIndices.size() > minTracksToKeep) {
        Log.w(TAG, "No tracks meet the optimal video size, keeping minimum number of tracks: " + minTracksToKeep);
        definition = new ExoTrackSelection.Definition(definition.group, Ints.toArray(filteredTrackIndices.subList(0, minTracksToKeep)), definition.type);
      } else if (optimalTrackIndexes.isEmpty()) {
        Log.d(TAG, "No tracks meet the optimal video size and the filtered track count is less than minTracksToKeep: " + minTracksToKeep);
        definition = new ExoTrackSelection.Definition(definition.group, Ints.toArray(filteredTrackIndices), definition.type);
      } else {
        Log.d(TAG, "Optimal video size met by tracks: " + optimalTrackIndexes.size() + " tracks selected");
        definition = new ExoTrackSelection.Definition(definition.group, Ints.toArray(optimalTrackIndexes), definition.type);
      }
    }

    for (int i = 0; i < definition.tracks.length; i++) {
      Format format = definition.group.getFormat(definition.tracks[i]);
      Log.d(TAG, "GridLocation: " + getGridLocation() + " video track " + i + " - " + Format.toLogString(format));
    }

    return definition;
  }

  /**
   * Filters out trick play tracks and sort the remainder by "decoder load" ( = Width*Height*FrameRate)
   * followed by bandwidth (bitrate is close proxy)
   *
   * @param definition the track selection definition, which includes the group of tracks and the selected tracks
   * @return the list of track indices that are not trick play tracks, sorted by decoder load and bandwidth
   */
  @NonNull
  @VisibleForTesting
  static List<Integer> filterAndSortTracks(ExoTrackSelection.Definition definition) {
    List<Integer> filteredTrackIndices = new ArrayList<>(Ints.asList(definition.tracks));

    // Clean up the list of tracks to remove trick play tracks
    Iterator<Integer> iterator = filteredTrackIndices.iterator();
    while (iterator.hasNext()) {
      Format format = definition.group.getFormat(iterator.next());
      if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) == C.ROLE_FLAG_TRICK_PLAY) {
        iterator.remove();
      }
    }

    // Sort the remaining tracks by decoder load (width*height*frameRate) and then by bandwidth
    Collections.sort(filteredTrackIndices, (element1, element2) -> {
      Format format1 = definition.group.getFormat(element1);
      Format format2 = definition.group.getFormat(element2);
      float decoderLoad1 = format1.width * format1.height * (format1.frameRate == Format.NO_VALUE ? DEFAULT_FRAME_RATE : format1.frameRate);
      float decoderLoad2 = format2.width * format2.height * (format2.frameRate == Format.NO_VALUE ? DEFAULT_FRAME_RATE : format2.frameRate);
      int bandwidth1 = format1.bitrate == Format.NO_VALUE ? DEFAULT_BITRATE : format1.bitrate;
      int bandwidth2 = format2.bitrate == Format.NO_VALUE ? DEFAULT_BITRATE : format2.bitrate;
      int decoderLoadCompare = Float.compare(decoderLoad1, decoderLoad2);
      return decoderLoadCompare == 0 ? Integer.compare(bandwidth1, bandwidth2) : decoderLoadCompare;
    });


    return filteredTrackIndices;
  }

  @Nullable
  synchronized private OptimalVideoSize getOptimalVideoSize() {
    return optimalVideoSize;
  }

  synchronized private int getMinTracksToKeep() {
    return minTracksToKeep;
  }

  synchronized private @Nullable GridLocation getGridLocation() {
    return gridLocation;
  }
}
