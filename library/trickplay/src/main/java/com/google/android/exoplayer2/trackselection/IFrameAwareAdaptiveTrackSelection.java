package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayControlInternal;
import com.google.android.exoplayer2.trickplay.hls.AugmentedPlaylistParser;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MimeTypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * TrackSelection implementation that adapts based on bandwidth as does {@link AdaptiveTrackSelection}
 * while considering the possible I-Frame only tracks in the track group.
 *
 * The TrackSelection defintions passed to the factory method, {@link Factory#createTrackSelections(Definition[], BandwidthMeter)},
 * are filtered based on the trick-play mode.  That is for trick-play mode, only IFrame Only tracks are
 * included in the adaptive track group, and for normal mode only non-IFrame Only tracks are used.
 *
 * TODO - the really belongs in the TrackSelector itself, adding a TrackSelectionParameters for the trickplay state, that
 *        also whould enable/disable sound tracks and other constraints germane to trickplay. This should be integrated with Google source
 *        as TrackSelectionParameters are impossible to extend.
 */
public class IFrameAwareAdaptiveTrackSelection extends AdaptiveTrackSelection {

  private static final String TAG = "IFrameAwareAdaptiveTrackSelection";

  public static class Factory extends AdaptiveTrackSelection.Factory {
    private final int minDurationForQualityIncreaseMs;
    private final int maxDurationForQualityDecreaseMs;
    private final int minDurationToRetainAfterDiscardMs;
    private final float bandwidthFraction;
    private final float bufferedFractionToLiveEdgeForQualityIncrease;
    private final Clock clock;

    @Nullable
    private TrickPlayControlInternal trickPlayControl;

    public Factory() {
      this(DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS, DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS, DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
           DEFAULT_BANDWIDTH_FRACTION, DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE, Clock.DEFAULT);
    }

    public Factory(
            int minDurationForQualityIncreaseMs,
            int maxDurationForQualityDecreaseMs,
            int minDurationToRetainAfterDiscardMs,
            float bandwidthFraction,
            float bufferedFractionToLiveEdgeForQualityIncrease,
            Clock clock) {
      super(minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs,
              bandwidthFraction, bufferedFractionToLiveEdgeForQualityIncrease, clock);
      this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
      this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
      this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
      this.bandwidthFraction = bandwidthFraction;
      this.bufferedFractionToLiveEdgeForQualityIncrease = bufferedFractionToLiveEdgeForQualityIncrease;
      this.clock = clock;

    }

    @Override
    protected AdaptiveTrackSelection createAdaptiveTrackSelection(TrackGroup group, BandwidthMeter bandwidthMeter, int[] tracks, int totalFixedTrackBandwidth) {
      if (shouldFilterTracks(group, tracks)) {
        tracks = filterTracks(group, tracks);
      }
      return new IFrameAwareAdaptiveTrackSelection(
              group,
              tracks,
              bandwidthMeter,
              0,    // Don't reserve any bandwidth for other than ExoPlayer
              minDurationForQualityIncreaseMs,
              maxDurationForQualityDecreaseMs,
              minDurationToRetainAfterDiscardMs,
              bandwidthFraction,
              bufferedFractionToLiveEdgeForQualityIncrease,
              clock,
              trickPlayControl);
    }

    /**
     * If the created {@link TrackSelection} objects should consider trick-play state, call this method.
     */
    public void setTrickPlayControl(@Nullable TrickPlayControlInternal trickPlayControl) {
      this.trickPlayControl = trickPlayControl;
    }

    @VisibleForTesting
    int[] filterTracks(TrackGroup group, int []tracks) {
      int[] filtered = tracks;    // default is no further filtering is required.

      boolean isSelectionOverride = group.length != tracks.length;
      boolean isIframeOnlyOverride = false;
      if (isSelectionOverride) {
        for (int i=0; i < tracks.length && !isIframeOnlyOverride; i++) {
          isIframeOnlyOverride = isIframeOnly(group.getFormat(tracks[i]));
        }
      }

      // if there are no selection overrides, or the selection overrides do not
      // include iFrame only tracks, then we need add filtering of iFrame only tracks
      // based on the trickplay mode.  Otherwise we can simply return the already filtered
      //
      if (!isSelectionOverride || !isIframeOnlyOverride) {
        assert trickPlayControl != null;
        boolean isTrickPlayEnabled = trickPlayControl.getCurrentTrickDirection() != TrickPlayControl.TrickPlayDirection.NONE;
        ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < group.length; i++) {
          Format format = group.getFormat(i);
          if (!isIframeOnly(format) && !isTrickPlayEnabled) {
            if (!isSelectionOverride || Arrays.binarySearch(tracks, i) >= 0) {
              list.add(i);
            }
          } else if (isIframeOnly(format) && isTrickPlayEnabled) {
            list.add(i);
          }
        }
        filtered = new int[list.size()];
        int i = 0;
        for (Integer value : list) {
          filtered[i++] = value;
        }
      }
      return filtered;
    }

    @VisibleForTesting
    boolean shouldFilterTracks(TrackGroup group, int[] tracks) {
      boolean isAllVideo = isVideoGroup(group);
      boolean hasAnyIframe = false;
      for (int i=0; i < group.length && ! hasAnyIframe; i++) {
        hasAnyIframe = isIframeOnly(group.getFormat(i));
      }
      return trickPlayControl != null && isAllVideo && hasAnyIframe;
    }

    public static boolean isIframeOnly(Format format) {
      return (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
    }

    private boolean isVideoGroup(TrackGroup group) {
      return group.length > 0 && isVideo(group.getFormat(0));
    }

    private static boolean isVideo(Format format) {
      return format.height > 0 || MimeTypes.getVideoMediaMimeType(format.codecs) != null;
    }
  }

  private @Nullable final TrickPlayControlInternal control;

  public IFrameAwareAdaptiveTrackSelection(
          TrackGroup group, int[] tracks,
          BandwidthMeter bandwidthMeter,
          long reservedBandwidth,
          long minDurationForQualityIncreaseMs,
          long maxDurationForQualityDecreaseMs,
          long minDurationToRetainAfterDiscardMs,
          float bandwidthFraction,
          float bufferedFractionToLiveEdgeForQualityIncrease,
          Clock clock,
          @Nullable TrickPlayControlInternal control) {
    super(group, tracks, bandwidthMeter, reservedBandwidth, minDurationForQualityIncreaseMs, maxDurationForQualityDecreaseMs,
            minDurationToRetainAfterDiscardMs, bandwidthFraction, bufferedFractionToLiveEdgeForQualityIncrease,
            clock);
    this.control = control;
  }

  @Override
  protected boolean canSelectFormat(Format format, int trackBitrate, float playbackSpeed, long effectiveBitrate) {
    boolean canSelect = false;
    if (control == null) {
      canSelect = super.canSelectFormat(format, trackBitrate, playbackSpeed, effectiveBitrate);
    } else {
      TrickPlayControl.TrickPlayDirection mode = control.getCurrentTrickDirection();
      switch (mode) {
        case NONE:
          canSelect = ! Factory.isIframeOnly(format) && super.canSelectFormat(format, trackBitrate, playbackSpeed, effectiveBitrate);
          break;

        case REVERSE:
          canSelect = Factory.isIframeOnly(format) && isMaxBitrateFormat(format);
          break;

        case FORWARD:
          if (control.isPlaybackSpeedForwardTrickPlayEnabled()) {
            Float trickPlaySpeed = control.getSpeedFor(control.getCurrentTrickMode());
            canSelect = Factory.isIframeOnly(format) && isBestIFrameFormat(format, trackBitrate, trickPlaySpeed, effectiveBitrate);
          } else {
            //We're doing only seek-based VTP we the full iframe list for FORWARD too.
            canSelect = Factory.isIframeOnly(format) && isMaxBitrateFormat(format);
          }

          break;

        case SCRUB:
          canSelect = Factory.isIframeOnly(format) && isMaxBitrateFormat(format);
          break;
      }
    }
    return canSelect;
  }

  @VisibleForTesting
  boolean testShouldDeferSwitching(long bufferedDurationUs, long availableDurationUs, int currentSelectedIndex, Format selectedFormat) {
    return shouldDeferSwitching(bufferedDurationUs, availableDurationUs, currentSelectedIndex, selectedFormat);
  }

  @Override
  protected boolean shouldDeferSwitching(long bufferedDurationUs, long availableDurationUs, int currentSelectedIndex, Format selectedFormat) {
    boolean value = false;
    if (control == null) {
      value = super.shouldDeferSwitching(bufferedDurationUs, availableDurationUs, currentSelectedIndex, selectedFormat);
    } else {
      TrickPlayControl.TrickPlayDirection mode = control.getCurrentTrickDirection();
      switch (mode) {
        case NONE:
          value = super.shouldDeferSwitching(bufferedDurationUs, availableDurationUs, currentSelectedIndex, selectedFormat);
          break;

          // In REVERSE, if we have played a while, the back buffer can help.  TODO - need to quantify the back buffer with played time
        case REVERSE:
          value = bufferedDurationUs >= 5_000_000L;
          break;

        case FORWARD:
        case SCRUB:
          // default to false
          break;
      }
    }
    return value;
  }

  /**
   * Examines each of the iFrame only Formats in this {@link TrackSelection} to find the
   * one nearest to the target playback speed ({@link TrickPlayControlInternal#getTargetFrameRateForPlaybackSpeed(float)})
   *
   * Neither HLS or DASH require trickplay variants to specify frame rate, so this is inferred by analyzing
   * the segment durations in the iFrame only playlist.  For the curated playlists, {@link Format#frameRate} is
   * set as the fraction of the source playlist's frame rate (which may not be specified).
   *
   * If the no playlist has yet been loaded and the original source playlist frame rate is unknown this method will
   * just return the source playlist format
   *
   * @param playbackSpeed - current trickplay speed (forward, so > 0)
   * @return the matching Format closest matching format or the source.
   */
  @VisibleForTesting
  Format closestTargetFrameRateMatch(float playbackSpeed) {
    @Nullable Format closest = null;
    if (control != null) {
      float targetFrameRate = control.getTargetFrameRateForPlaybackSpeed(playbackSpeed);
      float lowestDelta = Float.MAX_VALUE;
      for (int i = 0; i < length(); i++) {
        Format candidate = getFormat(i);
        if (Factory.isIframeOnly(candidate)) {
          final float frameRateForFormat = control.getFrameRateForFormat(candidate);
          if (frameRateForFormat != Format.NO_VALUE) {
            float actualFrameRate = frameRateForFormat * playbackSpeed;
            float delta = Math.abs(targetFrameRate - actualFrameRate);
            if (delta < lowestDelta) {
              closest = candidate;
              lowestDelta = delta;
            }
          }
        }
      }
    }
    return closest == null ? getSourceIFrameFormat() : closest;
  }

  private Format getSourceIFrameFormat() {
    @Nullable Format format = null;
    for (int index = 0; format == null && index < length(); index++) {
      Format candidate = getFormat(index);
      if (AugmentedPlaylistParser.SRC_FORMAT_LABEL.equals(candidate.label)) {
        format = candidate;
      }
    }
    assert format != null;
    return format;
  }

  private boolean isBestIFrameFormat(Format format, int trackBitrate, float playbackSpeed, long effectiveBitrate) {
    boolean canSelect;
    @Nullable Format closestFrameRateMatch = closestTargetFrameRateMatch(playbackSpeed);
    if (closestFrameRateMatch == null) {
      canSelect = isMinBitrateFormat(format);
    } else {
      canSelect = closestFrameRateMatch.equals(format);
    }
    return canSelect;
  }

  private boolean isMinBitrateFormat(Format format) {
    return isBestBitrateFormat(format, (o1, o2) -> o1 - o2);
  }

  private boolean isMaxBitrateFormat(Format format) {
    return isBestBitrateFormat(format, (o1, o2) -> o2 - o1);
  }

  private boolean isBestBitrateFormat(Format format, Comparator<Integer> bitrateCompare) {
    boolean canSelect;
    @Nullable Format minBitrateFormat = null;
    for (int index = 0; index < length(); index++) {
      Format candidate = getFormat(index);
      minBitrateFormat = minBitrateFormat == null || bitrateCompare.compare(minBitrateFormat.bitrate, candidate.bitrate) > 0 ? candidate : minBitrateFormat;
    }
    canSelect = format == minBitrateFormat;
    return canSelect;
  }


}
