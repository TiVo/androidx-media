package com.tivo.exoplayer.library.metrics;

import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_BASEVIDEO_FORMAT;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_TIMELINE;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createEventTime;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createMediaLoad;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.util.Clock;

@RunWith(AndroidJUnit4.class)
public class ManagePlaybackMetricsTest {

    // Object being tested.
    private PlaybackMetricsManagerApi manageMetrics;

    // Mock player to capture the current analytics listener
    @Mock
    private SimpleExoPlayer playerMock;

    // Mock TrickPlayControl to capture the TrickPlayEventListener
    @Mock
    private TrickPlayControl controlMock;


    private AnalyticsListener analyticsListener;            // current AnalyticsListener attached to player.
    private TrickPlayEventListener trickPlayEventListener;  // shouldn't change

    // Captures the AnalyticsListener[s] passed to the player mock's addAnalyticsListener
    private ArgumentCaptor<AnalyticsListener> analyticsListenerArgumentCaptor;

    @Before
    public void setupMocksAndTestee() {
        MockitoAnnotations.initMocks(this);

        ArgumentCaptor<TrickPlayEventListener> trickPlayListenerCaptor = ArgumentCaptor.forClass(TrickPlayEventListener.class);
        analyticsListenerArgumentCaptor = ArgumentCaptor.forClass(AnalyticsListener.class);

        when(playerMock.getCurrentTimeline()).thenReturn(TEST_TIMELINE);
        manageMetrics = new ManagePlaybackMetrics.Builder(playerMock, controlMock)
                .setClock(Clock.DEFAULT)
                .build();

        verify(playerMock).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        analyticsListener = analyticsListenerArgumentCaptor.getValue();

        verify(controlMock).addEventListener(trickPlayListenerCaptor.capture());
        trickPlayEventListener = trickPlayListenerCaptor.getValue();
    }


    @Test
    public void testExcludesTrickPlay_PlaybackMetrics() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20)
        };

        // Format 0 plays from 200 to 500, but trickplay is active from 225 to 300
        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        analyticsListener.onTimelineChanged(createEventTime(200), Player.TIMELINE_CHANGE_REASON_DYNAMIC);
        analyticsListener.onPlayerStateChanged(createEventTime(200), true, Player.STATE_READY);

        // Switch into trickplay mode and generate some metrics that should be ingored
        SystemClock.setCurrentTimeMillis(225);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);

        // The analytics listener changes, kind of a cheat to understand this internal of ManagePlaybackMetrics
        verify(playerMock, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener underTrickPlayListener = analyticsListenerArgumentCaptor.getValue();
        assertThat(analyticsListener).isNotEqualTo(underTrickPlayListener);

        underTrickPlayListener.onPlayerStateChanged(createEventTime(230), true, Player.STATE_BUFFERING);
        underTrickPlayListener.onDownstreamFormatChanged(createEventTime(230), createMediaLoad(formats[1]));
        underTrickPlayListener.onPlayerStateChanged(createEventTime(240), true, Player.STATE_READY);

        // Changing back to NORMAL exits trickplay and restores normal playback analytics capture
        SystemClock.setCurrentTimeMillis(300);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);
        verify(playerMock, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener savedHandler = analyticsListenerArgumentCaptor.getValue();
        assertThat(analyticsListener).isSameInstanceAs(savedHandler);
        analyticsListener.onPlayerStateChanged(createEventTime(400), true, Player.STATE_READY);
        SystemClock.setCurrentTimeMillis(500);

        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);
        assertThat(metrics.getTotalTrickPlayTime()).isEqualTo(300 - 225);
        assertThat(metrics.getTrickPlayCount()).isEqualTo(1);

        Map<Format, Long> results = metrics.getTimeInVideoFormat();
        assertThat(metrics.getTotalRebufferingTime()).isEqualTo(0);
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo((500 - 200) - (300 - 225) );

        assertThat(metrics.getProfileShiftCount()).isEqualTo(1);
    }

    @Test
    public void testMultiplePlaybackMetrics_GetTimeInVideoFormat() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20), TEST_BASEVIDEO_FORMAT.copyWithBitrate(30),
        };

        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        analyticsListener.onTimelineChanged(createEventTime(200), Player.TIMELINE_CHANGE_REASON_DYNAMIC);
        analyticsListener.onPlayerStateChanged(createEventTime(200), true, Player.STATE_READY);

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
        analyticsListener.onPlayerStateChanged(createEventTime(800), true, Player.STATE_ENDED);

        SystemClock.setCurrentTimeMillis(850);  // after last event
        metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);

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

    @Test
    public void test_getAvgVideoBitrate_metric_excludesTrickplay() {

        // Will play half the time in 10Mbps and half in 20Mbps, expect result is 15Mbps
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10_000_000), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20_000_000)
        };

        // first format plays from 400 - 700, minus 100ms of buffering so 200 ms
        SystemClock.setCurrentTimeMillis(200);
        analyticsListener.onDownstreamFormatChanged(createEventTime(300), createMediaLoad(formats[0]));
        analyticsListener.onPlayerStateChanged(createEventTime(400), true, Player.STATE_READY);
        analyticsListener.onPlayerStateChanged(createEventTime(500), true, Player.STATE_BUFFERING);
        analyticsListener.onPlayerStateChanged(createEventTime(600), true, Player.STATE_READY);
        SystemClock.setCurrentTimeMillis(700);

        // second format 700 to 1000, with 100ms of trick-play  so 200 ms
        analyticsListener.onDownstreamFormatChanged(createEventTime(700), createMediaLoad(formats[1]));

        SystemClock.setCurrentTimeMillis(800);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);
        SystemClock.setCurrentTimeMillis(900);
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);

        SystemClock.setCurrentTimeMillis(1000);
        analyticsListener.onPlayerStateChanged(createEventTime(1000), true, Player.STATE_ENDED);
        PlaybackMetrics metrics = manageMetrics.createOrReturnCurrent();
        manageMetrics.updateFromCurrentStats(metrics);

        // Expect half the time in format 0 and half in format 1
        float expected = (formats[0].bitrate/1_000_000.0f + formats[1].bitrate/1_000_000.0f) / 2;

        assertThat(metrics.getAvgVideoBitrate()).isEqualTo(expected);
    }

    @Test
    public void test_getAvgNetworkBandwidth_metric() {

        SystemClock.setCurrentTimeMillis(200);
        analyticsListener.onPlayerStateChanged(createEventTime(200), true, Player.STATE_READY);
        analyticsListener.onBandwidthEstimate(createEventTime(250), 200, 1_000_000, 0);
        analyticsListener.onBandwidthEstimate(createEventTime(260), 400, 1_000_000, 0);
        SystemClock.setCurrentTimeMillis(660);
        analyticsListener.onPlayerStateChanged(createEventTime(660), true, Player.STATE_ENDED);

        PlaybackMetrics metrics = new PlaybackMetrics();
        manageMetrics.updateFromCurrentStats(metrics);

        // Two loads, both 1MB avg is the total loaded / total time to load it
        float expected = (float) ((2.0 * 8) / (0.2 + 0.4));

        assertThat(metrics.getAvgNetworkBitrate()).isEqualTo(expected);
    }
}
