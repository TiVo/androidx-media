package com.tivo.exoplayer.library.metrics;

/**
 * This is the API to allow managing updating the the metrics objects ({@link PlaybackMetrics} and {@link TrickPlayMetrics})
 * for use in an operational intelligence dashboard
 *
 * The ExoPlayer analytics package (PlaybackStatsListener) is used along with trick-play state to produce the set of playback metrics.
 *
 * This API does best effort to abstract all reference to ExoPlayer classes and interfaces behind this class and the
 * {@link PlaybackMetrics} and {@link TrickPlayMetrics} it manages.
 */
public interface PlaybackMetricsManagerApi {
    /**
     * This method will trigger the final {@link MetricsEventListener} session end events
     * <p>
     * It should be called when:
     * <ol>
     *     <li>There is a fatal player error reported.</li>
     *     <li>Prior to releasing the player - (e.g. in the Activity lifecycle methods, onStop or destroy).</li>
     * </ol>
     */
    void endAllSessions();

    /**
     * Sets a "lap counter" reset in the PlaybackMetrics, so values that are accumulated only from the last call to this
     * method to the call to {@link #createOrReturnCurrent()} are accounted properly.
     * <p>
     * TODO - is this needed still?  the architecture here is a bit fragile as the reset is async to the data
     * collection
     */
    void resetPlaybackMetrics();

    /**
     * Update the PlaybackMetrics object parameter with statistics from the current playback session.
     *
     * @param metrics - {@link PlaybackMetrics} object or subclass thereof
     * @return true if there are active stats available.
     */
    boolean updateFromCurrentStats(PlaybackMetrics metrics);

    /**
     * Triggers creation of new {@link PlaybackMetrics} object using the callback or returns the last one created.
     *
     * @return current {@link PlaybackMetrics} object or subclass
     */
    PlaybackMetrics createOrReturnCurrent();
}
