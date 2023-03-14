package com.tivo.exoplayer.library.metrics;

import android.util.Log;
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

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DOWNSTREAM_FORMAT_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_POSITION_DISCONTINUITY;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_TIMELINE_CHANGED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.tivo.exoplayer.library.metrics.TrickPlayMetricsHelper.createEventsAtSameEventTime;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    public void testGetTimeInFormat_PlayingStateChange() {
        Format formats[] = {
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

        eventTime = createEventTime(40);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
        playbackStatsListener.onEvents(playerMock, createEventsAtSameEventTime(eventTime, EVENT_DOWNSTREAM_FORMAT_CHANGED, EVENT_PLAYBACK_STATE_CHANGED));

        eventTime = createEventTime(80);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        when(playerMock.getPlayWhenReady()).thenReturn(true);
        playbackStatsListener.onEvents(playerMock, createEventsAtSameEventTime(eventTime, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_STATE_CHANGED));

        trackSelection.setSelectedIndex(2); postDownStreamFormatChange(playbackStatsListener, formats[2], createEventTime(100));
        trackSelection.setSelectedIndex(0); postDownStreamFormatChange(playbackStatsListener, formats[0], createEventTime(160));
        trackSelection.setSelectedIndex(1); postDownStreamFormatChange(playbackStatsListener, formats[1], createEventTime(200));

        when(playerMock.getPlayWhenReady()).thenReturn(false);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_ENDED);
        eventTime = createEventTime(400);
        playbackStatsListener.onPlaybackStateChanged(eventTime, Player.STATE_ENDED);
        playbackStatsListener.onPlayWhenReadyChanged(eventTime, false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        playbackStatsListener.onEvents(playerMock, createEventsAtSameEventTime(eventTime, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_STATE_CHANGED));

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

    @Test
    public void testGetTimeInFormat_PlayingStateChangeWithLastStatePlaying() {
        Format[] formats = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(),
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(),
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

        long[] expected = {
                (30 - 5) ,
                (100 - 30)
        };

        PlaybackStats stats = playbackStatsListener.getPlaybackStats();
        assertThat(stats).isNotNull();

        Map<Format, Long> results = PlaybackStatsExtension.getPlayingTimeInVideoFormat(stats, 100);

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
