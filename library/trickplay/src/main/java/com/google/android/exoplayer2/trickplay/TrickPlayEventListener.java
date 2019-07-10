package com.google.android.exoplayer2.trickplay;

/**
 * Event callbacks from the TrickPlayControl
 */
public interface TrickPlayEventListener {

    /**
     * Called when trickplay TrickMode changes.
     *
     * @param previous
     * @param current
     */
    void onTrickModeChange(TrickPlayControl.TrickMode previous, TrickPlayControl.TrickMode current);
}
