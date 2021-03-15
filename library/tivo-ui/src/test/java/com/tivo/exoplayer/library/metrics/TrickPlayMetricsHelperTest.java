package com.tivo.exoplayer.library.metrics;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.MimeTypes;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class TrickPlayMetricsHelperTest {
    static final Format TEST_BASEVIDEO_FORMAT = Format.createVideoSampleFormat(null,
            MimeTypes.VIDEO_H264, null, Format.NO_VALUE, Format.NO_VALUE, 1280, 720, Format.NO_VALUE,
            null, null);

    static final Format TEST_IFRAME_FORMAT = Format.createVideoContainerFormat(null, null, MimeTypes.VIDEO_H264, MimeTypes.VIDEO_H264, null,
            null, 1000, 1280, 720, 0.1f, null, 0, C.ROLE_FLAG_TRICK_PLAY);

    TrickPlayMetricsHelper testee;

    // Mock player to capture the current analytics listener
    @Mock
    private SimpleExoPlayer playerMock;

    // Mock TrickPlayControl to capture the TrickPlayEventListener
    @Mock
    private TrickPlayControl controlMock;

    @Mock
    private MetricsEventListener metricsEventListener;

    private final Clock clock = new FakeClock(100);

    @Before
    public void setupMocks() {
        MockitoAnnotations.initMocks(this);

        when(metricsEventListener.createEmptyTrickPlayMetrics(any(), any())).thenCallRealMethod();
        when(metricsEventListener.createEmptyPlaybackMetrics()).thenCallRealMethod();
    }

    @Test
    public void testTrickModeChangedForward() {
        createFastForwardPlayback(TrickPlayControl.TrickMode.FF1);

        // Callback we are entering trick-play mode.
        verify(metricsEventListener, atMost(1)).enteringTrickPlayMeasurement();

        // Verify listening, return value unused
        currentListeners();
    }

    @Test
    public void testTrickModeChangedForward_ThenNormal() {
        createFastForwardPlayback(TrickPlayControl.TrickMode.FF1);

        // Callback we are entering trick-play mode.
        verify(metricsEventListener).enteringTrickPlayMeasurement();

        // Verify listening, return value unused
        currentListeners();

        // Change back to normal and verify the trick-play session was ended
        TrickPlayMetrics metrics = returnToNormalFrom(TrickPlayControl.TrickMode.FF1);
        assertThat(metrics.getEndReason()).isEqualTo(PlaybackMetrics.EndReason.USER_ENDED);

    }

    @Test
    public void testTrickPlayModeChangedForward_IntraMode() {
        createFastForwardPlayback(TrickPlayControl.TrickMode.FF1);
        List<AnalyticsListener> listeners = currentListeners();
        for (AnalyticsListener listener : listeners) {
            listener.onDownstreamFormatChanged(testee.createEventTimeNow(), TrickPlayMetricsHelper.createMediaLoad(TEST_BASEVIDEO_FORMAT));
        }

        // Intra-mode change
        testee.trickPlayModeChanged(TrickPlayControl.TrickMode.FF2, TrickPlayControl.TrickMode.FF1);

        // Verify exit does not occur till trickmode returns to NORMAL
        verify(metricsEventListener, never()).exitingTrickPlayMeasurement();

        TrickPlayMetrics metrics = getLastEndedMetrics();
        assertThat(metrics.lastPlayedFormat()).isEqualTo(TEST_BASEVIDEO_FORMAT);
        assertThat(metrics.getEndReason()).isEqualTo(PlaybackMetrics.EndReason.USER_ENDED);

    }

    /**
     * Note this test covers logic in both {@link TrickPlayMetrics} and {@link TrickPlayMetricsHelper} that
     * measures and records i-Frame downloads.  A bit of a cheat to test TrickPlayMetrics from a test of the
     * TrickPlayMetricsHelper, but logic is in both classes
     */
    @Test
    public void testFrameLoadTimeMetrics() {
        // create a forward session
        createFastForwardPlayback(TrickPlayControl.TrickMode.FF1);

        // Post bandwidth numbers

        List<AnalyticsListener> listeners = currentListeners();

        long[] bytesLoaded = {1000, 2000, 1000, 1000, 2000};
        long[] loadTimeMs = {20, 20, 40, 200, 2000};

        for (int i=0; i < bytesLoaded.length; i++) {
            for (AnalyticsListener listener : listeners) {
                int elapsedRealTime = (i + 1) * 1000;
                listener.onLoadCompleted(createTestEventTime(elapsedRealTime),
                        createMockLoadData(elapsedRealTime, loadTimeMs[i], bytesLoaded[i]),
                        createMockLoadEvent(i * 1000));
            }
        }

        // Change back to NORMAL mode and verify metrics
        TrickPlayMetrics metrics = returnToNormalFrom(TrickPlayControl.TrickMode.FF1);

        assertThat(metrics.getArithmeticMeanFrameLoadTime()).isEqualTo(456.0f);
        assertThat(metrics.getMedianFrameLoadTime()).isEqualTo(40);
        assertThat(metrics.getEndReason()).isEqualTo(PlaybackMetrics.EndReason.USER_ENDED);
    }

    @Test
    public void testFrameLoadCancelCount() {
        createFastForwardPlayback(TrickPlayControl.TrickMode.FF1);

        // post cancels
        List<AnalyticsListener> listeners = currentListeners();
        for (AnalyticsListener listener : listeners) {
            listener.onLoadCanceled(createTestEventTime(100),
                    createMockLoadData(100, 100, 100),
                    createMockLoadEvent(100));
        }
        TrickPlayMetrics metrics = returnToNormalFrom(TrickPlayControl.TrickMode.FF1);
        assertThat(metrics.getTotalCanceledLoads()).isEqualTo(1);
    }

    private void createFastForwardPlayback(TrickPlayControl.TrickMode mode) {
        testee = new TrickPlayMetricsHelper(clock, playerMock, controlMock, metricsEventListener);
        setTrickPlayControlMockToMockMode(mode);
        testee.trickPlayModeChanged(mode, TrickPlayControl.TrickMode.NORMAL);
    }

    private TrickPlayMetrics returnToNormalFrom(TrickPlayControl.TrickMode previousMode) {
        testee.trickPlayModeChanged(TrickPlayControl.TrickMode.NORMAL, previousMode);
        verify(metricsEventListener).exitingTrickPlayMeasurement();
        TrickPlayMetrics metrics = getLastEndedMetrics();
        assertThat(metrics).isNotNull();
        return metrics;
    }

    private MediaSourceEventListener.MediaLoadData createMockLoadEvent(int startMs) {
        return new MediaSourceEventListener.MediaLoadData(0, C.TRACK_TYPE_VIDEO, TEST_IFRAME_FORMAT, 0, null, startMs, startMs + 1000);
    }

    private MediaSourceEventListener.LoadEventInfo createMockLoadData(long elapsedRealTimeMs, long loadDurationMs, long bytesLoaded) {
        return new MediaSourceEventListener.LoadEventInfo(null, null, null, elapsedRealTimeMs, loadDurationMs, bytesLoaded);
    }

    private TrickPlayMetrics getLastEndedMetrics() {
        ArgumentCaptor<TrickPlayMetrics> metricsArgCaptor;
        metricsArgCaptor = ArgumentCaptor.forClass(TrickPlayMetrics.class);
        verify(metricsEventListener).trickPlayMetricsAvailable(metricsArgCaptor.capture(), any());
        return metricsArgCaptor.getValue();
    }

    private List<AnalyticsListener> currentListeners() {
        ArgumentCaptor<AnalyticsListener> analyticsListenerArgumentCaptor;
        analyticsListenerArgumentCaptor = ArgumentCaptor.forClass(AnalyticsListener.class);
        verify(playerMock, atLeastOnce()).addAnalyticsListener(analyticsListenerArgumentCaptor.capture());
        return analyticsListenerArgumentCaptor.getAllValues();
    }

    private void setTrickPlayControlMockToMockMode(TrickPlayControl.TrickMode mode) {
        when(controlMock.getCurrentTrickDirection()).thenReturn(TrickPlayControl.TrickPlayDirection.FORWARD);
        when(controlMock.getCurrentTrickMode()).thenReturn(mode);
        when(controlMock.getSpeedFor(mode)).thenReturn(15.0f);  // fine for test purposes if all are same
    }

    private AnalyticsListener.EventTime createTestEventTime(long elapsedRealTime) {
        return new AnalyticsListener.EventTime(elapsedRealTime, Timeline.EMPTY, 0, null,
                0, 0, 0);
    }
}
