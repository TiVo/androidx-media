package com.tivo.exoplayer.library.metrics;

import android.util.SparseArray;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.FlagSet;
import java.util.Map;

import com.google.android.exoplayer2.source.MediaLoadData;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DOWNSTREAM_FORMAT_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_TIMELINE_CHANGED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.util.MimeTypes;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class PlaybackStatsExtensionTest {

    static final Format TEST_BASEVIDEO_FORMAT = new Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(1280)
        .setHeight(720)
        .build();
    static final Format TEST_BASEAUDIO_FORMAT = new Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_AAC)
        .build();
    static final Timeline TEST_TIMELINE = new FakeTimeline(/* windowCount= */ 1);

    @Mock
    Player playerMock;

    @Before
    public void setupMock() {
        MockitoAnnotations.openMocks(this);

        // Calls to the player from PlaybackStatsListener that are invariant
        when(playerMock.getPlaybackParameters()).thenReturn(PlaybackParameters.DEFAULT);
        when(playerMock.getPlayWhenReady()).thenReturn(false);
    }

    @Test
    public void testGetTimeInFormat_AllwaysPlayingState() {
        Format[] formats = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(30).setPeakBitrate(30).build(),
        };

        MockTrackSelection trackSelection = MockTrackSelection.buildFrom(formats);
        TrackSelectionArray trackSelectionArray = new TrackSelectionArray(trackSelection);
        when(playerMock.getCurrentTrackSelections()).thenReturn(trackSelectionArray);

        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

        AnalyticsListener.EventTime eventTime = createEventTime(0);
        playbackStatsListener.onTimelineChanged(eventTime, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
        playbackStatsListener.onDownstreamFormatChanged(eventTime, createMediaLoad(formats[0]));
        when(playerMock.getPlayerError()).thenReturn(null);
        playbackStatsListener.onEvents(playerMock, createEventsAtSameEventTime(eventTime, EVENT_TIMELINE_CHANGED, EVENT_DOWNSTREAM_FORMAT_CHANGED));

        when(playerMock.getPlayWhenReady()).thenReturn(true);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        eventTime = createEventTime(5);
        playbackStatsListener.onPlaybackStateChanged(eventTime, Player.STATE_READY);
        playbackStatsListener.onPlayWhenReadyChanged(eventTime, true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        playbackStatsListener.onEvents(playerMock, createEventsAtSameEventTime(eventTime, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_STATE_CHANGED));

        trackSelection.setSelectedIndex(1); postDownStreamFormatChange(playbackStatsListener, formats[1], createEventTime(30));
        trackSelection.setSelectedIndex(2); postDownStreamFormatChange(playbackStatsListener, formats[2], createEventTime(100));
        trackSelection.setSelectedIndex(0); postDownStreamFormatChange(playbackStatsListener, formats[0], createEventTime(160));
        trackSelection.setSelectedIndex(1); postDownStreamFormatChange(playbackStatsListener, formats[1], createEventTime(200));

        when(playerMock.getPlayWhenReady()).thenReturn(false);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_ENDED);
        eventTime = createEventTime(300);
        playbackStatsListener.onPlaybackStateChanged(eventTime, Player.STATE_ENDED);
        playbackStatsListener.onPlayWhenReadyChanged(eventTime, false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        playbackStatsListener.onEvents(playerMock, createEventsAtSameEventTime(eventTime, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_STATE_CHANGED));

        long[] expected = {
                (30 - 5) + (200 - 160),
                (100 - 30) + (300 - 200),
                160 - 100
        };
        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        assertThat(stats).isNotNull();

        Map<Format, Long> results = PlaybackStatsExtension.getPlayingTimeInVideoFormat(stats, C.TIME_UNSET);
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

    @Ignore
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

        Map<Format, Long> results = PlaybackStatsExtension.getPlayingTimeInVideoFormat(stats, C.TIME_UNSET);

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

    @Ignore
    @Test
    public void testGetTimeInFormat_PlayingStateChangeWithLastStatePlaying() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(30).setPeakBitrate(30).build(),
        };

        /* This test case tests for, where the last state is Playing and teh format has changed in the playing state*/
        PlaybackStatsListener playbackStatsListener = new PlaybackStatsListener(/* keepHistory= */ true, /* callback= */ null);

        playbackStatsListener.onTimelineChanged(createEventTime(0), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);

        playbackStatsListener.onDownstreamFormatChanged(createEventTime(0), createMediaLoad(formats[0]));
        playbackStatsListener.onPlaybackStateChanged(createEventTime(2), Player.STATE_BUFFERING);
        playbackStatsListener.onPlayWhenReadyChanged(createEventTime(5), true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        playbackStatsListener.onPlaybackStateChanged(createEventTime(5), Player.STATE_READY);
        playbackStatsListener.onDownstreamFormatChanged(createEventTime(30), createMediaLoad(formats[1]));

        long[] expected = {
                (30 - 5) ,
                (50 - 30)
        };

        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        assertThat(stats).isNotNull();

        Map<Format, Long> results = PlaybackStatsExtension.getPlayingTimeInVideoFormat(stats, 50);

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


    void postDownStreamFormatChange(AnalyticsListener listener, Format format, AnalyticsListener.EventTime eventTime) {
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime, EVENT_DOWNSTREAM_FORMAT_CHANGED);
        listener.onDownstreamFormatChanged(eventTime, createMediaLoad(format));
        listener.onEvents(playerMock, events);
    }


    static AnalyticsListener.Events createEventsAtSameEventTime(AnalyticsListener.EventTime eventTime, int... flags) {
        SparseArray<AnalyticsListener.EventTime> eventTimes = new SparseArray<>();
        for (int eventFlag : flags) {
            eventTimes.put(eventFlag, eventTime);
        }
        return new AnalyticsListener.Events(new FlagSet.Builder().addAll(flags).build(), eventTimes);
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
