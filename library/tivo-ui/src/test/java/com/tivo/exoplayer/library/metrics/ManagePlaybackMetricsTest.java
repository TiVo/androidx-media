package com.tivo.exoplayer.library.metrics;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.MimeTypes;

@RunWith(AndroidJUnit4.class)
public class ManagePlaybackMetricsTest {

    static final Format TEST_BASEVIDEO_FORMAT = Format.createVideoSampleFormat(null,
            MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
            null, null);

    @Test
    public void testGetTimeInFormatNoPlaybackStop() {
        Format formats[] = {
            TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20), TEST_BASEVIDEO_FORMAT.copyWithBitrate(30),
        };

        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

        playbackStatsListener.onPlayerStateChanged(createEventTime(0), true, Player.STATE_READY);
        playbackStatsListener.onTimelineChanged(createEventTime(0), Player.TIMELINE_CHANGE_REASON_DYNAMIC);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(5), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(30), createMediaLoad(formats[1]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[2]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(160), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(200), createMediaLoad(formats[1]));

        PlaybackStats stats1 = playbackStatsListener.getPlaybackStats();
        /* keepHistory= */
        /* callback= */
        PlaybackStats stats = stats1;
        assertThat(stats).isNotNull();

        Map<Format, Long> results = ManagePlaybackMetrics.getTimeInFormat(stats, 400);

        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo((30 - 5) + (200 - 160));

        assertThat(results.get(formats[1])).isNotNull();
        assertThat(results.get(formats[1])).isEqualTo((100 - 30) + (400 - 200));

        assertThat(results.get(formats[2])).isNotNull();
        assertThat(results.get(formats[2])).isEqualTo(160 - 100);
    }


    @Test
    public void testGetAvgVideoBitrate() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10_000_000), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20_000_000)
        };


        FakeClock clock = new FakeClock(0, 0);
        ManagePlaybackMetrics managePlaybackMetrics = new ManagePlaybackMetrics(clock);
        PlaybackMetrics metrics = new PlaybackMetrics();
        managePlaybackMetrics.setCurrentPlaybackMetrics(metrics);

        PlaybackStatsListener playbackStatsListener = managePlaybackMetrics.getPlaybackStatsListener();

        playbackStatsListener.onPlayerStateChanged(createEventTime(0), true, Player.STATE_READY);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(0), createMediaLoad(formats[0]));
        clock.advanceTime(250);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(250), createMediaLoad(formats[1]));
        clock.advanceTime(500);

        metrics = managePlaybackMetrics.getUpdatedPlaybackMetrics();
        assertThat(metrics.getAvgVideoBitrate()).isEqualTo(15.0);     // half the time at 10bps and half at 20bps

    }


    private MediaSourceEventListener.MediaLoadData createMediaLoad(Format format) {
        int type = MimeTypes.getTrackType(format.sampleMimeType);
        return new MediaSourceEventListener.MediaLoadData(0, type, format, 0, null, 0, 0);
    }

    AnalyticsListener.EventTime createEventTime(long realtimeMs) {
        return new AnalyticsListener.EventTime(
                realtimeMs,
                Timeline.EMPTY,
                0,
                null,
                0,
                0,
                0);
    }
}
