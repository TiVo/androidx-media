/*
 * Copyright (C) 2016 The Android Open Source Project
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
package androidx.media3.exoplayer.hls.playlist;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for {@link HlsMediaPlaylistParser} for validation of Vecima playlist update logic this is
 * test cases for the TiVo added code isUpdateValid()
 */
@RunWith(AndroidJUnit4.class)
public class HlsMediaPlaylistParserTest_Validation {

  @Test
  public void testPlaylistUpdateValid_truncateToMs() throws IOException {
    Uri mockUrl = Uri.parse("https://example.com/test3.m3u8");
    String playlistOld =
        "#EXTM3U\n"
            + "#EXT-X-VERSION:8\n"
            + "#EXT-X-MEDIA-SEQUENCE:274575440\n"
            + "#EXT-X-TARGETDURATION:8\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2022-04-04T19:21:32.646Z\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100092646000~D6016000.tsa\n"
            + "#EXTINF:5.994667,\n"
            + "CCURStream_5_1_AAC-10_T1649100098662000~D5994666.tsa\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100104656666~D6016000.tsa\n";

    String playlistNew =
        "#EXTM3U\n"
            + "#EXT-X-VERSION:8\n"
            + "#EXT-X-MEDIA-SEQUENCE:274575442\n"
            + "#EXT-X-TARGETDURATION:8\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2022-04-04T19:21:44.656Z\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100104656666~D6016000.tsa\n"
            + "#EXTINF:5.994667,\n"
            + "CCURStream_5_1_AAC-10_T1649100110672666~D5994666.tsa\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100116667333~D6016000.tsa\n"
            + "#EXTINF:5.994667,\n";

    InputStream inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(playlistOld));
    HlsMediaPlaylist oldPlaylist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(mockUrl, inputStream);
    inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(playlistNew));
    HlsMediaPlaylist newPlaylist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(mockUrl, inputStream);

    assertThat(newPlaylist.isNewerThan(oldPlaylist)).isTrue();
    assertThat(newPlaylist.isUpdateValid(oldPlaylist)).isTrue();
  }

  @Test
  public void testPlaylistUpdateValid_failBadMediaSequence() throws IOException {
    Uri mockUrl = Uri.parse("https://example.com/test3.m3u8");
    String playlistOld =
        "#EXTM3U\n"
            + "#EXT-X-VERSION:8\n"
            + "#EXT-X-MEDIA-SEQUENCE:274575441\n"
            + "#EXT-X-TARGETDURATION:8\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2022-04-04T19:21:32.646Z\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100092646000~D6016000.tsa\n"
            + "#EXTINF:5.994667,\n"
            + "CCURStream_5_1_AAC-10_T1649100098662000~D5994666.tsa\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100104656666~D6016000.tsa\n";

    String playlistNew =
        "#EXTM3U\n"
            + "#EXT-X-VERSION:8\n"
            + "#EXT-X-MEDIA-SEQUENCE:274575442\n"
            + "#EXT-X-TARGETDURATION:8\n"
            + "#EXT-X-PROGRAM-DATE-TIME:2022-04-04T19:21:44.656Z\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100104656666~D6016000.tsa\n"
            + "#EXTINF:5.994667,\n"
            + "CCURStream_5_1_AAC-10_T1649100110672666~D5994666.tsa\n"
            + "#EXTINF:6.016000,\n"
            + "CCURStream_5_1_AAC-10_T1649100116667333~D6016000.tsa\n"
            + "#EXTINF:5.994667,\n";

    InputStream inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(playlistOld));
    HlsMediaPlaylist oldPlaylist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(mockUrl, inputStream);
    inputStream = new ByteArrayInputStream(Util.getUtf8Bytes(playlistNew));
    HlsMediaPlaylist newPlaylist =
        (HlsMediaPlaylist) new HlsPlaylistParser().parse(mockUrl, inputStream);

    assertThat(newPlaylist.isNewerThan(oldPlaylist)).isTrue();
    assertThat(newPlaylist.isUpdateValid(oldPlaylist)).isFalse();
  }
}
