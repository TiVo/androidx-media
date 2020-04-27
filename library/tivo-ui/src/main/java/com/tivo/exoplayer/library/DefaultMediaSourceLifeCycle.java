package com.tivo.exoplayer.library;


import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Log;
import com.tivo.exoplayer.ext.vcas.VcasDataSourceFactory;

import java.io.File;

/**
 * Manages creation and the lifecycle of playback of an ExoPlayer {@link MediaSource}
 *
 * The ExoPlayer MediaSource connects an ExoPlayer playback session to the data source (for
 * HLS this is an HTTP URL).
 *
 * The {@link SimpleExoPlayer#prepare(MediaSource)} starts the mediasource loading, or reloads it in
 * the event of a recoverable playback error (eg {@link com.google.android.exoplayer2.source.BehindLiveWindowException}
 *
 * This object manages the life-cycle of playback and error handling for a give URL
 */
public class DefaultMediaSourceLifeCycle implements MediaSourceLifeCycle, AnalyticsListener {

  private static final String TAG = "ExoPlayer";

  protected final SimpleExoPlayer player;
  protected final Context context;
  protected MediaSource currentMediaSource;

  @Nullable
  private MediaSourceEventCallback callback;

  private boolean isInitialMediaSourceEvent;

  /**
   * Construct the default implementation of {@link MediaSourceLifeCycle}
   *
   * @param player - SimpleExoPlayer to build mediasources for
   * @param context - android context
   */
  public DefaultMediaSourceLifeCycle(SimpleExoPlayer player, Context context) {
    this.player = player;
    this.context = context;

    player.addAnalyticsListener(this);
  }

  @Override
  public void playUrl(Uri uri, boolean enableChunkless) {
    Log.d("ExoPlayer", "play URL " + uri);

    MediaSource mediaSource = buildMediaSource(uri, buildDataSourceFactory(), enableChunkless);
    playMediaSource(mediaSource);
  }

  protected void playMediaSource(MediaSource mediaSource) {
    player.stop(true);
    currentMediaSource = mediaSource;
    isInitialMediaSourceEvent = false;
    player.setPlayWhenReady(true);
    player.prepare(currentMediaSource);
  }


  @Override
  public void setMediaSourceEventCallback(@Nullable MediaSourceEventCallback callback) {
    this.callback = callback;
  }

  /**
   * Construct and return the Factory class for {@link DataSource} objects.
   *
   * The DataSource is used to build a {@link MediaSource} for loading and playing a
   * URL.
   *
   * TODO - will need DRM type parameter to allow creating factory for VCAS
   *
   * @return factory to produce DataSource objects.
   */
  protected DataSource.Factory buildDataSourceFactory() {
    String userAgent = getUserAgentPrefix();

    userAgent += "-" + ExoPlayerLibraryInfo.VERSION;


    HttpDataSource.Factory upstreamFactory = new DefaultHttpDataSourceFactory(userAgent);
    DataSource.Factory factory = new DefaultDataSourceFactory(context, upstreamFactory);

// TODO - integrate the VCAS DRM, standalone applications may need to verify files access, hopefull
// will not need the native library directory
//
// Create the singleton "Verimatrix DRM" data source factory
    if (Build.VERSION.SDK_INT >= 22) {
      String filesDir = "/sdcard/demoVR/";
      File folder = new File(filesDir);
      if (!folder.exists()) {
        folder.mkdirs();
      }
      VcasDataSourceFactory vfactory = new VcasDataSourceFactory(upstreamFactory,
              filesDir,
              context.getApplicationInfo().nativeLibraryDir,
              context.getApplicationInfo().sourceDir,
              true);
      vfactory.prepareForPlayback("addaf9ae-846f-34ad-a5d0-4f9f75f741bc", "devvcas04.engr.tivo.com:8042");
      factory = vfactory;
    }

  return factory;
  }

  /**
   * Subclass can override this to add an application specific UserAgent prefix.
   *
   * @return useragent string, version of ExoPlayer library postfix to this.
   */
  protected String getUserAgentPrefix() {
    return "TiVoExoPlayer";
  }


  /**
   * Built the MediaSource for the Uri (assumed for now to be HLS)
   *
   * The player glue layer should save this for error recovery (if re-prepare is required)
   *
   * @param uri - URL for the HLS master playlist
   * @param dataSourceFactory - factory to create a {@link DataSource} to load data from the HLS resouces
   * @param enableChunkless - if true HLS will prepare 'chunkless'
   *          See <a href="https://medium.com/google-exoplayer/faster-hls-preparation-f6611aa15ea6">Faster HLS Prepare</a>
   * @return an {@link MediaSource} ready to pass to {@link com.google.android.exoplayer2.SimpleExoPlayer#prepare(MediaSource)}
   */
  protected MediaSource buildMediaSource(Uri uri, DataSource.Factory dataSourceFactory,
      boolean enableChunkless) {
    HlsMediaSource.Factory factory = new HlsMediaSource.Factory(dataSourceFactory);

    // TODO - integrate VCAS DRM
    //
    //    // Create the singleton "Verimatrix DRM" media source factory
    //    gVerimatrixMediaSourceFactory = new HlsMediaSource.Factory
    //        (gVerimatrixDataSourceFactory);

    return factory.setAllowChunklessPreparation(enableChunkless).createMediaSource(uri);
  }

  @Override
  public boolean recoverFrom(ExoPlaybackException e) {
    boolean value = false;

    if (isBehindLiveWindow(e)) {
      // BehindLiveWindowException occurs when the current play point is in
      // a segment that has expired from the server.  This happens if the play point
      // falls behind the oldest segment in a live playlist.
      //
      // Recovery action is to re-prepare the current playing MediaSource.
      // The player, will then jump to the earliest available segment,
      // which is where we want to be anyway.  If not we can prepare with keep position
      //
      Log.d(TAG, "Got BehindLiveWindowException, re-preparing " +
          "player to resume playback: " + e);

      player.prepare(currentMediaSource, true, true);
      value = true;
    }
    return value;
  }

  @Override
  public boolean restartPlaybackAtLastPosition() {
    player.prepare(currentMediaSource, false, true);
    return true;
  }


  private static boolean isBehindLiveWindow(ExoPlaybackException e) {
    if (e.type != ExoPlaybackException.TYPE_SOURCE) {
      return false;
    }
    Throwable cause = e.getSourceException();
    while (cause != null) {
      if (cause instanceof BehindLiveWindowException) {
        return true;
      }
      cause = cause.getCause();
    }
    return false;
  }

  /**
   * After the player reads the initial M3u8 and parses it, the timeline is created.
   * Report the first such of these events, following a {@link MediaSource} change
   * (via {@link #playUrl(Uri, boolean)} call to any {@link MediaSourceEventCallback} listener
   *
   * @param eventTime The event time.
   * @param reason The reason for the timeline change.
   */
  @Override
  public void onTimelineChanged(EventTime eventTime, int reason) {
    if (reason == Player.TIMELINE_CHANGE_REASON_PREPARED) {
      if (!isInitialMediaSourceEvent && callback != null) {
        callback.mediaSourcePrepared(currentMediaSource, player);
        isInitialMediaSourceEvent = false;
      }
    }
  }
}
