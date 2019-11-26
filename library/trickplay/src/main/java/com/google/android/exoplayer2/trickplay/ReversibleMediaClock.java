package com.google.android.exoplayer2.trickplay;// Copyright 2010 TiVo Inc.  All rights reserved.

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MediaClock;

/**
 * The main ExoPlayer {@link MediaClock} is implemented by the
 * {@link com.google.android.exoplayer2.audio.MediaCodecAudioRenderer} and based on time from the
 * audio track.  In the event there is no audio track the exoplayer DefaultMediaClock implements
 * time keeping for media time.  Neither of these implementation support reverse playback.
 *
 * This {@link MediaClock} allows for reverse playback but other wise follows the interface.
 *
 */
class ReversibleMediaClock implements MediaClock {

    private boolean isForward;
    private Clock clock;
    private long baseElapsedMs;
    private PlaybackParameters playbackParameters;
    private long baseUs;

    /**
     * Construct the clock source with the current position to start and the
     * intended playback direction (as PlaybackParameters does not support speed < 0)
     *
     * @param isForward - true if playback is forward else false.
     * @param positionUs - current position at start of clock
     * @param clock - source of time
     */
    ReversibleMediaClock(boolean isForward, long positionUs, Clock clock) {
        this.isForward = isForward;
        this.clock = clock;
        baseElapsedMs = clock.elapsedRealtime();
        this.baseUs = positionUs;
    }

    Clock getClock() {
        return clock;
    }

    /**
     * Resets the clock's position.  This is used in the event of a forced seek or
     * other discontinuity
     *
     * @param positionUs The position to set in microseconds.
     */
    public void resetPosition(long positionUs) {
        baseElapsedMs = clock.elapsedRealtime();
        baseUs = positionUs;
    }

    @Override
    public long getPositionUs() {
        long positionUs = baseUs;
        long elapsedSinceBaseMs = clock.elapsedRealtime() - baseElapsedMs;
        long elapsedSinceBaseUs = C.msToUs(elapsedSinceBaseMs);
        if (playbackParameters.speed == 1f) {
            positionUs += isForward ? elapsedSinceBaseUs : -elapsedSinceBaseUs;
        } else if (isForward) {
            positionUs += playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
        } else {
            positionUs -= playbackParameters.getMediaTimeUsForPlayoutTimeMs(elapsedSinceBaseMs);
        }
        return Math.max(0, positionUs);
    }

    public void setForward(boolean forward) {
        isForward = forward;
    }

    @Override
    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return playbackParameters;
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return playbackParameters;
    }
}
