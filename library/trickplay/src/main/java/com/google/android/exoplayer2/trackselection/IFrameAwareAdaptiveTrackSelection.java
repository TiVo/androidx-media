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

        // Don't filter selection at all if there are selection overrides, otherwise if we are doing
        // trickplay, filter in/out the iFrame only tracks
        //
        boolean hasSelectionOverrides = definition.group.length != definition.tracks.length;
        if (trickPlayControl != null && ! hasSelectionOverrides) {
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

    /**
     * Filter out tracks that are not useful given the trickplay mode (if normal, only include
     * non-iFrame Only tracks, otherwise include only iFrame tracks)
     *
     * @param definition - returned as is or filtered if it is a video selection and has iframe
     * @param mode - current Trick-play direction
     * @return the passed in definition or a filtered copy of it.
     */
    private Definition filterIFrameTracks(Definition definition,
        TrickPlayControl.TrickPlayDirection mode) {
      Definition newDefinition = definition;

      if (isVideoDefinition(definition) && hasIframeOnlyTracks(definition)) {
        ArrayList<Integer> list = new ArrayList<>();
        TrackGroup group = definition.group;
        for (int i = 0; i < group.length; i++) {
          if (useFormatInTrickMode(group.getFormat(i), mode)) {
            list.add(i);
          }
        }
        newDefinition = new Definition(group, Util.toArray(list), definition.reason,
            definition.data);
      }
      return newDefinition;
    }

    /**
     * Detrmine if {@link Format} should be excluded or included from the selectable tracks
     *
     * @param format - format to check (checks it for IFrame Only)
     * @param mode - current Trick-play direction
     * @return true to include the format.
     */
    private boolean useFormatInTrickMode(Format format, TrickPlayControl.TrickPlayDirection mode) {
      return (isIframeOnly(format) && (mode != TrickPlayControl.TrickPlayDirection.NONE))
          || (!isIframeOnly(format) && (mode == TrickPlayControl.TrickPlayDirection.NONE));
    }

    /**
     * If the created {@link TrackSelection} objects should consider trick-play state, call this
     * method.
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

    /**
     * Check for iFrame only tracks.
     *
     * @param definition {@link TrackSelection.Definition} to check
     * @return true if any tracks are iFrame only
     */
    private boolean hasIframeOnlyTracks(Definition definition) {
      boolean hasIframeOnly = false;
      for (int i = 0; i < definition.group.length && !hasIframeOnly; i++) {
        hasIframeOnly = isIframeOnly(definition.group.getFormat(i));
      }
      return hasIframeOnly;
    }

    private boolean isVideo(Format format) {
      return format.height > 0 || MimeTypes.getVideoMediaMimeType(format.codecs) != null;
    }
  }

  // TODO this will probably be non-empty class when we adapt multiple iFrame tracks.
  public IFrameAwareAdaptiveTrackSelection(TrackGroup group, int[] tracks,
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
}
