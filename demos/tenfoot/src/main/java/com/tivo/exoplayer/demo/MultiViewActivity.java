package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.


import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSourceFactory;
import com.google.android.exoplayer2.source.hls.playlist.DefaultHlsPlaylistTracker;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.common.collect.ImmutableList;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.SourceFactoriesCreated;
import com.tivo.exoplayer.library.errorhandlers.PlaybackExceptionRecovery;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorHandlerListener;
import com.tivo.exoplayer.library.multiview.MultiExoPlayerView;
import com.tivo.exoplayer.library.multiview.MultiViewPlayerController;
import com.tivo.exoplayer.library.source.MediaItemHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Example player that uses a "ten foot" UI, that is majority of the UX is controlled by
 * media controls on an Android STB remote.
 *
 * <p>The activity plays either a single URL (intent data is simply a string with the URL) or a list of URL's
 * (intent data "uri_list" is a list of URL's).  Switching URL's is via the channel up/down buttons</p>
 */
public class MultiViewActivity extends AppCompatActivity {

  public static final String TAG = "MultiViewActivity";

  public static final Integer DEFAULT_LOG_LEVEL = com.google.android.exoplayer2.util.Log.LOG_LEVEL_ALL;

  // Intents
  public static final String ACTION_VIEW_LIST = ViewActivity.ACTION_VIEW_LIST;
  public static final String ACTION_VIEW = ViewActivity.ACTION_VIEW;

  // Intent data
  public static final String URI_LIST_EXTRA = ViewActivity.URI_LIST_EXTRA;
  public static final String ENABLE_ASYNC_RENDER = ViewActivity.ENABLE_ASYNC_RENDER;

  // Fast re-sync
  public static final String LIVE_OFFSET = ViewActivity.LIVE_OFFSET;
  public static final String FAST_RESYNC = ViewActivity.FAST_RESYNC;

  // Constrols size of the Mosaic, default is 2x2 and count of players to create
  public static final String GRID_ROWS = "grid_rows";
  public static final String GRID_COLUMNS = "grid_columns";
  public static final String PLAYBACK_COUNT = "player_count";
  public static final String SELECT_CELL = "select_cell";
  public static final String QUICK_AUDIO_SELECT = "quick_audio_select";

  @Nullable private Intent newIntent;
  @Nullable private PlaybackState playbackState;

  private Toast errorRecoveryToast;
  private MultiExoPlayerView mainView;
  private SimpleExoPlayerFactory.Builder simpleExoPlayerFactoryBuilder;

  private static class PlaybackState {
    List<MediaItem> mediaItems;
    int selectedCell;

    public PlaybackState(MultiExoPlayerView mainView) {
      mediaItems = new ArrayList<>();
      for (int i = 0; i < mainView.getViewCount(); i++) {
        PlayerView playerView = (PlayerView) mainView.getChildAt(i);
        Player player = playerView.getPlayer();
        if (player != null) {
          mediaItems.add(player.getCurrentMediaItem());
        }
        if (playerView.hasFocus()) {
          selectedCell = i;
        }
      }
    }
  }

  // Channel up/down support
  @NonNull private ImmutableList<MediaItem> channelList =   // All MediaItem's created from URI_LIST_EXTRA
    ImmutableList.of();
  private int[] currentChannelForCell;                     // Current playing channel for each multi-view grid cell

  /**
   * Callback class for when each MediaItem and it's MediaSource is created.  This allows
   * modfying settings on the MediaSourceFactory or cloning and creating an alternate
   * MediaItem
   */
  private class FactoriesCreatedCallback implements SourceFactoriesCreated {
    @Override
    public MediaItem factoriesCreated(@C.ContentType int type, MediaItem item, MediaSourceFactory factory) {
      MediaItem.Builder itemBuilder = item.buildUpon();
      boolean fast_resync = getIntent().hasExtra(FAST_RESYNC);
      if (fast_resync) {
        DefaultHlsPlaylistTracker.ENABLE_SNTP_TIME_SYNC = true;
        DefaultHlsPlaylistTracker.ENABLE_SNTP_TIME_SYNC_LOGGING = true;
        float resyncPercentChange = getIntent().getFloatExtra(FAST_RESYNC, 0.0f) / 100.0f;

        itemBuilder
            .setLiveMinPlaybackSpeed(1.0f - resyncPercentChange)
            .setLiveMaxPlaybackSpeed(1.0f + resyncPercentChange);

      }

      if (getIntent().hasExtra(LIVE_OFFSET)) {
        int liveTargetOffsetMs = (int) (getIntent().getFloatExtra(LIVE_OFFSET, 30.0f) * 1000);
        itemBuilder
            .setLiveTargetOffsetMs(liveTargetOffsetMs);
        if (fast_resync) {
          itemBuilder
              .setLiveMinOffsetMs(liveTargetOffsetMs)
              .setLiveMaxOffsetMs(liveTargetOffsetMs);
        }
      }
      return itemBuilder.build();
    }

    @Override
    public void upstreamDataSourceFactoryCreated(HttpDataSource.Factory upstreamFactory) {
      // TODO other factories then the default
      if (upstreamFactory instanceof DefaultHttpDataSource.Factory) {
        ((DefaultHttpDataSource.Factory) upstreamFactory).setUserAgent("TenFootDemo - [" + SimpleExoPlayerFactory.VERSION_INFO + "]");
      }
    }
  }

  private class PlaybackErrorHandlerCallback implements PlayerErrorHandlerListener {
    @Override
    public void playerErrorProcessed(PlaybackException error, HandlingStatus status) {
      if (error == null) {
        Log.d(TAG, "null error!");
        return;
      }
      switch (status) {
        case IN_PROGRESS:
          Log.d(TAG, "playerErrorProcessed() - error: " + error.getMessage() + " status: " + status);
          MultiViewActivity.this.showErrorRecoveryWhisper(null, error);
          break;

        case SUCCESS:
          Log.d(TAG, "playerErrorProcessed() - recovered from " + error.getMessage());
          MultiViewActivity.this.showErrorRecoveryWhisper(null, null);
          break;

        case WARNING:
          if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) {
            ExoPlaybackException exoPlaybackException = (ExoPlaybackException) error;
            @C.FormatSupport int formatSupport = exoPlaybackException.rendererFormatSupport;
            String reason = "format: " + Format.toLogString(exoPlaybackException.rendererFormat) + ", ";
            switch (formatSupport) {
              case C.FORMAT_EXCEEDS_CAPABILITIES:
                reason = "Exceeds Capabilities";
                break;
              case C.FORMAT_HANDLED:
                break;
              case C.FORMAT_UNSUPPORTED_DRM:
                reason = "Unsupported DRM";
                break;
              case C.FORMAT_UNSUPPORTED_SUBTYPE:
                reason = "Unsupported Subtype";
                break;
              case C.FORMAT_UNSUPPORTED_TYPE:
                reason = "Unsupported Type";
                break;
            }
            // TODO this is ok if it is audio only
//            ViewActivity.this.showError("No supported video tracks, " + reason, error);

          } else {
            MultiViewActivity.this.showErrorDialogWithRecoveryOption(error, "Un-excpected playback error");
          }
          break;

        case FAILED:
          MultiViewActivity.this.showErrorDialogWithRecoveryOption(error, "Playback Failed");
          break;
      }
    }
  }

  // Activity lifecycle
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Log.d(TAG, "onCreate() called");

    Log.i(TAG, SimpleExoPlayerFactory.VERSION_INFO);
    Context context = getApplicationContext();

    SimpleExoPlayerFactory.initializeLogging(context, DEFAULT_LOG_LEVEL);

    simpleExoPlayerFactoryBuilder = new SimpleExoPlayerFactory.Builder(context)
        .setPlaybackErrorHandlerListener(new PlaybackErrorHandlerCallback())
        .setSourceFactoriesCreatedCallback(new FactoriesCreatedCallback());

    boolean enableAsyncRenderer = getIntent().getBooleanExtra(ENABLE_ASYNC_RENDER, false);
    if (enableAsyncRenderer) {
      simpleExoPlayerFactoryBuilder.setMediaCodecOperationMode(enableAsyncRenderer);
    }


    setContentView(R.layout.multiview_activity);
    View contentView = findViewById(android.R.id.content);
    mainView = contentView.findViewById(R.id.multi_player_view);
  }

  @Override
 public void onNewIntent(Intent intent) {
    Log.d(TAG, "onNewIntent() - intent: " + intent);
    super.onNewIntent(intent);
    newIntent = intent;
  }

  public void onStart() {
    super.onStart();
    SimpleExoPlayerFactory.initializeLogging(getApplicationContext(), DEFAULT_LOG_LEVEL);
    Log.d(TAG, "onStart() called");
    processIntent(getIntent());
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    Log.d(TAG, "onRestart() called");
  }

  @Override
  public void onResume() {
    super.onResume();
    Log.d(TAG, "onResume() called");
    if (newIntent != null) {
      processIntent(newIntent);
      setIntent(newIntent);
      newIntent = null;
    } else if (playbackState != null) {
      for (int i=0; i < mainView.getViewCount(); i++) {
        boolean fast_resync = getIntent().hasExtra(FAST_RESYNC);
        mainView.getPlayerController(i).playMediaItem(fast_resync, playbackState.mediaItems.get(i));
      }
      mainView.setSelectedPlayerView(playbackState.selectedCell);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    Log.d(TAG, "onPause() called");
    playbackState = new PlaybackState(mainView);
    mainView.stopAllPlayerViews();
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.d(TAG, "onStop() called");
    mainView.removeAllPlayerViews();
    playbackState = null;
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "onDestroy() called");
    super.onDestroy();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    Log.d(TAG, "onWindowFocusChanged() called with: hasFocus = [" + hasFocus + "]");
  }

  // Internal methods

  private void processIntent(Intent intent) {
    Log.d(TAG, "processIntent() - intent: " + intent);
    String action = intent.getAction();
    Uri videoUri = intent.getData();

    Uri[] uris = new Uri[0];

    String[] uriStrings = intent.getStringArrayExtra(URI_LIST_EXTRA);

    if (ACTION_VIEW.equals(action) && videoUri != null) {
      uris = new Uri[]{videoUri};
    } else if (ACTION_VIEW_LIST.equals(action) && uriStrings != null) {
      uris = parseToUriList(uriStrings);
    }


    List<MediaItem> mediaItemsToPlay = new ArrayList<>();
    for (Uri uri : uris) {
      MediaItem.Builder builder = new MediaItem.Builder();
      builder.setUri(uri);
      MediaItemHelper.populateDrmPropertiesFromIntent(builder, intent, this);
      mediaItemsToPlay.add(builder.build());
    }

    boolean fast_resync = intent.hasExtra(FAST_RESYNC);
    int rows = intent.getIntExtra(GRID_ROWS, 2);
    int columns = intent.getIntExtra(GRID_COLUMNS, 2);

    channelList = ImmutableList.copyOf(mediaItemsToPlay);
    currentChannelForCell = new int[rows * columns];
    Arrays.fill(currentChannelForCell, 0);
    mainView.setQuickAudioSelect(intent.getBooleanExtra(QUICK_AUDIO_SELECT, false));

    mainView.createExoPlayerViews(rows, columns, simpleExoPlayerFactoryBuilder, new MultiExoPlayerView.FocusedPlayerListener() {
      @Override
      public void focusedPlayerChanged(PlayerView view, MultiViewPlayerController controller, boolean focused) {
        Log.d(TAG, "focusedPlayerChanged: view: " + view + " location: " + controller.getGridLocation() + " focused: " + focused);
      }

      @Override
      public void focusPlayerClicked(PlayerView view, MultiViewPlayerController controller) {
        Log.d(TAG, "focusPlayerClicked: view: " + view + " location: " + controller.getGridLocation());
        MediaItem mediaItem = Objects.requireNonNull(view.getPlayer()).getCurrentMediaItem();
        MediaItem.PlaybackProperties properties = mediaItem.playbackProperties;
        if (properties != null) {

          Intent currentIntent = getIntent();
          Intent intent = new Intent(MultiViewActivity.this, ViewActivity.class);
          intent.setAction(ViewActivity.ACTION_VIEW);
          intent.setData(properties.uri);

          // Copy extras from the current intent
          if (currentIntent.getExtras() != null) {
            intent.putExtras(currentIntent.getExtras());
          }

          startActivity(intent);
        }
      }
    });
    MultiExoPlayerView.OptimalVideoSize optimalSize = mainView.calculateOptimalVideoSizes(false);

    Iterator<MediaItem> it = mediaItemsToPlay.iterator();
    MediaItem currentItem = null;
    int playbackCount = mainView.getViewCount();
    playbackCount = intent.getIntExtra(PLAYBACK_COUNT, playbackCount);
    int selectCell = intent.getIntExtra(SELECT_CELL, 0);
    mainView.setSelectedPlayerView(selectCell);

    for (int i = 0; i < playbackCount; i++) {
      currentItem = it.hasNext() ? it.next() : currentItem;
      if (currentItem != null) {
        currentChannelForCell[i] = i;
        MultiViewPlayerController playerController = mainView.getPlayerController(i);
        playerController.setOptimalVideoSize(optimalSize);
        Log.d(TAG, "Position " + i + " MediaItem: " + currentItem.playbackProperties.uri);
        playerController.playMediaItem(fast_resync, currentItem);
      }
    }
  }

  // Utilities
  private Uri[] parseToUriList(String[] uriStrings) {
    Uri[] uris;
    uris = new Uri[uriStrings.length];
    for (int i = 0; i < uriStrings.length; i++) {
      uris[i] = Uri.parse(uriStrings[i]);
    }
    return uris;
  }


  private boolean channelUpDown(boolean isChannelUp) {
    MultiViewPlayerController playerController = mainView.getSelectedController();
    int selectedControllerIndex = playerController.getGridLocation().getViewIndex();

    int currentChannel = currentChannelForCell[selectedControllerIndex];
    int nextChannel = isChannelUp ? currentChannel + 1 : currentChannel - 1;
    currentChannel = (nextChannel + channelList.size()) % channelList.size();
    currentChannelForCell[selectedControllerIndex] = currentChannel;

    boolean fast_resync = getIntent().hasExtra(FAST_RESYNC);
    playerController.playMediaItem(fast_resync, channelList.get(currentChannel));
    return true;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    boolean handled = false;

    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      int keyCode = event.getKeyCode();

      switch (keyCode) {
        case KeyEvent.KEYCODE_CHANNEL_UP:
          handled = channelUpDown(true);
          break;

        case KeyEvent.KEYCODE_CHANNEL_DOWN:
          handled = channelUpDown(false);
          break;
      }
    }

    return handled || super.dispatchKeyEvent(event);
  }

  private void showErrorRecoveryWhisper(@Nullable String message, @Nullable PlaybackException error) {
    String text = null;

    if (message != null) {
      text = message;
    } else if (error != null) {
      if (PlaybackExceptionRecovery.isBehindLiveWindow(error)) {
         text = "Recovering Behind Live Window";
      } else if (PlaybackExceptionRecovery.isPlaylistStuck(error)) {
        text = "Retrying stuck playlist";
      } else {
        text = "Retrying - " + error.getErrorCodeName();
      }
    }
    if (text != null) {
      if (errorRecoveryToast == null) {
        errorRecoveryToast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
      }
      errorRecoveryToast.setText(text);
      errorRecoveryToast.setDuration(Toast.LENGTH_LONG);
      errorRecoveryToast.show();
    } else if (errorRecoveryToast != null) {
      errorRecoveryToast.cancel();
      errorRecoveryToast = null;
    }
  }

  private void showError(String message, @Nullable Exception exception) {
    AlertDialog alertDialog = new AlertDialog.Builder(this).create();
    alertDialog.setTitle("Error");
    if (exception != null) {
      message += " - " + exception.getMessage();
    }
    alertDialog.setMessage(message);
    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", (dialog, which) -> dialog.dismiss());
    alertDialog.show();
  }


  private void showErrorDialogWithRecoveryOption(PlaybackException error, String title) {
    title += " - " + error.getLocalizedMessage();

    AlertDialog alertDialog = new AlertDialog.Builder(this)
        .setTitle("Error - " + error.errorCode)
        .setMessage(title)
        .setPositiveButton("Retry", (dialog, which) -> {
//          player.seekToDefaultPosition();
//          player.prepare();   // Attempt recovery with simple re-prepare using current MediaItem
//          dialog.dismiss();
        })
        .setNegativeButton("Ok", (dialog, which) -> {
//          player.stop();
//          player.clearMediaItems();
          dialog.dismiss();
        })
        .create();
    alertDialog.show();
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

}
