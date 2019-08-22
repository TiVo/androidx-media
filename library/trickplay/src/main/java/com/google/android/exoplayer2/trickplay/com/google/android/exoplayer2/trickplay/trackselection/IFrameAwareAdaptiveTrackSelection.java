package com.google.android.exoplayer2.trickplay.com.google.android.exoplayer2.trickplay.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.FixedTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackBitrateEstimator;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * TrackSelection implementation that adapts based on bandwidth as does {@link AdaptiveTrackSelection}
 * while considering the possible I-Frame only tracks in the track group.
 *
 * During higher speed playback adapataion would use the i-Frame only tracks, even if adequate bandwith
 * exists for the regular tracks in order to ensure smooth playback at a fixed frame rate
 *
 */
public class IFrameAwareAdaptiveTrackSelection extends AdaptiveTrackSelection {
  private static final String TAG = "TRICK-PLAY";

  private float iframeSpeedThreshold  = 6.0f;

  public static class Factory implements TrackSelection.Factory {
    private final int minDurationForQualityIncreaseMs;
    private final int maxDurationForQualityDecreaseMs;
    private final int minDurationToRetainAfterDiscardMs;
    private final float bandwidthFraction;
    private final float bufferedFractionToLiveEdgeForQualityIncrease;
    private final long minTimeBetweenBufferReevaluationMs;
    private final Clock clock;

    private TrackBitrateEstimator trackBitrateEstimator;
    private boolean blockFixedTrackSelectionBandwidth;

    /** Creates an adaptive track selection factory with default parameters. */
    public Factory() {
      this(
          DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
          DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
          DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
          DEFAULT_BANDWIDTH_FRACTION,
          DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
          DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
          Clock.DEFAULT);
    }

    public Factory(
        int minDurationForQualityIncreaseMs,
        int maxDurationForQualityDecreaseMs,
        int minDurationToRetainAfterDiscardMs,
        float bandwidthFraction,
        float bufferedFractionToLiveEdgeForQualityIncrease,
        long minTimeBetweenBufferReevaluationMs,
        Clock clock) {
      this.minDurationForQualityIncreaseMs = minDurationForQualityIncreaseMs;
      this.maxDurationForQualityDecreaseMs = maxDurationForQualityDecreaseMs;
      this.minDurationToRetainAfterDiscardMs = minDurationToRetainAfterDiscardMs;
      this.bandwidthFraction = bandwidthFraction;
      this.bufferedFractionToLiveEdgeForQualityIncrease =
          bufferedFractionToLiveEdgeForQualityIncrease;
      this.minTimeBetweenBufferReevaluationMs = minTimeBetweenBufferReevaluationMs;
      this.clock = clock;
      trackBitrateEstimator = TrackBitrateEstimator.DEFAULT;
    }

    @Override
    public @NullableType TrackSelection[] createTrackSelections(@NullableType Definition[] definitions, BandwidthMeter bandwidthMeter) {

      TrackSelection[] selections = new TrackSelection[definitions == null ? 0 : definitions.length];
      for (int i = 0; i < selections.length; i++) {
        Definition definition = definitions[i];
        if (definition == null) {
          continue;
        }
        if (definition.tracks.length > 1) {
          TrackSelection adaptiveSelection = new IFrameAwareAdaptiveTrackSelection(definition, bandwidthMeter);
          selections[i] = adaptiveSelection;
        } else {
          selections[i] = new FixedTrackSelection(
              definition.group, definition.tracks[0], definition.reason, definition.data);
        }
      }
      return selections;
    }
  }

  private IFrameAwareAdaptiveTrackSelection(Definition definition, BandwidthMeter bandwidthMeter) {
    this(definition.group, definition.tracks, bandwidthMeter);
  }

  private IFrameAwareAdaptiveTrackSelection(TrackGroup group,
      int[] tracks,
      BandwidthMeter bandwidthMeter) {
    super(group, tracks, bandwidthMeter);
  }

  public IFrameAwareAdaptiveTrackSelection(TrackGroup group, int[] tracks,
      BandwidthMeter bandwidthMeter, long minDurationForQualityIncreaseMs,
      long maxDurationForQualityDecreaseMs, long minDurationToRetainAfterDiscardMs,
      float bandwidthFraction, float bufferedFractionToLiveEdgeForQualityIncrease,
      long minTimeBetweenBufferReevaluationMs, Clock clock) {
    super(group, tracks, bandwidthMeter, minDurationForQualityIncreaseMs,
        maxDurationForQualityDecreaseMs, minDurationToRetainAfterDiscardMs, bandwidthFraction,
        bufferedFractionToLiveEdgeForQualityIncrease, minTimeBetweenBufferReevaluationMs, clock);
  }


  /**
   * Override to select only the iFrame tracks when playback speed exceeds a threshold.
   *
   * @param format The {@link Format} of the candidate track.
   * @param trackBitrate The estimated bitrate of the track. May differ from {@link Format#bitrate}
   *     if a more accurate estimate of the current track bitrate is available.
   * @param playbackSpeed The current playback speed.
   * @param effectiveBitrate The bitrate available to this selection.
   * @return
   */
  protected boolean canSelectFormat(
      Format format, int trackBitrate, float playbackSpeed, long effectiveBitrate) {

    boolean isIframeOnly = (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
    boolean canSelect = Math.round(trackBitrate * playbackSpeed) <= effectiveBitrate;

    Log.d(TAG, "canSelect - ID: " + format.id + " isIf: " + isIframeOnly + " canSelect: " + canSelect + " effBR: " + effectiveBitrate + " trackBR: " + trackBitrate + " speed: "+playbackSpeed);

    if (Math.abs(playbackSpeed) > iframeSpeedThreshold) {
      canSelect = isIframeOnly;   // TODO factor in playback speed...
    } else {
      canSelect = super.canSelectFormat(format, trackBitrate, playbackSpeed, effectiveBitrate);
    }
    return canSelect;

  }

}
