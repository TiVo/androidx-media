package com.tivo.exoplayer.library.tracks;

import static com.google.android.exoplayer2.PlaybackException.CUSTOM_ERROR_CODE_BASE;

import android.content.Context;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.MimeTypes;

/**
 * TrackSelector implementation that restricts selected audio tracks to
 * the subset that are supported for {@link com.google.android.exoplayer2.LivePlaybackSpeedControl}.
 *
 * The synced video playback requires an audio format that supports live playback speed
 * adjustment, this track selector filters the audio selections to restrict to formats
 * supported for speed adjustment (that is PCM audio only)
 */
public class SyncVideoTrackSelector extends DefaultTrackSelector {


  /** Sync video failed because tunneling mode was selected and is not supported */
  public static final int ERROR_CODE_SYNC_VIDEO_FAILED_TUNNELING = CUSTOM_ERROR_CODE_BASE + 1;

  /** Sync video failed because no supported audio tracks are available */
  public static final int ERROR_CODE_SYNC_VIDEO_FAILED_NO_PCM_AUDIO = CUSTOM_ERROR_CODE_BASE + 2;

  private boolean enableTrackFiltering = false;

  public SyncVideoTrackSelector(Context context, ExoTrackSelection.Factory trackSelectionFactory) {
    super(context, trackSelectionFactory);
  }

  /**
   * Calls the super method, but first with a modified formatSupport value that excludes
   * all audio formats (by marking as {@link C.FormatSupport#FORMAT_UNSUPPORTED_TYPE}) that
   * are non-PCM audio (so the do not allow speed adjustment)
   *
   * Checks for tunneling mode requested or no selectable audio format and if either throws
   * a playback exception with a custom error code.
   *
   * @param groups The {@link TrackGroupArray} mapped to the renderer.
   * @param formatSupport The {@link RendererCapabilities.Capabilities} for each mapped track, indexed by track group and
   *     track (in that order).
   * @param mixedMimeTypeAdaptationSupports The {@link RendererCapabilities.AdaptiveSupport} for mixed MIME type
   *     adaptation for the renderer.
   * @param params The selector's current constraint parameters.
   * @param enableAdaptiveTrackSelection Whether adaptive track selection is allowed.
   * @return result from super class, but all non PCM audio formats are marked as C.FORMAT_UNSUPPORTED_TYPE
   * @throws ExoPlaybackException if no audio formats meet criteria or tunneling requested.
   */
  @Nullable
  @Override
  protected Pair<ExoTrackSelection.Definition, AudioTrackScore> selectAudioTrack(
      TrackGroupArray groups,
      int[][] formatSupport,
      int mixedMimeTypeAdaptationSupports,
      Parameters params,
      boolean enableAdaptiveTrackSelection
  ) throws ExoPlaybackException {
    @RendererCapabilities.Capabilities int[][] filteredFormatSupport = formatSupport;

    if (enableTrackFiltering) {
      filteredFormatSupport = filterFormatSupport(groups, formatSupport, params);
    }

    return super.selectAudioTrack(groups, filteredFormatSupport, mixedMimeTypeAdaptationSupports, params,
        enableAdaptiveTrackSelection);
  }

  @NonNull
  private int[][] filterFormatSupport(TrackGroupArray groups, int[][] formatSupport, Parameters params) throws ExoPlaybackException {
    if (params.tunnelingEnabled) {
      throw ExoPlaybackException.createForUnexpected(new RuntimeException("tunneling not supported for synced video"),
          ERROR_CODE_SYNC_VIDEO_FAILED_TUNNELING);
    }

    @RendererCapabilities.Capabilities int[][] formatSupportCopy = new int[formatSupport.length][formatSupport[0].length];

    int supportedFormatsCount = 0;
    Format unsupportedFormat = null;
    for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
      TrackGroup trackGroup = groups.get(groupIndex);
      for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
        Format format = trackGroup.getFormat(trackIndex);
        if (isSupportedAudioFormatForSyncVideo(format)) {
          supportedFormatsCount++;
          formatSupportCopy[groupIndex][trackIndex] = formatSupport[groupIndex][trackIndex];
        } else {
          formatSupportCopy[groupIndex][trackIndex] = C.FORMAT_UNSUPPORTED_TYPE;
          unsupportedFormat = format;
        }
      }
    }

    if (supportedFormatsCount == 0) {
      throw ExoPlaybackException.createForRenderer(new RuntimeException("synced video requires at least one PCM audio track"),
          "Audio",
          0,
          unsupportedFormat,
          C.FORMAT_UNSUPPORTED_TYPE,
          false,
          ERROR_CODE_SYNC_VIDEO_FAILED_NO_PCM_AUDIO
        );
    }
    return formatSupportCopy;
  }

  /**
   * Set the overall behavior to filter or not filter. By default filtering is turned
   * off.  This method should be called before each playback start to set the correct state
   * It can also be called during error recovery to disable filtering
   * 
   * @param enable true to enable filtering to only audio supporting synced video
   */
  public void setEnableTrackFiltering(boolean enable) {
    boolean changed = enable != enableTrackFiltering;
    enableTrackFiltering = enable;
    if (changed) {
      super.invalidate();
    }
  }

  /**
   * Get the state of the enableTrackFiltering boolean.
   * This is the getter for setEnableTrackFiltering.
   *
   * @return boolean enableTrackFiltering
   */
  public boolean getEnableTrackFiltering() {
    return enableTrackFiltering;
  }

  /**
   * Encapsulates the supported audio formats for Synced Playback.
   *
   * @param format, the audio format
   * @return boolean whether or not the given audio format is supported
   */
  public static boolean isSupportedAudioFormatForSyncVideo(Format format) {
    return MimeTypes.AUDIO_MP4.equals(format.sampleMimeType) || MimeTypes.AUDIO_AAC.equals(format.sampleMimeType);
  }
}