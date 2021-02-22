package com.tivo.exoplayer.library.metrics;

import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;

/**
 * Callback interface for {@link PlaybackMetrics} and {@link TrickPlayMetrics} lifecycle events
 *
 * This interface is the main interface to allow {@link ManagePlaybackMetrics} to communicate with
 * external clients.
 */
public interface MetricsEventListener {

    /**
     * Factory method allow clients to create their own sub-class of {@link TrickPlayMetrics}.
     * Default creates the base class.
     *
     * @param currentMode - current {@link TrickPlayControl.TrickMode} at time of call
     * @param prevMode - previous {@link TrickPlayControl.TrickMode}
     * @return instance of {@link TrickPlayMetrics}, this object will be returned as a parameter
     *         to {@link #trickPlayMetricsAvailable(TrickPlayMetrics, PlaybackStats)}
     */
    default TrickPlayMetrics createEmptyTrickPlayMetrics(TrickPlayControl.TrickMode currentMode, TrickPlayControl.TrickMode prevMode) {
        return new TrickPlayMetrics(currentMode, prevMode);
    }

    /**
     * Called after each change to {@link TrickPlayControl.TrickMode} with the {@link TrickPlayMetrics}
     * collected for that section of trick-play playback.
     *
     * @param metrics a {@link TrickPlayMetrics} with the final values filled in for the session.  This will be
     *                the same object that was returned from {@link #createEmptyTrickPlayMetrics(TrickPlayControl.TrickMode, TrickPlayControl.TrickMode)}
     *                if you override it, so you must type cast to the subclass.
     * @param stats the {@link PlaybackStats} used for filling in the metrics, preference is to use metrics
     */
    default void trickPlayMetricsAvailable(TrickPlayMetrics metrics, PlaybackStats stats) {}

    /**
     * Factory method allow clients to create their own sub-class of {@link TrickPlayMetrics}.
     * Default creates the base class.
     *
     * This method is called-back from the {@link ManagePlaybackMetrics} when the client requests updated
     * metrics via {@link ManagePlaybackMetrics#getUpdatedPlaybackMetrics()}.  Having a factory callback is
     * simply for symmetry with the TrickPlayMetrics creation.
     *
     * @return object returned from {@link #createEmptyPlaybackMetrics()} filled in with metrics. Type cast is
     *         required to the type you return from the create method (generics would make this so messy, not worth it)
     */
    default PlaybackMetrics createEmptyPlaybackMetrics() { return new PlaybackMetrics(); }

    /**
     * Called when the current playback session ends (due to an error, channel change, or end of content)
     * with the {@link PlaybackMetrics} for that session
     *
     * @param stats the {@link PlaybackStats} used for filling in the metrics, preference is to use metrics
     * @param sessionInformation {@link MetricsPlaybackSessionManager.SessionInformation} information about the active session
     * @param metrics object returned from {@link #createEmptyPlaybackMetrics()} filled in with metrics. Type cast is
     *         required to the type you return from the create method (generics would make this so messy, not worth it)
     */
    default void playbackMetricsAvailable(PlaybackStats stats, MetricsPlaybackSessionManager.SessionInformation sessionInformation, PlaybackMetrics metrics) {}

    /**
     * Notification measurement is now starting to measure trick-play playback
     */
    default void enteringTrickPlayMeasurement() {}


    /**
     * Notification trick-play playback measurement is ending, may restore regular playback measurement
     */
    default void exitingTrickPlayMeasurement() {}
}
