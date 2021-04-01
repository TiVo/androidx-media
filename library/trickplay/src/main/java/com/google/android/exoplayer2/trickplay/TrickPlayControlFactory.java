package com.google.android.exoplayer2.trickplay;


import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.IFrameAwareAdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.util.Clock;

/**
 * Constructs the TrickPlayControl and binds it to a SimpleExoPlayer object.
 *
 * The Trick-play control enables fast playback/rewind for a {@link MediaSource}.  If the MediaSource is
 * from an HLS playlist that includes an IFrame only playlist then playback/rewind at faster speeds is
 * supported, this is indicated by the supported speeds returned.
 *
 */
public class TrickPlayControlFactory {

    private IFrameAwareAdaptiveTrackSelection.Factory trackSelectionFactory;

    public TrickPlayControlFactory() {

        /*
          Override defaults to:
            - set the low buffer threshold to 12s rather than 25s.  This allows live to shift up.
            - set to use all the bandwidth in the playlist (fraction is 100%)
         */
        trackSelectionFactory = new IFrameAwareAdaptiveTrackSelection.Factory(
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
                12_000,
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                1.0f,
                AdaptiveTrackSelection.DEFAULT_BUFFERED_FRACTION_TO_LIVE_EDGE_FOR_QUALITY_INCREASE,
                AdaptiveTrackSelection.DEFAULT_MIN_TIME_BETWEEN_BUFFER_REEVALUTATION_MS,
                Clock.DEFAULT);
    }

    /**
     * Create the TrickPlayControl implementation.  The TrickPlayControl can be re-used for multiple
     * player instances.  Call {@link TrickPlayControl#setPlayer(SimpleExoPlayer)} to bind the control to
     * a player.
     *
     * @param trackSelector - used for selecting the iFrame only track and toggling audio
     * @return the new TrickPlayControl implenentation
     */
    public TrickPlayControl createTrickPlayControl(DefaultTrackSelector trackSelector) {
        TrickPlayController trickPlayController = new TrickPlayController(trackSelector);
        trackSelectionFactory.setTrickPlayControl(trickPlayController);
        return trickPlayController;
    }

    /**
     * Cleans up any references held by the trick-play control in preperation for app stop()
     *
     * @param control the {@link TrickPlayControl} to cleanup
     */
    public void destroyTrickPlayControl(TrickPlayControl control) {
        trackSelectionFactory.setTrickPlayControl(null);
    }

    /**
     * Get the TrackSelection.Factory that Trick-play will use.  This allows wiring to the
     * {@link DefaultTrackSelector} that you would pass in to {@link #createTrickPlayControl(DefaultTrackSelector)}
     *
     * @return The {@link TrackSelection.Factory} instance that the {@link TrickPlayControl} is using
     */
    public TrackSelection.Factory getTrackSelectionFactory() {
        return trackSelectionFactory;
    }
}
