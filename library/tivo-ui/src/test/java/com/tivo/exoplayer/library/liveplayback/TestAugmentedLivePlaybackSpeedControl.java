package com.tivo.exoplayer.library.liveplayback;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLivePlaybackSpeedControl;
import com.google.android.exoplayer2.MediaItem;
import com.tivo.exoplayer.library.logging.LoggingLivePlaybackSpeedControl;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class TestAugmentedLivePlaybackSpeedControl {

  public static final int TARGET_OFFSET_MS = 30_000;
  private MediaItem.LiveConfiguration testConfiguration =
      new MediaItem.LiveConfiguration(TARGET_OFFSET_MS, TARGET_OFFSET_MS, TARGET_OFFSET_MS, 0.7f, 1.3f);

  @Test
  public void testSetOverrideOutOfLive_initial_state() {
    AugmentedLivePlaybackSpeedControl tested = createTestObject();
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));

    // Speed up because behind target
    assertThat(tested.getAdjustedPlaybackSpeed(C.msToUs(TARGET_OFFSET_MS + 2_000), 1000)).isGreaterThan(1.0f);
  }

  @Test
  public void testSetOverrideOutOfLive_setDefault() {
    AugmentedLivePlaybackSpeedControl tested = createTestObject();
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));

    // Set live offset out of range that allows adjustment
    long liveOffsetUs = C.msToUs(AugmentedLivePlaybackSpeedControl.MAX_LIVE_OFFSET_DELTA_MS + TARGET_OFFSET_MS + 1);
    tested.setTargetLiveOffsetOverrideUs(liveOffsetUs);

    // AugmentedLivePlaybackSpeedControl disabled adjustment, so returns unset for getTargetLiveOffset and speed 1.0
    //
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.TIME_UNSET);
    assertThat(tested.getAdjustedPlaybackSpeed(C.msToUs(liveOffsetUs + 2_000), 1_000)).isEqualTo(1.0f);
    assertThat(tested.getAdjustedPlaybackSpeed(C.msToUs(liveOffsetUs - 1_000), 1_000)).isEqualTo(1.0f);

    // Restore, like re-tune or Player.seekToDefaultPosition() would do
    tested.setTargetLiveOffsetOverrideUs(C.TIME_UNSET);

    // Speed up if behind target and original target now returend
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));
    assertThat(tested.getAdjustedPlaybackSpeed(C.msToUs(TARGET_OFFSET_MS + 2_000), 1000)).isGreaterThan(1.0f);
  }


  @Test
  public void testSetTargetLiveOffsetOverrideUs_outside_of_live_stops_adjust() {
    AugmentedLivePlaybackSpeedControl tested = createTestObject();
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));

    // Set live offset out of range that allows adjustment
    long liveOffsetUs = C.msToUs(AugmentedLivePlaybackSpeedControl.MAX_LIVE_OFFSET_DELTA_MS + TARGET_OFFSET_MS + 1);
    tested.setTargetLiveOffsetOverrideUs(liveOffsetUs);

    // AugmentedLivePlaybackSpeedControl disabled adjustment, so returns unset for getTargetLiveOffset and speed 1.0
    //
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.TIME_UNSET);
    assertThat(tested.getAdjustedPlaybackSpeed(C.msToUs(liveOffsetUs + 2_000), 1_000)).isEqualTo(1.0f);
    assertThat(tested.getAdjustedPlaybackSpeed(C.msToUs(liveOffsetUs - 1_000), 1_000)).isEqualTo(1.0f);
  }


  @Test
  public void testSetTargetLiveOffsetOverrideUs_back_into_live_restarts_adjust() {
    AugmentedLivePlaybackSpeedControl tested = createTestObject();
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));

    // Set live offset out of range that allows adjustment, this is the initial seek back
    long liveOffsetUs = C.msToUs(AugmentedLivePlaybackSpeedControl.MAX_LIVE_OFFSET_DELTA_MS + TARGET_OFFSET_MS + 1);
    tested.setTargetLiveOffsetOverrideUs(liveOffsetUs);

    // AugmentedLivePlaybackSpeedControl disabled adjustment, so returns unset for getTargetLiveOffset and speed 1.0
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.TIME_UNSET);

    // Seek back to within live, and should restore original target live offset
    liveOffsetUs = C.msToUs(AugmentedLivePlaybackSpeedControl.MAX_LIVE_OFFSET_DELTA_MS + TARGET_OFFSET_MS - 5_000);
    tested.setTargetLiveOffsetOverrideUs(liveOffsetUs);

    // Also, live adjustment should happen if not at the target
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));

  }

  @Test
  public void testSetTargetLiveOffsetOverrideUs_without_min_max() {
    testConfiguration = new MediaItem.LiveConfiguration(TARGET_OFFSET_MS, C.TIME_UNSET, C.TIME_UNSET, 0.7f, 1.3f);
    AugmentedLivePlaybackSpeedControl tested = createTestObject();

    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));

    // Set live offset out of range that allows adjustment, this is the initial seek back
    long liveOffsetUs = C.msToUs(AugmentedLivePlaybackSpeedControl.MAX_LIVE_OFFSET_DELTA_MS + TARGET_OFFSET_MS + 1);
    tested.setTargetLiveOffsetOverrideUs(liveOffsetUs);

    // AugmentedLivePlaybackSpeedControl disabled adjustment, so returns unset for getTargetLiveOffset and speed 1.0
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.TIME_UNSET);

    // Seek back to within live, and should restore original target live offset
    liveOffsetUs = C.msToUs(AugmentedLivePlaybackSpeedControl.MAX_LIVE_OFFSET_DELTA_MS + TARGET_OFFSET_MS - 5_000);
    tested.setTargetLiveOffsetOverrideUs(liveOffsetUs);

    // Also, live adjustment should happen if not at the target
    assertThat(tested.getTargetLiveOffsetUs()).isEqualTo(C.msToUs(TARGET_OFFSET_MS));
  }



    private AugmentedLivePlaybackSpeedControl createTestObject() {
    AugmentedLivePlaybackSpeedControl livePlaybackSpeedControl =
        new AugmentedLivePlaybackSpeedControl(
            new LoggingLivePlaybackSpeedControl(
                new DefaultLivePlaybackSpeedControl.Builder().build())
        );

    livePlaybackSpeedControl.setLiveConfiguration(testConfiguration);
    return livePlaybackSpeedControl;
  }


}
