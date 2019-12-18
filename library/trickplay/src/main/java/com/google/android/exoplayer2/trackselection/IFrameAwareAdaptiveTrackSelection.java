package com.google.android.exoplayer2.trackselection;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.chunk.MediaChunk;
import com.google.android.exoplayer2.source.chunk.MediaChunkIterator;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
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
  private static final String TAG = "IFrameAwareAdaptiveTrackSelection";

  @Nullable
  private TrickPlayControl trickPlayControl;
  private boolean flushQueueOnNextEvaluate;
  private TrickPlayControl.TrickMode trickModeLastQueueSizeEvaluate;

  public static class Factory implements TrackSelection.Factory {

    @Nullable
    private TrickPlayControl trickPlayControl;

    @Override
    public @NullableType TrackSelection[] createTrackSelections(@NullableType Definition[] definitions, BandwidthMeter bandwidthMeter) {

      TrackSelection[] selections = new TrackSelection[definitions == null ? 0 : definitions.length];
      for (int i = 0; i < selections.length; i++) {
        Definition definition = definitions[i];
        if (definition == null) {
          continue;
        }

        // TODO - the really belongs in the TrackSelector itself, adding a TrackSelectionParameters for the trickplay state.
        // TODO - that also should enable/disable sound and the whole bit..  Would need that to be integrated with Google source
        // TODO - as TrackSelectionParameters are impossible to extend, but a much more architecurally correct solution..
        // Our hack, if trick play mode filter the Definition to include only or exclude IFRAME only tracks
        if (trickPlayControl != null) {
          definition = filterIFrameTracks(definition, trickPlayControl.getCurrentTrickDirection());
        }
        if (definition.tracks.length > 1) {
          TrackSelection adaptiveSelection = new AdaptiveTrackSelection(definition.group, definition.tracks, bandwidthMeter);
          selections[i] = adaptiveSelection;
        } else {
          selections[i] = new FixedTrackSelection(
              definition.group, definition.tracks[0], definition.reason, definition.data);
        }

      }
      return selections;
    }

    private Definition filterIFrameTracks(Definition definition, TrickPlayControl.TrickPlayDirection mode) {
      Definition newDefinition = definition;

      if (isVideoDefinition(definition) && hasIframeOnlyTracks(definition)) {
        ArrayList<Integer> list = new ArrayList<>();
        TrackGroup group = definition.group;
        for (int i=0; i < group.length; i++) {
          if (useFormatInTrickMode(group.getFormat(i), mode)) {
            list.add(i);
          }
        }
        newDefinition = new Definition(group, Util.toArray(list), definition.reason, definition.data);


      }
      return newDefinition;
    }

    private boolean useFormatInTrickMode(Format format, TrickPlayControl.TrickPlayDirection mode) {
      return (isIframeOnly(format) && (mode != TrickPlayControl.TrickPlayDirection.NONE))
          || (!isIframeOnly(format) && (mode == TrickPlayControl.TrickPlayDirection.NONE));
    }

    /**
     * If the created {@link TrackSelection} objects should consider trick-play state, call this
     * method.
     *
     * @param trickPlayControl
     */
    public void setTrickPlayControl(@Nullable TrickPlayControl trickPlayControl) {
      this.trickPlayControl = trickPlayControl;
    }

    public static boolean isIframeOnly(Format format) {
      return (format.roleFlags & C.ROLE_FLAG_TRICK_PLAY) != 0;
    }

    private boolean isVideoDefinition(Definition definition) {
      return definition.group.length > 0 && isVideo(definition.group.getFormat(0));
    }

    private boolean hasIframeOnlyTracks(Definition definition) {
      boolean hasIframeOnly = false;
      for (int i=0; i < definition.group.length && ! hasIframeOnly; i++) {
        hasIframeOnly = isIframeOnly(definition.group.getFormat(i));
      }
      return hasIframeOnly;
    }

    private boolean isVideo(Format format) {
      return format.height > 0 || MimeTypes.getVideoMediaMimeType(format.codecs) != null;
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

  public void setTrickPlayControl(TrickPlayControl trickPlayControl) {
    this.trickPlayControl = trickPlayControl;
  }

  @Override
  public int evaluateQueueSize(long playbackPositionUs, List<? extends MediaChunk> queue) {
    int targetQueueSize;
    if (trickPlayControl == null) {
      targetQueueSize = super.evaluateQueueSize(playbackPositionUs, queue);
    } else {
      targetQueueSize = evaluateQueueSizeForTrickPlay(playbackPositionUs, queue);
    }
    return targetQueueSize;
  }

  private int evaluateQueueSizeForTrickPlay(long playbackPositionUs, List<? extends MediaChunk> queue) {
    int targetQueueSize;

    if (trickModeLastQueueSizeEvaluate == null) {
      trickModeLastQueueSizeEvaluate = trickPlayControl.getCurrentTrickMode();
      targetQueueSize = super.evaluateQueueSize(playbackPositionUs, queue);
    } else if (trickModeLastQueueSizeEvaluate != trickPlayControl.getCurrentTrickMode()) {

      if (queue.size() > 0) {
        Log.d(TAG,
            "evaluateQueueSize() - mode " + trickPlayControl.getCurrentTrickMode() + " size: "
                + queue.size());
        for (MediaChunk chunk : queue) {
          Log.d(TAG, "chunk " + chunk.chunkIndex
              + " start/end: " + chunk.startTimeUs + "/" + chunk.endTimeUs + " format: "
              + chunk.trackFormat);
        }
      }

      // We just switched into or out of trickplay
      if (trickPlayControl.getCurrentTrickMode() == TrickPlayControl.TrickMode.NORMAL) {
        targetQueueSize = 0;   // Flush non-iframe chunks, TODO - check them for format.
      } else if (trickModeLastQueueSizeEvaluate == TrickPlayControl.TrickMode.NORMAL) {
        targetQueueSize = 0;
      } else {
        targetQueueSize = super.evaluateQueueSize(playbackPositionUs, queue);
      }
      trickModeLastQueueSizeEvaluate = trickPlayControl.getCurrentTrickMode();
    } else {
      targetQueueSize = super.evaluateQueueSize(playbackPositionUs, queue);
    }
    return targetQueueSize;
  }

  @Override
  protected boolean canSwitchNow(long bufferedDurationUs, long availableDurationUs,
      Format currentFormat, Format selectedFormat) {
    boolean canSwitch = Factory.isIframeOnly(currentFormat) || Factory.isIframeOnly(selectedFormat);
    if (canSwitch) {
      Log.d(TAG, "canSwitchNow() - from " + currentFormat.toString() + " to " + selectedFormat.toString());

      if (Factory.isIframeOnly(currentFormat)) {
        Log.d(TAG, "Switching from iFrame track, force flush of samples");
        flushQueueOnNextEvaluate = true;
      }

    } else {
      canSwitch = super.canSwitchNow(bufferedDurationUs, availableDurationUs, currentFormat, selectedFormat);
    }
    return canSwitch;
  }

  @Override
  public void updateSelectedTrack(long playbackPositionUs, long bufferedDurationUs,
      long availableDurationUs, List<? extends MediaChunk> queue,
      MediaChunkIterator[] mediaChunkIterators) {

    int currentSelectedIndex = getSelectedIndex();

    super.updateSelectedTrack(playbackPositionUs, bufferedDurationUs, availableDurationUs, queue,
        mediaChunkIterators);

    if (currentSelectedIndex != getSelectedIndex()) {
      if (Factory.isIframeOnly(getFormat(currentSelectedIndex)) && trickPlayControl != null && trickPlayControl.getCurrentTrickMode() == TrickPlayControl.TrickMode.NORMAL) {
        Log.d(TAG, "Switching back from iFrame track to track: " + getFormat(getSelectedIndex()).toString());
      } else {
        Log.d(TAG, "Switching track from " + getFormat(currentSelectedIndex) + ", to: " + getFormat(getSelectedIndex()));

      }
    }
  }

  /**
   * Override to select only the iFrame tracks when we are in iFrame trickplay mode.
   *
   * @param format The {@link Format} of the candidate track.
   * @param trackBitrate The estimated bitrate of the track. May differ from {@link Format#bitrate}
   *     if a more accurate estimate of the current track bitrate is available.
   * @param playbackSpeed The current playback speed.
   * @param effectiveBitrate The bitrate available to this selection.
   * @return true if the format can be selected
   */
  protected boolean canSelectFormat(
      Format format, int trackBitrate, float playbackSpeed, long effectiveBitrate) {

    boolean isIframeOnly = Factory.isIframeOnly(format);
    boolean canSelect;


    if (Factory.isIframeOnly(getFormat(getSelectedIndex())) && trickPlayControl != null && trickPlayControl.getCurrentTrickMode() == TrickPlayControl.TrickMode.NORMAL) {
      Log.d(TAG, "Switching back from iFrame track from trickplaymode");
    }
    if (trickPlayControl != null && trickPlayControl.getCurrentTrickMode() != TrickPlayControl.TrickMode.NORMAL) {
      canSelect = isIframeOnly;
    } else {
      canSelect = super.canSelectFormat(format, trackBitrate, playbackSpeed, effectiveBitrate);
    }

//    Log.d(TAG, "canSelectFormat() - to: " + format.toString() + " isIf: " + isIframeOnly + " canSelect: " + canSelect + " effBR: " + effectiveBitrate + " trackBR: " + trackBitrate + " speed: "+playbackSpeed);

    return canSelect;
  }

}
