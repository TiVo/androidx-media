package com.google.android.exoplayer2.trickplay;

/**
 * Event callbacks for various interesting state changes to trick play.
 */
public interface TrickPlayEventListener {

    /**
     * Called when the playback state changes such that the underlying
     * {@link com.google.android.exoplayer2.source.MediaSource} will support high speed playback
     *
     * The updated playback speeds supported are returned via {@link TrickPlayControl#}
     *
     * @param isSmoothPlaySupported
     */
    void smoothPlayStateChanged(boolean isSmoothPlaySupported);

    /**
     * Triggered by the call to change the speed {@see TrickPlayControl#setTrickMode(TrickMode)}.
     *
     * Dispatched on the listeners Looper, so it will not re-entrantly call the caller of setTrickMode()
     *
     * @param newMode
     * @param prevMode
     */
    void trickPlayModeChanged(TrickPlayControl.TrickMode newMode, TrickPlayControl.TrickMode prevMode);
}
