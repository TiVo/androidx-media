package com.tivo.exoplayer.library.metrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Map;

import com.google.android.exoplayer2.source.MediaLoadData;
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
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.util.MimeTypes;

@RunWith(AndroidJUnit4.class)
public class PlaybackStatsExtensionTest {

    static final Format TEST_BASEVIDEO_FORMAT = Format.createVideoSampleFormat(null,
            MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
            null, null);
    static final Format TEST_BASEAUDIO_FORMAT = Format.createAudioSampleFormat(null,
            MimeTypes.AUDIO_AAC,null,Format.NO_VALUE,Format.NO_VALUE,Format.NO_VALUE,Format.NO_VALUE,
            null,null,Format.NO_VALUE,null);
    static final Timeline TEST_TIMELINE = new FakeTimeline(/* windowCount= */ 1);

    @Test
    public void testGetTimeInFormat_AllwaysPlayingState() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(30).setPeakBitrate(30).build(),
        };

        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);
        playbackStatsListener.onTimelineChanged(createEventTime(0), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(0), createMediaLoad(formats[0]));
        playbackStatsListener.onPlaybackStateChanged(createEventTime(5), Player.STATE_READY);
        playbackStatsListener.onPlayWhenReadyChanged(createEventTime(5), true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(30), createMediaLoad(formats[1]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[2]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(160), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(200), createMediaLoad(formats[1]));
        playbackStatsListener.onPlaybackStateChanged(createEventTime(300), Player.STATE_ENDED);
        playbackStatsListener.onPlayWhenReadyChanged(createEventTime(300), false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

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
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(30).setPeakBitrate(30).build(),
        };

        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

        playbackStatsListener.onTimelineChanged(createEventTime(0), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(0), createMediaLoad(formats[0]));
        playbackStatsListener.onPlaybackStateChanged(createEventTime(5), Player.STATE_READY);
        playbackStatsListener.onPlayWhenReadyChanged(createEventTime(5), true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(30), createMediaLoad(formats[1]));
        playbackStatsListener.onPlaybackStateChanged(createEventTime(40), Player.STATE_BUFFERING);
        playbackStatsListener.onPlaybackStateChanged(createEventTime(80), Player.STATE_READY);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[2]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(160), createMediaLoad(formats[0]));
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(200), createMediaLoad(formats[1]));
        playbackStatsListener.onPlaybackStateChanged(createEventTime(400), Player.STATE_ENDED);
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


    static MediaLoadData createMediaLoad(Format format) {
        int type = MimeTypes.getTrackType(format.sampleMimeType);
        return new MediaLoadData(0, type, format, 0, null, 0, 0);
    }

    static AnalyticsListener.EventTime createEventTime(long realtimeMs) {
        return new AnalyticsListener.EventTime(
                realtimeMs,
                TEST_TIMELINE,
                0,
                null,
                0,
                TEST_TIMELINE,
                0,
                null,
                0,
                0);
    }
}
