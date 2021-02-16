package com.tivo.exoplayer.library.metrics;

import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;

/**
 * PlaybackMetrics accumulated during a VTP session.  A VTP session is defined as a period of
 * playback time under a single mode/speed ({@link TrickPlayControl.TrickMode} of VTP.
 *
 * During VTP, regular PlaybackMetrics are suspended and each VTP operation accumulates counters
 * in this object.
 *
 * The metrics measure quantitative performance against the main goals of Trick-Play, which are:
 * <ol>
 *     <li>Progress media time forward at the indicated speed (e.g. at 30x 30 seconds of media should take 1 second)</li>
 *     <li>Render some acceptable set of video frames per-second</li>
 * </ol>
 *
 *
 */
public class TrickPlayMetrics extends PlaybackMetrics {

    private long totalRenderedFrames;
    private long startTimeUs;

    TrickPlayMetrics(long startTimeUs) {
        this.startTimeUs = startTimeUs;
    }

    void setTotalRenderedFrames(long totalRenderedFrames) {
        this.totalRenderedFrames = totalRenderedFrames;
    }

    public long getRenderedFramesCount() {
        return totalRenderedFrames;
    }
}
