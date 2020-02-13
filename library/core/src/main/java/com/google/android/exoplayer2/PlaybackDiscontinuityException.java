package com.google.android.exoplayer2;

public class PlaybackDiscontinuityException extends Exception {

    /**
     * Time of last media sample before discontinuity.
     */
    public long mediaTimeUs;

    public PlaybackDiscontinuityException(long timeUs)
    {
        mediaTimeUs = timeUs;
    }
}
