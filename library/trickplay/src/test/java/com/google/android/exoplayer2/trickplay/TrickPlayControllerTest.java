package com.google.android.exoplayer2.trickplay;


import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import static com.google.common.truth.Truth.assertThat;

@RunWith(AndroidJUnit4.class)
public class TrickPlayControllerTest {

  @Test
  public void test_getTargetFrameRateForPlaybackSpeed() {
    Context context = ApplicationProvider.getApplicationContext();
    DefaultTrackSelector mockSelector = new DefaultTrackSelector(context);
    TrickPlayController testee = new TrickPlayController(mockSelector);

    assertThat(testee.getTargetFrameRateForPlaybackSpeed(testee.getSpeedFor(TrickPlayControl.TrickMode.FR1))).isEqualTo(3);
    assertThat(testee.getTargetFrameRateForPlaybackSpeed(testee.getSpeedFor(TrickPlayControl.TrickMode.FR2))).isEqualTo(4);
    assertThat(testee.getTargetFrameRateForPlaybackSpeed(testee.getSpeedFor(TrickPlayControl.TrickMode.FR3))).isEqualTo(5);

    assertThat(testee.getTargetFrameRateForPlaybackSpeed(testee.getSpeedFor(TrickPlayControl.TrickMode.FF1))).isEqualTo(4);
    assertThat(testee.getTargetFrameRateForPlaybackSpeed(testee.getSpeedFor(TrickPlayControl.TrickMode.FF2))).isEqualTo(5);
    assertThat(testee.getTargetFrameRateForPlaybackSpeed(testee.getSpeedFor(TrickPlayControl.TrickMode.FF3))).isEqualTo(7);

  }
}
