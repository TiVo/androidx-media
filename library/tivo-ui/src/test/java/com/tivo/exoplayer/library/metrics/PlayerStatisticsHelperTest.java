package com.tivo.exoplayer.library.metrics;

import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.decoder.DecoderCounters;


import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;
import static com.tivo.exoplayer.library.metrics.PlaybackStatsExtensionTest.createEventTime;
import static org.mockito.Mockito.when;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.trickplay.TrickPlayControl;

/**
 * Test the {@link PlayerStatisticsHelper} that manages accumulation of metrics that are not part
 *  * of {@link com.google.android.exoplayer2.analytics.PlaybackStats} but instead managed by the
 *  * player core, for example {@link DecoderCounters}.
 *
 * The strategy is to Mock the {@link SimpleExoPlayer}
 */
@RunWith(AndroidJUnit4.class)
public class PlayerStatisticsHelperTest {

  // Object being tested.
  private PlayerStatisticsHelper testee;

  @Mock
  private SimpleExoPlayer playerMock;
  PlaybackMetrics pbMetrics = new PlaybackMetrics();

  private DecoderCounters decoderCountersTest = new DecoderCounters();
  private DecoderCounters decoderCountersTP = new DecoderCounters();

  private DecoderCounters current(int val) {
    decoderCountersTest.renderedOutputBufferCount = val;
    return decoderCountersTest;
  }

  private DecoderCounters currentTP(int val) {
    decoderCountersTP.renderedOutputBufferCount = val;
    return decoderCountersTP;
  }

  @Before
  public void setupMocksAndTestee() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testPlayback() {
    testee = new PlayerStatisticsHelper();

    //Current: 10 playback frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(10));
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);
  }

  @Test
  public void testOnVideoDisabled() {
    testee = new PlayerStatisticsHelper();
    //Current: 10 playback frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(10));
    AnalyticsListener.EventTime eventTime = createEventTime(0);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());
    // Stored: 10  + Current: 10  playback frames
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(20);
  }

  @Test
  public void testMergeCounterValues() {
    testee = new PlayerStatisticsHelper();
    //Current: 10 playback frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(10));
    testee.mergeCounterValues(playerMock.getVideoDecoderCounters());
    // Stored: 10  + Current: 10  playback frames
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(20);
  }

  @Test
  public void testPlaybackAndEnteringTrickPlay() {
    testee = new PlayerStatisticsHelper();
    //Current: 10 playback frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(10));
    AnalyticsListener.EventTime eventTime = createEventTime(0);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());
    testee.enterTrickPlay(playerMock.getVideoDecoderCounters());//enter trickplay with 10 frames
    // Stored: 10 playback frames (Current: 10 trickplay frames not included, since we are in trickplay)
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);
  }

  @Test
  public void testPlaybackAndTrickPlay() {
    testee = new PlayerStatisticsHelper();
    //Current: 10 playback frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(100));
    AnalyticsListener.EventTime eventTime = createEventTime(0);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());
    testee.enterTrickPlay(playerMock.getVideoDecoderCounters()); //enter trickplay with 100 frames
    // Stored: 100 playback frames (Current: 10 trickplay frames not included, since we are in trickplay)
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(100);

    when(playerMock.getVideoDecoderCounters()).thenReturn(current(0));
    eventTime = createEventTime(10);
    testee.onVideoEnabled(eventTime, playerMock.getVideoDecoderCounters());

    //Current 20 playback frames(includes 10 TP frames)
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(12));
    testee.exitTrickPlay(playerMock.getVideoDecoderCounters());// exit trickplay with 12 frames

    // Stored: (100 playback frames - TP frames between exit and enter: 12)+ Current: 12 playback frames
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(100);

    // Stored: 0 playback frames + Current: 20
    eventTime = createEventTime(10);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());

    // Stored: 20 playback frames  + Current: 20
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(112);
  }

  @Test
  public void testPlaybackATrickPlayPlayback() {
    testee = new PlayerStatisticsHelper();

    when(playerMock.getVideoDecoderCounters()).thenReturn(current(10));
    AnalyticsListener.EventTime eventTime = createEventTime(0);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());
    testee.enterTrickPlay(playerMock.getVideoDecoderCounters());

    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);

    //Increment TP frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(20));
    testee.exitTrickPlay(playerMock.getVideoDecoderCounters());

    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);

    //increment playback frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(30));
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(20);

  }

  @Test
  public void testPlaybackTrickPlayTrickPlayPlayback() {
    testee = new PlayerStatisticsHelper();

    when(playerMock.getVideoDecoderCounters()).thenReturn(current(10));
    AnalyticsListener.EventTime eventTime = createEventTime(0);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());
    testee.enterTrickPlay(playerMock.getVideoDecoderCounters());
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);

    //increment TP frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(5));
    testee.exitTrickPlay(playerMock.getVideoDecoderCounters());

    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);

    eventTime = createEventTime(10);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());
    testee.enterTrickPlay(playerMock.getVideoDecoderCounters());
    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);

    //increment TP frames
    when(playerMock.getVideoDecoderCounters()).thenReturn(current(5));
    testee.exitTrickPlay(playerMock.getVideoDecoderCounters());

    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(10);

    eventTime = createEventTime(20);
    testee.onVideoDisabled(eventTime, playerMock.getVideoDecoderCounters());

    assertThat(testee.getVideoFramesRendered(playerMock.getVideoDecoderCounters())).isEqualTo(15);

  }
}
