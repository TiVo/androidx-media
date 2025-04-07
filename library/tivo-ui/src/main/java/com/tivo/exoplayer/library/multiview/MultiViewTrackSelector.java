package com.tivo.exoplayer.library.multiview;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.Log;
import com.google.common.primitives.Ints;
import com.tivo.exoplayer.library.tracks.SyncVideoTrackSelector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

  // Default values for track properties when not specified in the format
  private static final float DEFAULT_FRAME_RATE = 30.0f;
  private static final int DEFAULT_BITRATE = 3_000_000;

  // Quality limits for MultiView playback
  private static final float MAX_MULTIVIEW_FRAME_RATE = 30.0f;
  private static final int MAX_MULTIVIEW_BITRATE = 4_000_000;
  
  // Minimum number of tracks to keep when no tracks meet the optimal size
  private int minTracksToKeep = 1;

  private @Nullable OptimalVideoSize optimalVideoSize;
  private @Nullable GridLocation gridLocation;
  
  /**
   * Returns a dynamic tag including the grid view index when available
   */
  private String getLogTag() {
    GridLocation location = getGridLocation();
    if (location != null) {
      return TAG + "-" + location.getViewIndex();
    }
    return TAG;
  }

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
   *   <li>Limit the peak bitrate/frame rate to a "reasonable" target to not overload the decoders</li>
   *   <li>Enforce a hard limit of 30fps for frame rate and 4Mbps for bitrate</li>
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
   * @param minTracksToKeep the minimum number of tracks to keep, default is 1
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
  synchronized void setOptimalVideoSize(@Nullable OptimalVideoSize optimalVideoSize) {
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
    
    // First call the parent class implementation
    ExoTrackSelection.Definition definition = super.selectVideoTrack(groups, formatSupport, mixedMimeTypeAdaptationSupports, params,
        enableAdaptiveTrackSelection);

    if (definition == null) {
      return null;
    }

    // Step 1: Get filtered and sorted tracks
    List<Integer> filteredIndices = filterAndSortTracks(definition);
    if (filteredIndices.isEmpty()) {
      Log.w(getLogTag(), "No usable tracks after filtering out trick-play tracks");
      return definition; // Return original if no tracks after filtering
    }

    // Step 2: Limit tracks that exceed playback constraints
    filteredIndices = limitTracksExceedingPlaybackConstraints(filteredIndices, definition.group);

    // Step 3: Select tracks based on optimal size
    int[] selectedIndices = selectOptimalSizeTracks(filteredIndices, definition.group);

    // Create new definition with selected tracks
    ExoTrackSelection.Definition result = new ExoTrackSelection.Definition(
        definition.group, selectedIndices, definition.type);
    
    // Log the selected tracks for debugging
    logSelectedTracks(result);
    
    return result;
  }

  /**
   * Limit the number of tracks that exceed playback constraints (frame rate and bitrate).
   * If all tracks exceed limits, keep only minTracksToKeep lowest-resource tracks.
   */
  private List<Integer> limitTracksExceedingPlaybackConstraints(
      List<Integer> indices, TrackGroup group) {
    
    // Check if all tracks are non-compliant with playback constraints
    boolean allTracksNonCompliant = true;
    for (Integer index : indices) {
      Format format = group.getFormat(index);
      if (meetsPlaybackConstraints(format)) {
        allTracksNonCompliant = false;
        break;
      }
    }
    
    // If all tracks exceed limits, only keep minTracksToKeep
    if (allTracksNonCompliant) {
      int tracksToKeep = Math.min(indices.size(), getMinTracksToKeep());
      List<Integer> limitedTracks = indices.subList(0, tracksToKeep);
      Log.w(getLogTag(), "All tracks exceed playback constraints, keeping only " + tracksToKeep + 
          " lowest-resource track(s)");
      return new ArrayList<>(limitedTracks);
    }
    
    return indices;
  }
  
  /**
   * Checks if a format meets the playback constraints for frame rate and bitrate.
   */
  private boolean meetsPlaybackConstraints(Format format) {
    float frameRate = format.frameRate == Format.NO_VALUE ? DEFAULT_FRAME_RATE : format.frameRate;
    int bitrate = format.bitrate == Format.NO_VALUE ? DEFAULT_BITRATE : format.bitrate;
    return frameRate <= MAX_MULTIVIEW_FRAME_RATE && bitrate <= MAX_MULTIVIEW_BITRATE;
  }

  /**
   * Select tracks that meet the optimal video size constraints.
   * If no tracks meet the constraints, fall back to keeping minTracksToKeep.
   */
  private int[] selectOptimalSizeTracks(List<Integer> indices, TrackGroup group) {
    OptimalVideoSize maxVideoSize = getOptimalVideoSize();
    
    // No optimal size defined, just use all filtered tracks
    if (maxVideoSize == null) {
      Log.d(getLogTag(), "No optimal video size set, using " + indices.size() + " filtered tracks");
      return Ints.toArray(indices);
    }

    // Find tracks that meet optimal size
    List<Integer> optimalTracks = new ArrayList<>();
    for (Integer index : indices) {
      if (maxVideoSize.meetsOptimalSize(group.getFormat(index))) {
        optimalTracks.add(index);
      }
    }

    // Select final tracks
    if (!optimalTracks.isEmpty()) {
      // Found tracks that meet our target resolution
      return Ints.toArray(optimalTracks);
    } else {
      // No tracks meet optimal size, keep minimum number of lowest-resource tracks
      int tracksToKeep = Math.min(indices.size(), getMinTracksToKeep());
      int[] result = new int[tracksToKeep];
      for (int i = 0; i < tracksToKeep; i++) {
        result[i] = indices.get(i);
      }
      Log.w(getLogTag(), "No tracks meet the max video size, keeping " + 
          tracksToKeep + " lowest-resource tracks");
      return result;
    }
  }

  /**
   * Logs details about the selected tracks for debugging purposes.
   */
  private void logSelectedTracks(ExoTrackSelection.Definition definition) {
    if (definition.tracks.length == 0) {
      return;
    }
    
    StringBuilder logMsg = new StringBuilder("Selected track details:");
    
    for (int i = 0; i < definition.tracks.length; i++) {
      Format format = definition.group.getFormat(definition.tracks[i]);
      float frameRate = format.frameRate == Format.NO_VALUE ? DEFAULT_FRAME_RATE : format.frameRate;
      int bitrate = format.bitrate == Format.NO_VALUE ? DEFAULT_BITRATE : format.bitrate;
      
      boolean isHighFrameRate = frameRate > MAX_MULTIVIEW_FRAME_RATE;
      boolean isHighBitrate = bitrate > MAX_MULTIVIEW_BITRATE;
      
      // Add to debug summary log
      logMsg.append(String.format(Locale.US, "\n  Track %d: %dx%d @ %.2f fps, %d kbps",
          i, format.width, format.height, frameRate, bitrate / 1000));
      
      // Log warnings for non-compliant tracks
      if (isHighFrameRate || isHighBitrate) {
        StringBuilder warningMsg = new StringBuilder();
        warningMsg.append("Track ").append(i).append(" doesn't meet requirements: ")
                 .append(format.width).append("x").append(format.height)
                 .append(" @ ").append(String.format(Locale.US, "%.2f", frameRate)).append("fps")
                 .append(", ").append(bitrate / 1000).append(" kbps")
                 .append(" [WARNING:");
        
        if (isHighFrameRate) warningMsg.append(" HIGH FRAME RATE");
        if (isHighBitrate) warningMsg.append(isHighFrameRate ? "," : "").append(" HIGH BITRATE");
        warningMsg.append("]");
        
        Log.w(getLogTag(), warningMsg.toString());
      }
    }
    
    Log.i(getLogTag(), logMsg.toString());
  }

  /**
   * Filters out trick play tracks and sorts the remainder by "decoder load" and bandwidth.
   * Also enforces frame rate and bitrate constraints by categorizing tracks.
   *
   * @param definition the track selection definition
   * @return list of track indices sorted by priority and decoder load
   */
  @NonNull
  @VisibleForTesting
  static List<Integer> filterAndSortTracks(ExoTrackSelection.Definition definition) {
    // Step 1: Filter out trick play tracks
    List<Integer> nonTrickPlayTracks = filterOutTrickPlayTracks(definition);
    
    if (nonTrickPlayTracks.isEmpty()) {
      return nonTrickPlayTracks;
    }
    
    // Step 2: Get tracks in priority order based on quality constraints
    // Categorize and prioritize tracks based on quality constraints
    List<Integer> prioritizedTracks = categorizeAndPrioritizeTracks(nonTrickPlayTracks, definition.group);
    
    // Step 3: Sort tracks by resource usage (decoder load and bitrate)
    sortTracksByResourceUsage(prioritizedTracks, definition.group);
    
    return prioritizedTracks;
  }
  
  /**
   * Filter out trick play tracks from the definition.
   */
  private static List<Integer> filterOutTrickPlayTracks(ExoTrackSelection.Definition definition) {
    List<Integer> result = new ArrayList<>();
    for (int i = 0; i < definition.tracks.length; i++) {
      int trackIndex = definition.tracks[i];
      Format format = definition.group.getFormat(trackIndex);
      if ((format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != C.ROLE_FLAG_TRICK_PLAY) {
        result.add(trackIndex);
      }
    }
    return result;
  }

  /**
   * Categorizes tracks based on their compliance with quality constraints (frame rate and bitrate)
   * and prioritizes them for selection. The prioritization order is determined by the declaration
   * order of the {@link TrackCategory} enum, where categories declared earlier are preferred.
   *
   * @param trackIndices List of track indices to categorize and prioritize.
   * @param group The {@link TrackGroup} containing the tracks.
   * @return A list of track indices belonging to the highest-priority category.
   */
  private static List<Integer> categorizeAndPrioritizeTracks(List<Integer> trackIndices, TrackGroup group) {
    // Categorize tracks by quality constraint compliance
    Map<TrackCategory, List<Integer>> categorizedTracks = new EnumMap<>(TrackCategory.class);
    for (TrackCategory category : TrackCategory.values()) {
      categorizedTracks.put(category, new ArrayList<>());
    }
    
    for (Integer trackIndex : trackIndices) {
      Format format = group.getFormat(trackIndex);
      TrackCategory category = categorizeTrack(format);
      List<Integer> tracks = categorizedTracks.get(category);
      if (tracks != null) {
        tracks.add(trackIndex);
      }
    }
    
    // Find the highest priority category that has tracks
    for (TrackCategory category : TrackCategory.values()) {
      List<Integer> categoryTracks = categorizedTracks.get(category);
      if (categoryTracks != null && !categoryTracks.isEmpty()) {
        return categoryTracks;
      }
    }
    
    // If no category matched (shouldn't happen), return all non-trick-play tracks
    return trackIndices;
  }
  
  /**
   * Sort tracks by resource usage (decoder load then bitrate).
   * Note: Sorting by bitrate alone could be simpler and would avoid
   * floating-point comparison issues, as bitrate typically correlates 
   * with resolution and frame rate in most streams.
   */
  private static void sortTracksByResourceUsage(List<Integer> tracks, TrackGroup group) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      Collections.sort(tracks, Comparator
          .comparingDouble((Integer t) -> getDecoderLoad(group.getFormat(t)))
          .thenComparingInt(t -> {
            Format format = group.getFormat(t);
            return format.bitrate == Format.NO_VALUE ? DEFAULT_BITRATE : format.bitrate;
          }));
    }
  }

  /**
   * Calculate decoder load for a format (width × height × frame rate)
   */
  private static float getDecoderLoad(Format format) {
    return format.width * format.height * 
        (format.frameRate == Format.NO_VALUE ? DEFAULT_FRAME_RATE : format.frameRate);
  }

  @Nullable
  @VisibleForTesting
  synchronized OptimalVideoSize getOptimalVideoSize() {
    return optimalVideoSize;
  }

  // Changed from private to package-private for testing
  @VisibleForTesting
  synchronized int getMinTracksToKeep() {
    return minTracksToKeep;
  }

  @Nullable
  @VisibleForTesting
  synchronized GridLocation getGridLocation() {
    return gridLocation;
  }

  /**
   * Categorizes a track based on its frame rate and bitrate limits.
   * Boundary conditions:
   * - A track is considered to meet the frame rate limit if its frame rate is less than or equal to MAX_MULTIVIEW_FRAME_RATE.
   * - A track is considered to meet the bitrate limit if its bitrate is less than or equal to MAX_MULTIVIEW_BITRATE.
   * - If both limits are met, the track is categorized as MEETS_ALL_LIMITS.
   * - If only one limit is exceeded, the track is categorized accordingly.
   * - If both limits are exceeded, the track is categorized as EXCEEDS_BITRATE_AND_FRAME_RATE.
   */
  @NonNull
  @VisibleForTesting
  static TrackCategory categorizeTrack(Format format) {
    // Use default values if frame rate or bitrate is not specified
    float frameRate = format.frameRate == Format.NO_VALUE ? DEFAULT_FRAME_RATE : format.frameRate;
    int bitrate = format.bitrate == Format.NO_VALUE ? DEFAULT_BITRATE : format.bitrate;
    
    // Check if the track meets the defined limits
    boolean meetsBitrateLimit = bitrate <= MAX_MULTIVIEW_BITRATE; // Includes boundary condition (<=)
    boolean meetsFrameRateLimit = frameRate <= MAX_MULTIVIEW_FRAME_RATE; // Includes boundary condition (<=)
    
    // Categorize the track based on compliance with the limits
    if (meetsFrameRateLimit && meetsBitrateLimit) {
      return TrackCategory.MEETS_ALL_LIMITS;  // Meets both limits
    } else if (meetsFrameRateLimit) {
      return TrackCategory.EXCEEDS_BITRATE;  // Meets frame rate limit but exceeds bitrate limit
    } else if (meetsBitrateLimit) {
      return TrackCategory.EXCEEDS_FRAME_RATE;  // Meets bitrate limit but exceeds frame rate limit
    } else {
      return TrackCategory.EXCEEDS_BITRATE_AND_FRAME_RATE;  // Exceeds both limits
    }
  }

  /**
   * Categorization of tracks based on their compliance with limits.
   * The order of declaration here defines the priority order used for track selection.
   * TrackCategory values earlier in the declaration are preferred over later ones.
   */
  enum TrackCategory {
    /** Highest priority: Meets both frame rate and bitrate limits */
    MEETS_ALL_LIMITS,
    
    /** Second priority: Meets frame rate limit but exceeds bitrate limit */
    EXCEEDS_BITRATE,
    
    /** Third priority: Meets bitrate limit but exceeds frame rate limit */
    EXCEEDS_FRAME_RATE,
    
    /** Lowest priority: Exceeds both frame rate and bitrate limits */
    EXCEEDS_BITRATE_AND_FRAME_RATE
  }
}
