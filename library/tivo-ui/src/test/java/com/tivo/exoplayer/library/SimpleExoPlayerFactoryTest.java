package com.tivo.exoplayer.library;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.source.MaskingMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.testutil.ExoPlayerTestRunner;
import com.google.android.exoplayer2.testutil.FakeMediaSource;
import com.google.android.exoplayer2.testutil.FakeRenderer;
import com.google.android.exoplayer2.testutil.FakeTimeline;
import com.google.android.exoplayer2.testutil.TestExoPlayerBuilder;
import com.google.common.truth.Expect;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.tivo.exoplayer.library.tracks.TrackInfo;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for the TiVo {@link SimpleExoPlayerFactory}
 */
@RunWith(AndroidJUnit4.class)
public class SimpleExoPlayerFactoryTest {

  private Context context;
  private FakeRenderer[] renderers;
  private Format[] fakeFormats;
  private Timeline timeline;
  private String localeDefaultLang;
  private SimpleExoPlayer player;
  private TestExoPlayerBuilder testExoPlayerBuilder;

  @Before
  public void setUp() throws TimeoutException {
    context = ApplicationProvider.getApplicationContext();
    timeline = new FakeTimeline();
    renderers = new FakeRenderer[]{
        new FakeRenderer(C.TRACK_TYPE_VIDEO),
        new FakeRenderer(C.TRACK_TYPE_AUDIO),
        new FakeRenderer(C.TRACK_TYPE_TEXT)
    };
    localeDefaultLang = Locale.getDefault().getLanguage();
    fakeFormats = new Format[]{
        ExoPlayerTestRunner.VIDEO_FORMAT,
        ExoPlayerTestRunner.AUDIO_FORMAT.buildUpon().setLanguage(localeDefaultLang).build(),
        ExoPlayerTestRunner.AUDIO_FORMAT.buildUpon().setLanguage("zz").build(),
        new Format.Builder().setSampleMimeType("application/cea-608")
            .setLanguage(localeDefaultLang).build(),
        new Format.Builder().setSampleMimeType("text/vtt").setLanguage("zz").build()
    };

    testExoPlayerBuilder = new TestExoPlayerBuilder(context);
    player = testExoPlayerBuilder.setRenderers(renderers).build();
    player.setMediaSource(new FakeMediaSource(timeline, fakeFormats));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    TrackGroupArray availableTrackGroups = player.getCurrentTrackGroups();
    assertThat(availableTrackGroups.length).isEqualTo(5);
  }

  @Test
  public void test_getAvailableAudioTracks_selectTrack() throws TimeoutException {
    SimpleExoPlayerFactory factory = new SimpleExoPlayerFactory.Builder(context).build();
    factory.injectForTesting(player, testExoPlayerBuilder.getTrackSelector());

    List<TrackInfo> audios = factory.getAvailableAudioTracks();
    assertThat(audios.size()).isEqualTo(2);
    TrackInfo unselected = null;
    for (TrackInfo info : audios) {
      if (localeDefaultLang.equals(info.format.language)) {
        assertWithMessage("Locale default (%s) audio is selected default", localeDefaultLang)
            .that(info.isSelected).isTrue();
      } else {
        assertThat(info.isSelected).isFalse();
        unselected = info;
      }
    }

    // Test selectTrack toggles the selection to only the previous unselected track
    factory.selectTrack(unselected);
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    audios = factory.getAvailableAudioTracks();
    int selectedCount = 0;
    for (TrackInfo info : audios) {
      if (info.isSelected) {
        selectedCount++;
        assertThat(info.format.language).isEqualTo(unselected.format.language);
      }
    }
    assertThat(selectedCount).isEqualTo(1);
  }

  @Test
  public void test_getAvailableTextTracks() throws TimeoutException {
    SimpleExoPlayerFactory factory = new SimpleExoPlayerFactory.Builder(context).build();
    factory.injectForTesting(player, testExoPlayerBuilder.getTrackSelector());

    List<TrackInfo> textTracks = factory.getAvailableTextTracks();
    assertThat(textTracks.size()).isEqualTo(2);

    textTracks = factory.getAvailableTextTracks();
    int selectedCount = 0;
    for (TrackInfo info : textTracks) {
      if (info.isSelected) {
        selectedCount++;
      }
    }
    // The default for text is not to enable any tracks
    assertThat(selectedCount).isEqualTo(0);
  }
}