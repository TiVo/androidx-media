package com.tivo.exoplayer.library.metrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.util.MimeTypes;

@RunWith(AndroidJUnit4.class)
public class PlaybackStatsExtensionTest {

    static final Format TEST_BASEVIDEO_FORMAT = Format.createVideoSampleFormat(null,
            MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
            null, null);
    private static final Timeline TEST_TIMELINE = new FakeTimeline(/* windowCount= */ 1);

    @Test
    public void testGetTimeInFormat_AllwaysPlayingState() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20), TEST_BASEVIDEO_FORMAT.copyWithBitrate(30),
        };

        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);
        playbackStatsListener.onTimelineChanged(createEventTime(0), Player.TIMELINE_CHANGE_REASON_DYNAMIC);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(0), createMediaLoad(formats[0]));
        playbackStatsListener.onPlayerStateChanged(createEventTime(5), true, Player.STATE_READY);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(30), createMediaLoad(formats[1]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[2]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(160), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(200), createMediaLoad(formats[1]));
        playbackStatsListener.onPlayerStateChanged(createEventTime(300), true, Player.STATE_ENDED);

        long[] expected = {
                (30 - 5) + (200 - 160),
                (100 - 30) + (300 - 200),
                160 - 100
        };
        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        assertThat(stats).isNotNull();

        Map<Format, Long> results = PlaybackStatsExtension.getPlayingTimeInFormat(stats, C.TIME_UNSET);
        assertThat(results.keySet().size()).isEqualTo(expected.length);
        long totalInFormats = 0L;
        for (int i = 0; i < expected.length; i++) {
            Long time = results.get(formats[i]);
            assertWithMessage("Format at " + i + " should be in results")
                    .that(time).isNotNull();
            totalInFormats += time;
            assertWithMessage("Format at " + i + " should have playback time " + expected[i])
                    .that(time).isEqualTo(expected[i]);
        }

        assertThat(totalInFormats).isEqualTo(stats.getTotalPlayTimeMs());
    }

    @Test
    public void testGetTimeInFormat_PlayingStateChange() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20), TEST_BASEVIDEO_FORMAT.copyWithBitrate(30),
        };

        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

        playbackStatsListener.onTimelineChanged(createEventTime(0), Player.TIMELINE_CHANGE_REASON_DYNAMIC);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(0), createMediaLoad(formats[0]));
        playbackStatsListener.onPlayerStateChanged(createEventTime(5), true, Player.STATE_READY);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(30), createMediaLoad(formats[1]));
        playbackStatsListener.onPlayerStateChanged(createEventTime(40), true, Player.STATE_BUFFERING);
        playbackStatsListener.onPlayerStateChanged(createEventTime(80), true, Player.STATE_READY);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[2]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(160), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(200), createMediaLoad(formats[1]));
        playbackStatsListener.onPlayerStateChanged(createEventTime(400), true, Player.STATE_ENDED);
        long[] expected = {
                (30 - 5) + (200 - 160),
                ((100 - 30) - (80 - 40)) + (400 - 200),
                160 - 100
        };

        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        assertThat(stats).isNotNull();

        Map<Format, Long> results = PlaybackStatsExtension.getPlayingTimeInFormat(stats, C.TIME_UNSET);

        assertThat(results.keySet().size()).isEqualTo(expected.length);
        long totalInFormats = 0L;
        for (int i = 0; i < expected.length; i++) {
            Long time = results.get(formats[i]);
            assertWithMessage("Format at " + i + " should be in results")
                    .that(time).isNotNull();
            totalInFormats += time;
            assertWithMessage("Format at " + i + " should have playback time " + expected[i])
                    .that(time).isEqualTo(expected[i]);
        }

        assertThat(totalInFormats).isEqualTo(stats.getTotalPlayTimeMs());

    }


    static MediaSourceEventListener.MediaLoadData createMediaLoad(Format format) {
        int type = MimeTypes.getTrackType(format.sampleMimeType);
        return new MediaSourceEventListener.MediaLoadData(0, type, format, 0, null, 0, 0);
    }

    static AnalyticsListener.EventTime createEventTime(long realtimeMs) {
        return new AnalyticsListener.EventTime(
                realtimeMs,
                TEST_TIMELINE,
                0,
                null,
                0,
                0,
                0);
    }
}
