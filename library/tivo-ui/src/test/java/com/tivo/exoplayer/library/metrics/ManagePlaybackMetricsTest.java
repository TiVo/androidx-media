package com.tivo.exoplayer.library.metrics;

import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_BASEVIDEO_FORMAT;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createEventTime;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createMediaLoad;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.util.Clock;

@RunWith(AndroidJUnit4.class)
public class ManagePlaybackMetricsTest {

    @Test
    public void testMultiplePlaybackMetrics_GetPlaybackState() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20), TEST_BASEVIDEO_FORMAT.copyWithBitrate(30),
        };


        ManagePlaybackMetrics manageMetrics = new ManagePlaybackMetrics(Clock.DEFAULT);

        PlaybackStatsListener playbackStatsListener = manageMetrics.getPlaybackStatsListener();

        PlaybackMetrics metrics = new PlaybackMetrics();
        manageMetrics.setCurrentPlaybackMetrics(metrics);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));

        playbackStatsListener.onTimelineChanged(createEventTime(200), Player.TIMELINE_CHANGE_REASON_DYNAMIC);
        playbackStatsListener.onPlayerStateChanged(createEventTime(200), true, Player.STATE_READY);

        SystemClock.setCurrentTimeMillis(225);

        metrics = manageMetrics.setCurrentPlaybackMetrics(new PlaybackMetrics());
        Map<Format, Long> results = metrics.getTimeInVideoFormat();
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo(25);

        SystemClock.setCurrentTimeMillis(230);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(230), createMediaLoad(formats[1]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(300), createMediaLoad(formats[2]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(360), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(500), createMediaLoad(formats[1]));
        playbackStatsListener.onPlayerStateChanged(createEventTime(800), true, Player.STATE_ENDED);

        SystemClock.setCurrentTimeMillis(850);  // after last event
        metrics = manageMetrics.setCurrentPlaybackMetrics(new PlaybackMetrics());

        long[] expected = {
                (230 - 200) + (500 - 360),
                (300 - 230) + (800 - 500),
                160 - 100
        };
        results = metrics.getTimeInVideoFormat();
        assertThat(results.keySet().size()).isEqualTo(expected.length);
        long totalInFormats = 0L;
        for (int i = 0; i < expected.length; i++) {
            Long time = results.get(formats[i]);
            assertWithMessage("Format at " + i +" should be in results")
                    .that(time).isNotNull();
            totalInFormats += time;
            assertWithMessage("Format at " + i +" should have playback time " + expected[i])
                    .that(time).isEqualTo(expected[i]);
        }

        assertThat(totalInFormats).isEqualTo(metrics.getTotalPlaybackTimeMs());
    }

}
