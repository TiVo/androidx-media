/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.exoplayer.hls;

import static androidx.media3.test.utils.robolectric.RobolectricUtil.runMainLooperUntil;
import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import android.os.SystemClock;
import androidx.media3.common.MediaItem;
import androidx.media3.common.ParserException;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist;
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser;
import androidx.media3.test.utils.FakeDataSet;
import androidx.media3.test.utils.FakeDataSource;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit test for {@link HlsMediaSource}. */
@RunWith(AndroidJUnit4.class)
public class HlsMediaSourceDefaultStartOffsetTest {

  @Test
  // This is the only case in which defaultStartOffset has an effect.
  public void loadLivePlaylist_noTargetLiveOffset_noExtXStart()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds but not hold back or part hold back.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:CAN-SKIP-UNTIL=24";
    // The playlist finishes 1 second before the current time, therefore there's a live edge
    // offset of 1 second.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    factory.setDefaultStartOffset(
        -10_000_000); // this affects both targetLiveOffset and startOffset
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from defaultStartOffset (10 seconds) and then expressed
    // in relation to the live edge (10 + 1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(11000);
    // The window start is picked from defaultStartOffset (16 - 10 = 6 seconds) and then snapped
    // to the nearest preceding segment boundary (precise = no)
    assertThat(window.defaultPositionUs).isEqualTo(4000000);
  }

  @Test
  public void loadLivePlaylist_noTargetLiveOffset_withExtXStart()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds but not hold back or part hold back.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-START:TIME-OFFSET=-2\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:CAN-SKIP-UNTIL=24";
    // The playlist finishes 1 second before the current time, therefore there's a live edge
    // offset of 1 second.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    factory.setDefaultStartOffset(-10_000_000); // ignored, because EXT-X-START
    MediaItem mediaItem = MediaItem.fromUri(playlistUri);
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from EXT-X-START (-2 seconds) and then expressed
    // in relation to the live edge (2 + 1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(3000);
    // The window start is picked from EXT-X-START (16 - 2 = 14 seconds) and then snapped
    // to the nearest preceding segment boundary (precise = no)
    assertThat(window.defaultPositionUs).isEqualTo(12000000);
  }

  @Test
  public void loadLivePlaylist_withTargetLiveOffset_noExtXStart()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds but not hold back or part hold back.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:CAN-SKIP-UNTIL=24";
    // The playlist finishes 1 second before the current time, therefore there's a live edge
    // offset of 1 second.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    factory.setDefaultStartOffset(-10_000_000); // ignored, because setLiveTargetOffset below
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(playlistUri).setLiveTargetOffsetMs(7000).build();
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from setLiveTargetOffsetMs() (3 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(7000);
    // The window start is picked from targetOffset and then snapped
    // to the nearest preceding segment boundary (precise = no)
    assertThat(window.defaultPositionUs).isEqualTo(8000000);
  }

  @Test
  public void loadLivePlaylist_withTargetLiveOffset_withExtXStart()
      throws TimeoutException, ParserException {
    String playlistUri = "fake://foo.bar/media0/playlist.m3u8";
    // The playlist has a duration of 16 seconds but not hold back or part hold back.
    String playlist =
        "#EXTM3U\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2020-01-01T00:00:00.0+00:00\n"
            + "#EXT-X-TARGETDURATION:4\n"
            + "#EXT-X-VERSION:3\n"
            + "#EXT-X-START:TIME-OFFSET=-6\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence0.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence1.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence2.ts\n"
            + "#EXTINF:4.00000,\n"
            + "fileSequence3.ts\n"
            + "#EXT-X-SERVER-CONTROL:CAN-SKIP-UNTIL=24";
    // The playlist finishes 1 second before the current time, therefore there's a live edge
    // offset of 1 second.
    SystemClock.setCurrentTimeMillis(Util.parseXsDateTime("2020-01-01T00:00:17.0+00:00"));
    HlsMediaSource.Factory factory = createHlsMediaSourceFactory(playlistUri, playlist);
    factory.setDefaultStartOffset(-10_000_000); // ignored
    MediaItem mediaItem =
        new MediaItem.Builder().setUri(playlistUri).setLiveTargetOffsetMs(3000).build();
    HlsMediaSource mediaSource = factory.createMediaSource(mediaItem);

    Timeline timeline = prepareAndWaitForTimeline(mediaSource);

    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
    // The target live offset is picked from defaultStartOffset (10 seconds) and then expressed
    // in relation to the live edge (10 + 1 seconds).
    assertThat(window.liveConfiguration.targetOffsetMs).isEqualTo(3000);
    // The window start is picked from EXT-X-START (16 - 6 = 10 seconds) and then snapped
    // to the nearest preceding segment boundary (precise = no)
    assertThat(window.defaultPositionUs).isEqualTo(8000000);
  }

  private static HlsMediaSource.Factory createHlsMediaSourceFactory(
      String playlistUri, String playlist) {
    FakeDataSet fakeDataSet = new FakeDataSet().setData(playlistUri, Util.getUtf8Bytes(playlist));
    return new HlsMediaSource.Factory(
            dataType -> new FakeDataSource.Factory().setFakeDataSet(fakeDataSet).createDataSource())
        .setElapsedRealTimeOffsetMs(0);
  }

  /** Prepares the media source and waits until the timeline is updated. */
  private static Timeline prepareAndWaitForTimeline(HlsMediaSource mediaSource)
      throws TimeoutException {
    AtomicReference<Timeline> receivedTimeline = new AtomicReference<>();
    mediaSource.prepareSource(
        (source, timeline) -> receivedTimeline.set(timeline), /* mediaTransferListener= */ null);
    runMainLooperUntil(() -> receivedTimeline.get() != null);
    return receivedTimeline.get();
  }

  private static HlsMediaPlaylist parseHlsMediaPlaylist(String playlistUri, String playlist)
      throws IOException {
    return (HlsMediaPlaylist)
        new HlsPlaylistParser()
            .parse(Uri.parse(playlistUri), new ByteArrayInputStream(Util.getUtf8Bytes(playlist)));
  }
}
