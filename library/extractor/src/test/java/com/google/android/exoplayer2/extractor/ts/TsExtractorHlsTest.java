package com.google.android.exoplayer2.extractor.ts;

import androidx.test.core.app.ApplicationProvider;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.testutil.Dumper;
import com.google.android.exoplayer2.testutil.ExtractorAsserts;
import com.google.android.exoplayer2.testutil.FakeExtractorInput;
import com.google.android.exoplayer2.testutil.FakeExtractorOutput;
import com.google.android.exoplayer2.testutil.FakeTrackOutput;
import com.google.android.exoplayer2.testutil.TestUtil;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import static com.google.common.truth.Truth.assertThat;

@RunWith(RobolectricTestRunner.class)
public class TsExtractorHlsTest {

  @Test
  public void consumeInitThenSingleIframe() throws Exception {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(123), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_iframe_init.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }
    FakeExtractorInput input =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_iframe.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(input, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        input.setPosition((int) seekPositionHolder.position);
      }
    }

    Dumper dumper = new Dumper();
    output.dump(dumper);
    System.out.print(dumper.toString());
  }

  @Test
  public void consumeIframeWithInit() throws Exception {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    final int firstSampleTimestampUs = 123;
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(firstSampleTimestampUs), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_iframe_withinit_one_frame.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }
//
//    Dumper dumper = new Dumper();
//    output.dump(dumper);
//    System.out.print(dumper.toString());

    assertThat(output.numberOfTracks).isEqualTo(2);
    FakeTrackOutput trackOutput = output.trackOutputs.get(27);
    assertThat(trackOutput).isNotNull();

    Format actualFormat = trackOutput.lastFormat;
    Format expectedFormat = new Format.Builder()
        .setId("1/27")
        .setSampleMimeType("video/avc")
        .setCodecs("avc1.64001F")
        .setWidth(1280)
        .setHeight(720)
        .setInitializationData(actualFormat.initializationData)   // Ignore this.
        .build();
    assertThat(actualFormat).isEqualTo(expectedFormat);

    // One sample, with the Access Unit containing the IDR and associated NALU is present
    trackOutput.assertSampleCount(1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(firstSampleTimestampUs);
    byte[] sampleData = trackOutput.getSampleData(0);
    assertThat(sampleData.length).isEqualTo(245323);
    assertThat(Arrays.hashCode(sampleData)).isEqualTo(0x4D1DE20C);
  }

  @Test
  public void consumeThreeH264IFrames() throws IOException {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    TsExtractor tsExtractor =
            new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(0), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_Ateme_iframe_init.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
    }

    FakeExtractorInput streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_Ateme_iframe_1.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    FakeTrackOutput trackOutput = output.trackOutputs.get(27);
    trackOutput.assertSampleCount(1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(0);

    streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_Ateme_iframe_2.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    trackOutput.assertSampleCount(2);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(2002000);

    streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_Ateme_iframe_3.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    trackOutput.assertSampleCount(3);
    assertThat(trackOutput.getSampleTimeUs(2)).isEqualTo(4004000);
  }
  @Test
  public void consumeThreeH265IFrames() throws IOException {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    TsExtractor tsExtractor =
            new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(0), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h265_Ateme_iframe_init.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
    }

    FakeExtractorInput streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h265_Ateme_iframe_1.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    FakeTrackOutput trackOutput = output.trackOutputs.get(36);
    trackOutput.assertSampleCount(1);
    long timeBaseUs = trackOutput.getSampleTimeUs(0);

    streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h265_Ateme_iframe_2.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    trackOutput.assertSampleCount(2);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(timeBaseUs + 6006000);

    streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h265_Ateme_iframe_3.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    trackOutput.assertSampleCount(3);
    assertThat(trackOutput.getSampleTimeUs(2)).isEqualTo(timeBaseUs + 12012000);
  }

  @Test
  public void consumeTwoH264Segments() throws IOException {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    TsExtractor tsExtractor =
            new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(0), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_Ateme_180_frames_1.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    PositionHolder seekPositionHolder = new PositionHolder();
    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    FakeTrackOutput trackOutput = output.trackOutputs.get(27);
    trackOutput.assertSampleCount(180);
    long openingPTS = trackOutput.getSampleTimeUs(0);

    streamInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_Ateme_180_frames_2.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
    }

    trackOutput.assertSampleCount(360);
    assertThat(trackOutput.getSampleTimeUs(180) - openingPTS).isEqualTo(6006000);
  }

  @Test
  public void consumeTwoH265IFramesWithInit() throws IOException {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    final int firstSampleTimestampUs = 123;
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(firstSampleTimestampUs), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_h265_iframe_patpmt.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }

    FakeExtractorInput streamInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_h265_iframe1.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();


    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        streamInput.setPosition((int) seekPositionHolder.position);
      }
    }

    assertThat(output.numberOfTracks).isEqualTo(2);     // There is an ID3 track in the SEI data
    FakeTrackOutput trackOutput = output.trackOutputs.get(36);   // The HEVC video is 1/36,
    assertThat(trackOutput).isNotNull();

    Format actualFormat = trackOutput.lastFormat;
    Format expectedFormat = new Format.Builder()
        .setId("1/36")
        .setSampleMimeType("video/hevc")
        .setCodecs("hvc1.1.6.L120.B0")
        .setWidth(1920)
        .setHeight(1080)
        .setInitializationData(actualFormat.initializationData)   // Ignore this.
        .build();
    assertThat(actualFormat).isEqualTo(expectedFormat);

    // One sample, with the Access Unit containing the IDR and associated NALU is present
    trackOutput.assertSampleCount(1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(33489);
    byte[] sampleData = trackOutput.getSampleData(0);
    assertThat(sampleData.length).isEqualTo(34918);
    assertThat(Arrays.hashCode(sampleData)).isEqualTo(0xF28680AE);

    // Subsequent iFrame only segment loads, on the same TsExtractor, should only
    // commit their sample, that is the opening AUD does not double commit the previous iFrame Sample


    streamInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_h265_iframe2.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();


    readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(streamInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        streamInput.setPosition((int) seekPositionHolder.position);
      }
    }

    // Second iFrame segment adds one more sample, so total is now 2
    trackOutput = output.trackOutputs.get(36);   // The HEVC video is 1/36,
    trackOutput.assertSampleCount(2);
    assertThat(trackOutput.getSampleTimeUs(1)).isEqualTo(6039489);
    sampleData = trackOutput.getSampleData(1);
    assertThat(sampleData.length).isEqualTo(35185);
    assertThat(Arrays.hashCode(sampleData)).isEqualTo(0xD1E04F03);
  }

  @Test
  public void consumeIFrameWithFillerData() throws Exception {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    final int firstSampleTimestampUs = 123;
    TsExtractor tsExtractor =
            new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(firstSampleTimestampUs), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
            new FakeExtractorInput.Builder()
                    .setData(
                            TestUtil.getByteArray(
                                    ApplicationProvider.getApplicationContext(),
                                    "media/ts/sample_h264_filler_ending.ts"))
                    .setSimulateIOErrors(false)
                    .setSimulateUnknownLength(false)
                    .setSimulatePartialReads(false)
                    .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }

    assertThat(output.numberOfTracks).isEqualTo(2);
    FakeTrackOutput trackOutput = output.trackOutputs.get(27);
    assertThat(trackOutput).isNotNull();

    Format actualFormat = trackOutput.lastFormat;
    Format expectedFormat = new Format.Builder()
            .setId("1/27")
            .setSampleMimeType("video/avc")
            .setCodecs("avc1.4D401E")
            .setWidth(640)
            .setHeight(360)
            .setInitializationData(actualFormat.initializationData)   // Ignore this.
            .build();
    assertThat(actualFormat).isEqualTo(expectedFormat);

    // One sample, with the Access Unit containing the IDR and associated NALU is present
    trackOutput.assertSampleCount(1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(firstSampleTimestampUs);
    byte[] sampleData = trackOutput.getSampleData(0);
    assertThat(sampleData.length).isEqualTo(46776);
    assertThat(Arrays.hashCode(sampleData)).isEqualTo(0xAA71C7C9);
  }

  @Test
  public void consumeIFrameWithMultipleNalUnits() throws Exception {
    TsPayloadReader.Factory factory = new DefaultTsPayloadReaderFactory();
    final int firstSampleTimestampUs = 123;
    TsExtractor tsExtractor =
        new TsExtractor(TsExtractor.MODE_HLS, new TimestampAdjuster(firstSampleTimestampUs), factory);
    FakeExtractorOutput output = new FakeExtractorOutput();
    tsExtractor.init(output);

    FakeExtractorInput initInput =
        new FakeExtractorInput.Builder()
            .setData(
                TestUtil.getByteArray(
                    ApplicationProvider.getApplicationContext(),
                    "media/ts/sample_h264_multi_nal.ts"))
            .setSimulateIOErrors(false)
            .setSimulateUnknownLength(false)
            .setSimulatePartialReads(false)
            .build();

    PositionHolder seekPositionHolder = new PositionHolder();

    int readResult = Extractor.RESULT_CONTINUE;
    while (readResult != Extractor.RESULT_END_OF_INPUT) {
      readResult = tsExtractor.read(initInput, seekPositionHolder);
      if (readResult == Extractor.RESULT_SEEK) {
        initInput.setPosition((int) seekPositionHolder.position);
      }
    }

    assertThat(output.numberOfTracks).isEqualTo(2);
    FakeTrackOutput trackOutput = output.trackOutputs.get(27);
    assertThat(trackOutput).isNotNull();

    Format actualFormat = trackOutput.lastFormat;
    Format expectedFormat = new Format.Builder()
        .setId("1/27")
        .setSampleMimeType("video/avc")
        .setCodecs("avc1.4D401F")
        .setWidth(960)
        .setHeight(540)
        .setInitializationData(actualFormat.initializationData)   // Ignore this.
        .build();
    assertThat(actualFormat).isEqualTo(expectedFormat);

    // One sample, with the Access Unit containing the IDR and associated NALU is present
    trackOutput.assertSampleCount(1);
    assertThat(trackOutput.getSampleTimeUs(0)).isEqualTo(firstSampleTimestampUs);
    byte[] sampleData = trackOutput.getSampleData(0);
    assertThat(sampleData.length).isEqualTo(55161);
    assertThat(Arrays.hashCode(sampleData)).isEqualTo(0x1FCF80F3);
  }
}
