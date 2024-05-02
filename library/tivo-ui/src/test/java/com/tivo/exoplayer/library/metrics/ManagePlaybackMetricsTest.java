package com.tivo.exoplayer.library.metrics;

import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.decoder.Decoder;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_BANDWIDTH_ESTIMATE;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_DOWNSTREAM_FORMAT_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYBACK_STATE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAYER_ERROR;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_PLAY_WHEN_READY_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_TIMELINE_CHANGED;
import static com.google.android.exoplayer2.analytics.AnalyticsListener.EVENT_TRACKS_CHANGED;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_BASEAUDIO_FORMAT;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_BASEVIDEO_FORMAT;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_TIMELINE;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createEventTime;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createMediaLoad;
import static com.tivo.exoplayer.library.metrics.TrickPlayMetricsHelper.createEventsAtSameEventTime;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.util.Clock;

import java.util.List;


/**
 * Test the {@link PlaybackMetricsManagerApi} that manages {@link com.google.android.exoplayer2.analytics.PlaybackStats} collection
 * for trick play and regular playback.
 *
 * The strategy is to Mock the {@link SimpleExoPlayer} and call {@link AnalyticsListener} methods to simulate
 * playback execution paths.
 *
 * Note since our 2.15.1 ExoPlayer version update the {@link AnalyticsListener} API methods used by playback stats changed
 * with the introduction of {@link AnalyticsListener#onEvents(Player, AnalyticsListener.Events)}.  The vast majoroity of
 * state changes now come from this onEvents method call and calling the associated Player.   Only these {@link AnalyticsListener}
 * methods are still overridden:
 *  onPositionDiscontinuity
 *  onDroppedVideoFrames
 *  onLoadError
 *  onDrmSessionManagerError
 *  onBandwidthEstimate
 *  onDownstreamFormatChanged
 *  onVideoSizeChanged
 *
 */
@RunWith(AndroidJUnit4.class)
public class ManagePlaybackMetricsTest {

    // Object being tested.
    private PlaybackMetricsManagerApi manageMetrics;

    /**
     * The {@link com.google.android.exoplayer2.analytics.PlaybackStatsListener} and associated
     * classes  uses the {@link com.google.android.exoplayer2.analytics.PlaybackStatsListener} events and methods
     * on the {@link SimpleExoPlayer} to determine the playback history.
     */
    @Mock
    private SimpleExoPlayer playerMock;

    // Mock TrickPlayControl to capture the TrickPlayEventListener
    @Mock
    private TrickPlayControl controlMock;

    @Mock
    private MetricsEventListener metricsEventListener;

    private AnalyticsListener analyticsListener;            // current AnalyticsListener attached to player.

    private PlayerStatisticsHelper playerStatisticsHelper;  // current PlayerStatisticsHelper
    private TrickPlayEventListener trickPlayEventListener;  // shouldn't change

    // Captures the AnalyticsListener[s] passed to the player mock's addAnalyticsListener
    private ArgumentCaptor<AnalyticsListener> analyticsListenerArgumentCaptor;

    private DecoderCounters decoderCountersTest = new DecoderCounters();
    private DecoderCounters getDecoderCountersTP = new DecoderCounters();

    private DecoderCounters increment() {
        decoderCountersTest.renderedOutputBufferCount += 10;
        return decoderCountersTest;
    }

    private DecoderCounters zer0() {
        getDecoderCountersTP.renderedOutputBufferCount = 0;
        return getDecoderCountersTP;
    }
    class MyTrickPlayMetrics extends TrickPlayMetrics {

        public MyTrickPlayMetrics(TrickPlayControl.TrickMode currentMode, TrickPlayControl.TrickMode prevMode) {
            super(currentMode, prevMode);
        }
    }

    @Before
    public void setupMocksAndTestee() {
        MockitoAnnotations.openMocks(this);

        ArgumentCaptor<TrickPlayEventListener> trickPlayListenerCaptor = ArgumentCaptor.forClass(TrickPlayEventListener.class);
        ArgumentCaptor<PlaybackStatsListener> playbackStatsListenerArgumentCaptor = ArgumentCaptor.forClass(PlaybackStatsListener.class);

        analyticsListenerArgumentCaptor = ArgumentCaptor.forClass(AnalyticsListener.class);

        when(playerMock.getCurrentTimeline()).thenReturn(TEST_TIMELINE);
        when(playerMock.getPlaybackParameters()).thenReturn(PlaybackParameters.DEFAULT);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_IDLE);
        when(playerMock.getPlayWhenReady()).thenReturn(true);
        when(playerMock.getCurrentTrackSelections()).thenReturn(new TrackSelectionArray());

        when(metricsEventListener.createEmptyPlaybackMetrics()).thenCallRealMethod();
        when(metricsEventListener.createEmptyTrickPlayMetrics(any(), any())).thenCallRealMethod();

        manageMetrics = new ManagePlaybackMetrics.Builder(playerMock, controlMock)
                .setClock(Clock.DEFAULT)
                .setMetricsEventListener(metricsEventListener)
                .build();

        verify(playerMock, atMost(2)).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        List<AnalyticsListener> capturedListener = analyticsListenerArgumentCaptor.getAllValues();
        analyticsListener = capturedListener.get(0);
        playerStatisticsHelper = (PlayerStatisticsHelper)capturedListener.get(1);

        verify(controlMock).addEventListener(trickPlayListenerCaptor.capture());
        trickPlayEventListener = trickPlayListenerCaptor.getValue();
    }

    @Test
    public void testNullFormatHandled() {
        AnalyticsListener.EventTime initialTime = createEventTime(200);
        MockTrackSelection mockTrackSelection = MockTrackSelection.buildFrom(TEST_BASEVIDEO_FORMAT);
        when(playerMock.getCurrentTrackSelections()).thenReturn(new TrackSelectionArray(mockTrackSelection));
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        when(playerMock.getPlayWhenReady()).thenReturn(true);
        analyticsListener.onDownstreamFormatChanged(initialTime, createMediaLoad(TEST_BASEVIDEO_FORMAT));
        AnalyticsListener.Events events = createEventsAtSameEventTime(initialTime,
            EVENT_DOWNSTREAM_FORMAT_CHANGED, EVENT_TIMELINE_CHANGED, EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        // TrackSelection that disables video (our mute for example) can cause a null video format period,
        // that is the last 40ms (210 - 250) of playback
        TrackSelectionArray testSelection = new TrackSelectionArray();
        when(playerMock.getCurrentTrackSelections()).thenReturn(testSelection);
        AnalyticsListener.EventTime eventTime = createEventTime(210);
        events = createEventsAtSameEventTime(eventTime, EVENT_TRACKS_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        SystemClock.setCurrentTimeMillis(250);
        manageMetrics.endAllSessions();
        ArgumentCaptor<PlaybackMetrics> metricsArgumentCaptor = ArgumentCaptor.forClass(PlaybackMetrics.class);
        verify(metricsEventListener).playbackMetricsAvailable(metricsArgumentCaptor.capture(), any());
        PlaybackMetrics metrics = metricsArgumentCaptor.getValue();

        assertThat(metrics).isInstanceOf(PlaybackMetrics.class);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getMetricsAsMap()).isNotNull();
        Map<Format, Long> timeInVideoFormat = metrics.getTimeInVideoFormat();
        assertThat(timeInVideoFormat).isNotNull();
        Set<Map.Entry<Format, Long>> entries = timeInVideoFormat.entrySet();
        assertThat(entries.size()).isEqualTo(2);
        for (Map.Entry<Format, Long> entry : entries) {
            if (entry.getKey() == null) {
                assertThat(entry.getValue()).isEqualTo(40);
            } else {
                assertThat(entry.getValue()).isEqualTo(10);
                assertThat(entry.getKey()).isEqualTo(TEST_BASEVIDEO_FORMAT);
            }
        }
    }

    @Test
    public void testTrickPlayMetrics_ListenersCalled() {

        when(metricsEventListener.createEmptyTrickPlayMetrics(any(), any())).thenAnswer(
                (Answer<TrickPlayMetrics>) invocation -> new MyTrickPlayMetrics(invocation.getArgument(0), invocation.getArgument(1)));

        MockTrackSelection mockTrackSelection = MockTrackSelection.buildFrom(TEST_BASEVIDEO_FORMAT);
        when(playerMock.getCurrentTrackSelections()).thenReturn(new TrackSelectionArray(mockTrackSelection));
        AnalyticsListener.EventTime eventTime = createEventTime(100);
        analyticsListener.onDownstreamFormatChanged(eventTime, createMediaLoad(TEST_BASEVIDEO_FORMAT));
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime, EVENT_DOWNSTREAM_FORMAT_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        eventTime = createEventTime(200);
        events = createEventsAtSameEventTime(eventTime, EVENT_TIMELINE_CHANGED, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        //Not a null counter
        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        // Switch into trickplay mode, generate some trickplay events, then switch out.
        SystemClock.setCurrentTimeMillis(225);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);

        SystemClock.setCurrentTimeMillis(225);
        trickPlayEventListener.trickFrameRendered(1);
        SystemClock.setCurrentTimeMillis(325);
        trickPlayEventListener.trickFrameRendered(1);
        SystemClock.setCurrentTimeMillis(425);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);

        ArgumentCaptor<TrickPlayMetrics> trickPlayMetricsCaptor = ArgumentCaptor.forClass(TrickPlayMetrics.class);
        verify(metricsEventListener).trickPlayMetricsAvailable(trickPlayMetricsCaptor.capture(), any());
        TrickPlayMetrics metrics = trickPlayMetricsCaptor.getValue();
        assertThat(metrics).isInstanceOf(MyTrickPlayMetrics.class);
        assertThat(metrics).isNotNull();
        assertThat(metrics.getRenderedFramesCount()).isEqualTo(2);
    }

    @Test
    public void testExcludesTrickPlay_PlaybackMetrics() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build()
        };

        MockTrackSelection mockTrackSelection = MockTrackSelection.buildFrom(formats);
        when(playerMock.getCurrentTrackSelections()).thenReturn(new TrackSelectionArray(mockTrackSelection));

        // Loading starts for the initial format (formats[0]) in buffering state, this is the initial join time
        AnalyticsListener.EventTime eventTime = createEventTime(100);
        analyticsListener.onDownstreamFormatChanged(eventTime, createMediaLoad(formats[0]));
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime, EVENT_DOWNSTREAM_FORMAT_CHANGED, EVENT_PLAYBACK_STATE_CHANGED);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
        analyticsListener.onEvents(playerMock, events);

        // Format 0 plays from 200 to 500, but trickplay is active from 225 to 300
        int playbackTimePlusTrickPlayTime = 500 - 200;
        int expectedTrickPlayTime = 300 - 225;

        AnalyticsListener.EventTime eventTime200 = createEventTime(200);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        when(playerMock.getPlayWhenReady()).thenReturn(true);
        events = createEventsAtSameEventTime(eventTime200, EVENT_TIMELINE_CHANGED, EVENT_PLAYBACK_STATE_CHANGED, EVENT_PLAY_WHEN_READY_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        //Not a null counter
        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        // Switch into trickplay mode and generate some metrics that should be ingored
        SystemClock.setCurrentTimeMillis(225);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);

        // The analytics listener changes, kind of a cheat to understand this internal of ManagePlaybackMetrics
        verify(playerMock, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener underTrickPlayListener = analyticsListenerArgumentCaptor.getValue();
        assertThat(analyticsListener).isNotEqualTo(underTrickPlayListener);

        // Buffering events in trickplay are not part of playback metrics
        mockTrackSelection.setSelectedIndex(1);
        AnalyticsListener.EventTime eventTime230 = createEventTime(230);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
        underTrickPlayListener.onDownstreamFormatChanged(eventTime230, createMediaLoad(formats[1]));
        events = createEventsAtSameEventTime(eventTime230, EVENT_PLAYBACK_STATE_CHANGED, EVENT_DOWNSTREAM_FORMAT_CHANGED);
        underTrickPlayListener.onEvents(playerMock, events);

        AnalyticsListener.EventTime eventTime240 = createEventTime(240);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        events = createEventsAtSameEventTime(eventTime240, EVENT_PLAYBACK_STATE_CHANGED);
        underTrickPlayListener.onEvents(playerMock, events);

        // Changing back to NORMAL exits trickplay and restores normal playback analytics capture
        SystemClock.setCurrentTimeMillis(300);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);
        verify(playerMock, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener savedHandler = analyticsListenerArgumentCaptor.getValue();
        assertThat(analyticsListener).isSameInstanceAs(savedHandler);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        events = createEventsAtSameEventTime(createEventTime(400), EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        SystemClock.setCurrentTimeMillis(500);

        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);
        assertThat(metrics.getTotalTrickPlayTime()).isEqualTo(expectedTrickPlayTime);

        // Trickplay is counted as a seek (buffering)
        assertThat(metrics.getTrickPlayCount()).isEqualTo(1);
        assertThat(metrics.getTotalSeekTimeMs()).isEqualTo(expectedTrickPlayTime);

        Map<Format, Long> results = metrics.getTimeInVideoFormat();
        assertThat(metrics.getTotalRebufferingTime()).isEqualTo(0);
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo(playbackTimePlusTrickPlayTime - expectedTrickPlayTime);

        assertThat(metrics.getProfileShiftCount()).isEqualTo(0);
    }

    @Ignore("needs analytics listener onEvent")
    @Test
    public void testMultiplePlaybackMetrics_GetTimeInVideoFormat() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(), TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(30).setPeakBitrate(30).build(),
        };

        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        AnalyticsListener.EventTime eventTime200 = createEventTime(200);
        analyticsListener.onTimelineChanged(eventTime200, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
        analyticsListener.onPlaybackStateChanged(eventTime200, Player.STATE_READY);
        analyticsListener.onPlayWhenReadyChanged(eventTime200, true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

        SystemClock.setCurrentTimeMillis(225);

        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);
        Map<Format, Long> results = metrics.getTimeInVideoFormat();
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo(25);

        manageMetrics.resetPlaybackMetrics();

        SystemClock.setCurrentTimeMillis(230);
        analyticsListener.onDownstreamFormatChanged(createEventTime(230), createMediaLoad(formats[1]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(300), createMediaLoad(formats[2]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(360), createMediaLoad(formats[0]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(500), createMediaLoad(formats[1]));
        analyticsListener.onPlaybackStateChanged(createEventTime(800), Player.STATE_ENDED);

        SystemClock.setCurrentTimeMillis(850);  // after last event

        manageMetrics.endAllSessions();
        ArgumentCaptor<PlaybackMetrics> metricsArgumentCaptor = ArgumentCaptor.forClass(PlaybackMetrics.class);
        verify(metricsEventListener).playbackMetricsAvailable(metricsArgumentCaptor.capture(), any());
        metrics = metricsArgumentCaptor.getValue();

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

        assertThat(metrics.getEndReason()).isEqualTo(PlaybackMetrics.EndReason.END_OF_CONTENT);
    }

    @Ignore("needs analytics listener onEvent")
    @Test
    public void testMultiplePlaybackMetrics_GetTimeInAudioOnlyFormat() {
        Format formats[] = {
                TEST_BASEAUDIO_FORMAT.buildUpon().setAverageBitrate(10).setPeakBitrate(10).build(), TEST_BASEAUDIO_FORMAT.buildUpon().setAverageBitrate(20).setPeakBitrate(20).build(), TEST_BASEAUDIO_FORMAT.buildUpon().setAverageBitrate(30).setPeakBitrate(30).build(),
        };

        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        AnalyticsListener.EventTime eventTime200 = createEventTime(200);
        analyticsListener.onTimelineChanged(eventTime200, Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
        analyticsListener.onPlaybackStateChanged(eventTime200, Player.STATE_READY);
        analyticsListener.onPlayWhenReadyChanged(eventTime200, true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);

        SystemClock.setCurrentTimeMillis(225);

        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);
        Map<Format, Long> results = metrics.getTimeInAudioOnlyFormat();
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo(25);

        manageMetrics.resetPlaybackMetrics();

        SystemClock.setCurrentTimeMillis(230);
        analyticsListener.onDownstreamFormatChanged(createEventTime(230), createMediaLoad(formats[1]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(300), createMediaLoad(formats[2]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(360), createMediaLoad(formats[0]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(500), createMediaLoad(formats[1]));
        analyticsListener.onPlaybackStateChanged(createEventTime(800), Player.STATE_ENDED);

        SystemClock.setCurrentTimeMillis(850);  // after last event

        manageMetrics.endAllSessions();
        ArgumentCaptor<PlaybackMetrics> metricsArgumentCaptor = ArgumentCaptor.forClass(PlaybackMetrics.class);
        verify(metricsEventListener).playbackMetricsAvailable(metricsArgumentCaptor.capture(), any());
        metrics = metricsArgumentCaptor.getValue();

        long[] expected = {
                (230 - 200) + (500 - 360),
                (300 - 230) + (800 - 500),
                160 - 100
        };
        results = metrics.getTimeInAudioOnlyFormat();
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

        assertThat(metrics.getEndReason()).isEqualTo(PlaybackMetrics.EndReason.END_OF_CONTENT);
    }

    @Test
    public void test_getAvgVideoBitrate_metric_excludesTrickplay() {

        // Will play half the time in 10Mbps and half in 20Mbps, expect result is 15Mbps
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(10_000_000).setPeakBitrate(10_000_000).build(),
                TEST_BASEVIDEO_FORMAT.buildUpon().setAverageBitrate(20_000_000).setPeakBitrate(20_000_000).build()
        };

        MockTrackSelection mockTrackSelection = MockTrackSelection.buildFrom(formats);
        when(playerMock.getCurrentTrackSelections()).thenReturn(new TrackSelectionArray(mockTrackSelection));

        // Loading starts for the initial format (formats[0]) in buffering state, this is the initial join time
        AnalyticsListener.EventTime eventTime100 = createEventTime(100);
        analyticsListener.onDownstreamFormatChanged(eventTime100, createMediaLoad(formats[0]));

        // first format plays from 400 - 700, minus 100ms of buffering so 200 ms actual playing time
        SystemClock.setCurrentTimeMillis(200);
        AnalyticsListener.EventTime eventTime300 = createEventTime(300);
        analyticsListener.onDownstreamFormatChanged(eventTime300, createMediaLoad(formats[0]));
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime300, EVENT_DOWNSTREAM_FORMAT_CHANGED, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        AnalyticsListener.EventTime eventTime400 = createEventTime(400);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        when(playerMock.getPlayWhenReady()).thenReturn(true);
        events = createEventsAtSameEventTime(eventTime400, EVENT_PLAY_WHEN_READY_CHANGED, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        AnalyticsListener.EventTime eventTime500 = createEventTime(500);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_BUFFERING);
        events = createEventsAtSameEventTime(eventTime500, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        AnalyticsListener.EventTime eventTime600 = createEventTime(600);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        events = createEventsAtSameEventTime(eventTime600, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        SystemClock.setCurrentTimeMillis(700);

        // second format 700 to 1000, with 100ms of trick-play  so 200 ms
        AnalyticsListener.EventTime eventTime700 = createEventTime(700);
        analyticsListener.onDownstreamFormatChanged(eventTime700, createMediaLoad(formats[1]));
        mockTrackSelection.setSelectedIndex(1);
        events = createEventsAtSameEventTime(eventTime700, EVENT_DOWNSTREAM_FORMAT_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        //Not a null counter
        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        SystemClock.setCurrentTimeMillis(800);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);
        SystemClock.setCurrentTimeMillis(900);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);

        SystemClock.setCurrentTimeMillis(1000);
        AnalyticsListener.EventTime eventTime1000 = createEventTime(1000);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_ENDED);
        events = createEventsAtSameEventTime(eventTime1000, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);

        // Expect half the time in format 0 and half in format 1
        float expected = (formats[0].bitrate/1_000_000.0f + formats[1].bitrate/1_000_000.0f) / 2;

        assertThat(metrics.getAvgVideoBitrate()).isEqualTo(expected);
    }

    @Ignore("this was a copy of test_getAvgVideoBitrate_metric_excludesTrickplay, so copy it again ;-)")
    @Test
    public void test_getAvgAudioBitrate_metric_excludesTrickplay() {

        // Will play audio half the time in 1Mbps and half in 2Mbps, expect result is 1.5Mbps
        Format formats[] = {
                TEST_BASEAUDIO_FORMAT.buildUpon().setAverageBitrate(10_000_00).setPeakBitrate(10_000_00).build(),
                TEST_BASEAUDIO_FORMAT.buildUpon().setAverageBitrate(20_000_00).setPeakBitrate(20_000_00).build()
        };

        // first format plays from 400 - 700, minus 100ms of buffering so 200 ms
        SystemClock.setCurrentTimeMillis(200);
        analyticsListener.onDownstreamFormatChanged(createEventTime(300), createMediaLoad(formats[0]));
        analyticsListener.onPlayWhenReadyChanged(createEventTime(400), true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
        analyticsListener.onPlaybackStateChanged(createEventTime(400), Player.STATE_READY);
        analyticsListener.onPlaybackStateChanged(createEventTime(500), Player.STATE_BUFFERING);
        analyticsListener.onPlaybackStateChanged(createEventTime(600), Player.STATE_READY);
        SystemClock.setCurrentTimeMillis(700);

        // second format 700 to 1000, with 100ms of trick-play  so 200 ms
        analyticsListener.onDownstreamFormatChanged(createEventTime(700), createMediaLoad(formats[1]));

        SystemClock.setCurrentTimeMillis(800);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);
        SystemClock.setCurrentTimeMillis(900);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);

        SystemClock.setCurrentTimeMillis(1000);
        analyticsListener.onPlaybackStateChanged(createEventTime(1000), Player.STATE_ENDED);
        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);

        // Expect half the time in format 0 and half in format 1 of Audio
        float expected = (formats[0].bitrate/1_000_000.0f + formats[1].bitrate/1_000_000.0f) / 2;

        assertThat(metrics.getAvgAudioBitrate()).isEqualTo(expected);
    }

    @Test
    public void test_getAvgNetworkBandwidth_metric() {
        SystemClock.setCurrentTimeMillis(200);
        AnalyticsListener.EventTime eventTime200 = createEventTime(200);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime200, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        AnalyticsListener.EventTime eventTime250 = createEventTime(250);
        analyticsListener.onBandwidthEstimate(eventTime250, 200, 1_000_000, 0);
        events = createEventsAtSameEventTime(eventTime250, EVENT_BANDWIDTH_ESTIMATE);
        analyticsListener.onEvents(playerMock, events);

        AnalyticsListener.EventTime eventTime260 = createEventTime(260);
        analyticsListener.onBandwidthEstimate(eventTime260, 400, 1_000_000, 0);
        events = createEventsAtSameEventTime(eventTime260, EVENT_BANDWIDTH_ESTIMATE);
        analyticsListener.onEvents(playerMock, events);

        SystemClock.setCurrentTimeMillis(660);

        AnalyticsListener.EventTime eventTime660 = createEventTime(660);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_ENDED);
        events = createEventsAtSameEventTime(eventTime660, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        PlaybackMetrics metrics = new PlaybackMetrics();
        manageMetrics.updateFromCurrentStats(metrics);

        // Two loads, both 1MB avg is the total loaded / total time to load it
        float expected = (float) ((2.0 * 8) / (0.2 + 0.4));

        assertThat(metrics.getAvgNetworkBitrate()).isEqualTo(expected);
    }

    @Test
    public void test_SessionEndsWithError() {

        ArgumentCaptor<PlaybackMetrics> metricsArgumentCaptor = ArgumentCaptor.forClass(PlaybackMetrics.class);

        AnalyticsListener.EventTime eventTime200 = createEventTime(200);
        when(playerMock.getPlaybackState()).thenReturn(Player.STATE_READY);
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime200, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        SystemClock.setCurrentTimeMillis(225);
        ExoPlaybackException error = ExoPlaybackException.createForUnexpected(new RuntimeException("test"), PlaybackException.ERROR_CODE_UNSPECIFIED);
        AnalyticsListener.EventTime eventTime225 = createEventTime(225);
        when(playerMock.getPlayerError()).thenReturn(error);
        events = createEventsAtSameEventTime(eventTime225, EVENT_PLAYER_ERROR);
        analyticsListener.onEvents(playerMock, events);

        manageMetrics.endAllSessions();

        verify(metricsEventListener).playbackMetricsAvailable(metricsArgumentCaptor.capture(), any());
        PlaybackMetrics metrics = metricsArgumentCaptor.getValue();

        assertThat(metrics.getEndReason()).isEqualTo(PlaybackMetrics.EndReason.ERROR);
        assertThat(metrics.getEndedWithError()).isEqualTo(error);
    }

    @Test
    public void testCaptureVideoDecoderCounters_WithTrickplay() {

        when(metricsEventListener.createEmptyTrickPlayMetrics(any(), any())).thenAnswer(
            (Answer<TrickPlayMetrics>) invocation -> new MyTrickPlayMetrics(invocation.getArgument(0), invocation.getArgument(1)));

        MockTrackSelection mockTrackSelection = MockTrackSelection.buildFrom(TEST_BASEVIDEO_FORMAT);
        when(playerMock.getCurrentTrackSelections()).thenReturn(new TrackSelectionArray(mockTrackSelection));
        AnalyticsListener.EventTime eventTime = createEventTime(100);
        analyticsListener.onDownstreamFormatChanged(eventTime, createMediaLoad(TEST_BASEVIDEO_FORMAT));
        AnalyticsListener.Events events = createEventsAtSameEventTime(eventTime, EVENT_DOWNSTREAM_FORMAT_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        eventTime = createEventTime(200);
        events = createEventsAtSameEventTime(eventTime, EVENT_TIMELINE_CHANGED, EVENT_PLAYBACK_STATE_CHANGED);
        analyticsListener.onEvents(playerMock, events);

        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());
        PlaybackMetrics pbmetrics = manageMetrics.createOrReturnCurrent();
        //First play
        manageMetrics.updateFromCurrentStats(pbmetrics);
        assertThat(pbmetrics.getVideoFramesPresented()).isEqualTo(10);

        //Adding 10 frames
        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());

        //First Trickplay
        // Switch into trickplay mode, generate some trickplay events, then switch out.
        SystemClock.setCurrentTimeMillis(225);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);
        playerStatisticsHelper.mergeCounterValues(playerMock.getVideoDecoderCounters());
        verify(metricsEventListener, atMost(1)).enteringTrickPlayMeasurement();

        //Capturing the first 20
        manageMetrics.updateFromCurrentStats(pbmetrics);
        assertThat(pbmetrics.getVideoFramesPresented()).isEqualTo(20);

        SystemClock.setCurrentTimeMillis(225);
        trickPlayEventListener.trickFrameRendered(1);
        SystemClock.setCurrentTimeMillis(325);
        trickPlayEventListener.trickFrameRendered(1);
        SystemClock.setCurrentTimeMillis(425);

        //Not counting the VTP frames, frame count not increased
        assertThat(pbmetrics.getVideoFramesPresented()).isEqualTo(20);

        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);
        verify(metricsEventListener, atMost(1)).exitingTrickPlayMeasurement();

        playerStatisticsHelper.mergeCounterValues(playerMock.getVideoDecoderCounters());

        //Captured: 20, Current: 30
        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());
        manageMetrics.updateFromCurrentStats(pbmetrics);
        assertThat(pbmetrics.getVideoFramesPresented()).isEqualTo(50);

        //Adding 10
        when(playerMock.getVideoDecoderCounters()).thenReturn(increment());
        manageMetrics.updateFromCurrentStats(pbmetrics);
        assertThat(pbmetrics.getVideoFramesPresented()).isEqualTo(60);

    }
}
