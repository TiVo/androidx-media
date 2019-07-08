package com.google.android.exoplayer2.trickplay;


import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;

/**
 * Constructs the TrickPlayControl and binds it to a SimpleExoPlayer object.
 *
 * The Trick-play control enables fast playback/rewind for a {@link MediaSource}.  If the MediaSource is
 * from an HLS playlist that includes an IFrame only playlist then playback/rewind at faster speeds is
 * supported, this is indicated by the supported speeds returned.
 *
 */
public class TrickPlayControlFactory {

    public TrickPlayControlFactory() {
    }

    /**
     * Create the TrickPlayControl implementation.  The TrickPlayControl can be re-used for multiple
     * player instances.  Call {@link TrickPlayControl#setPlayer(SimpleExoPlayer)} to bind the controll to
     * a player.
     *
     * @param trackSelector - used for selecting the iFrame only track and toggling audio
     * @return the new TrickPlayControl implenentation
     */
    public TrickPlayControl createTrickPlayControl(DefaultTrackSelector trackSelector) {
        return new TrickPlayController(trackSelector);
    }
}
