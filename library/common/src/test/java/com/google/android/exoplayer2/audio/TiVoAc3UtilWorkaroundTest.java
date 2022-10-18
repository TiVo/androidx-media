/*
 * Copyright 2022 TiVo Inc. All rights reserved.
 */
package com.google.android.exoplayer2.audio;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.util.ParsableByteArray;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link Ac3Util}. */
@RunWith(AndroidJUnit4.class)
public final class TiVoAc3UtilWorkaroundTest {
  // Test coverage is being provided for the workaround put in place for PARTDEFECT-12865
  // and PARTDEFECT-11478. If any of those defects are fixed, this file can be safely removed along
  // with the workaround in Ac3Util.java

  @Test
  public void parseAc3AnnexFFormat_5Chan_lfeon_set() {
    // AC3 5 channel audio with lfeon (low freq effects a.k.a. subwoofer) bit set
    assertThat(Ac3Util.parseAc3AnnexFFormat(new ParsableByteArray(new byte[] {0x10, 0x3D}),
            "", "", null).channelCount).isEqualTo(6);
  }

  @Test
  public void parseAc3AnnexFFormat_5Chan_lfeon_reset() {
    // AC3 5 channel audio with lfeon (low freq effects a.k.a. subwoofer) bit reset
    assertThat(Ac3Util.parseAc3AnnexFFormat(new ParsableByteArray(new byte[] {0x10, 0x39}),
            "", "", null).channelCount).isEqualTo(6);
  }
}
