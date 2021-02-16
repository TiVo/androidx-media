package com.tivo.exoplayer.library.metrics;

import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.TEST_BASEVIDEO_FORMAT;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createEventTime;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createMediaLoad;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackStatsListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.trickplay.TrickPlayEventListener;
import com.google.android.exoplayer2.util.Clock;

@RunWith(AndroidJUnit4.class)
public class ManagePlaybackMetricsTest {

    @Test
    public void testExcludesTrickPlay_PlaybackMetrics_GetTimeInVideoFormat() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20)
        };

        TrickPlayControl controlMock = mock(TrickPlayControl.class);
        ArgumentCaptor<TrickPlayEventListener> trickPlayListenerCaptor = ArgumentCaptor.forClass(TrickPlayEventListener.class);

        SimpleExoPlayer player = mock(SimpleExoPlayer.class);
        ArgumentCaptor<AnalyticsListener> analyticsListenerArgumentCaptor = ArgumentCaptor.forClass(AnalyticsListener.class);

        ManagePlaybackMetrics manageMetrics = new ManagePlaybackMetrics(Clock.DEFAULT, controlMock, player);

        verify(player).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener analyticsListener = analyticsListenerArgumentCaptor.getValue();

        verify(controlMock).addEventListener(trickPlayListenerCaptor.capture());
        TrickPlayEventListener trickPlayEventListener = trickPlayListenerCaptor.getValue();


        PlaybackMetrics metrics = new PlaybackMetrics();
        manageMetrics.setCurrentPlaybackMetrics(metrics);
        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        analyticsListener.onTimelineChanged(createEventTime(200), Player.TIMELINE_CHANGE_REASON_DYNAMIC);


        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        analyticsListener.onTimelineChanged(createEventTime(200), Player.TIMELINE_CHANGE_REASON_DYNAMIC);
        analyticsListener.onPlayerStateChanged(createEventTime(200), true, Player.STATE_READY);
        SystemClock.setCurrentTimeMillis(225);

        // Switch into trickplay mode and generate some metrics that should be ingored
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.FF1, TrickPlayControl.TrickMode.NORMAL);

        // The analytics listener changes, kind of a cheat to understand this internal of ManagePlaybackMetrics
        verify(player, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener underTrickPlayListener = analyticsListenerArgumentCaptor.getValue();
        assertThat(analyticsListener).isNotEqualTo(underTrickPlayListener);

        underTrickPlayListener.onPlayerStateChanged(createEventTime(230), true, Player.STATE_BUFFERING);
        underTrickPlayListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[1]));
        underTrickPlayListener.onPlayerStateChanged(createEventTime(240), true, Player.STATE_READY);

        // Changing back to NORMAL exits trickplay and restores normal playback analytics capture
        trickPlayEventListener.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, TrickPlayControl.TrickMode.FF1);
        verify(player, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener savedHandler = analyticsListenerArgumentCaptor.getValue();
        assertThat(analyticsListener).isSameInstanceAs(savedHandler);
        SystemClock.setCurrentTimeMillis(400);
        analyticsListener.onPlayerStateChanged(createEventTime(400), true, Player.STATE_READY);

        metrics = manageMetrics.getUpdatedPlaybackMetrics();
        Map<Format, Long> results = metrics.getTimeInVideoFormat();
        assertThat(metrics.getAvgRebufferTime()).isEqualTo(0);
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo(400 - 200);
    }

    @Test
    public void testMultiplePlaybackMetrics_GetTimeInVideoFormat() {
        Format formats[] = {
                TEST_BASEVIDEO_FORMAT.copyWithBitrate(10), TEST_BASEVIDEO_FORMAT.copyWithBitrate(20), TEST_BASEVIDEO_FORMAT.copyWithBitrate(30),
        };

        TrickPlayControl controlMock = mock(TrickPlayControl.class);
        SimpleExoPlayer player = mock(SimpleExoPlayer.class);
        ArgumentCaptor<AnalyticsListener> analyticsListenerArgumentCaptor = ArgumentCaptor.forClass(AnalyticsListener.class);

        ManagePlaybackMetrics manageMetrics = new ManagePlaybackMetrics(Clock.DEFAULT, controlMock, player);

        verify(player).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        AnalyticsListener analyticsListener = analyticsListenerArgumentCaptor.getValue();

        PlaybackMetrics metrics = new PlaybackMetrics();
        manageMetrics.setCurrentPlaybackMetrics(metrics);

        analyticsListener.onDownstreamFormatChanged(createEventTime(100), createMediaLoad(formats[0]));
        analyticsListener.onTimelineChanged(createEventTime(200), Player.TIMELINE_CHANGE_REASON_DYNAMIC);
        analyticsListener.onPlayerStateChanged(createEventTime(200), true, Player.STATE_READY);

        SystemClock.setCurrentTimeMillis(225);

        metrics = manageMetrics.setCurrentPlaybackMetrics(new PlaybackMetrics());
        Map<Format, Long> results = metrics.getTimeInVideoFormat();
        assertThat(results.keySet().size()).isEqualTo(1);
        assertThat(results.get(formats[0])).isNotNull();
        assertThat(results.get(formats[0])).isEqualTo(25);

        SystemClock.setCurrentTimeMillis(230);
        analyticsListener.onDownstreamFormatChanged(createEventTime(230), createMediaLoad(formats[1]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(300), createMediaLoad(formats[2]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(360), createMediaLoad(formats[0]));
        analyticsListener.onDownstreamFormatChanged(createEventTime(500), createMediaLoad(formats[1]));
        analyticsListener.onPlayerStateChanged(createEventTime(800), true, Player.STATE_ENDED);

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
