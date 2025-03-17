package com.tivo.exoplayer.demo;// Copyright 2010 TiVo Inc.  All rights reserved.


import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
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
import java.lang.reflect.Method;
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
  private boolean intentPossessed = false;

  private Toast errorRecoveryToast;
  private MultiExoPlayerView mainView;
  private SimpleExoPlayerFactory.Builder simpleExoPlayerFactoryBuilder;

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
    mainView.setMultiViewHandlesAudioFocus(true);
    intentPossessed = false;
  }

  @Override
 public void onNewIntent(Intent intent) {
    Log.d(TAG, "onNewIntent() - intent: " + intent);
    super.onNewIntent(intent);
    if ("dump_view".equals(intent.getAction())) {
      dumpViewHierarchy((View) mainView.getParent());
      return;
    }
    newIntent = intent;
  }

  public void onStart() {
    super.onStart();
    SimpleExoPlayerFactory.initializeLogging(getApplicationContext(), DEFAULT_LOG_LEVEL);
    Log.d(TAG, "onStart() called - intentProcessed: " + intentPossessed);
    if (!intentPossessed) {
      processIntent();
      intentPossessed = true;
    }
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    Log.d(TAG, "onRestart() called");
    if (mainView != null && newIntent== null)  {
      mainView.restoreStateAndRestart();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    mainView.onResume();
    Log.d(TAG, "onResume() called");
    if (newIntent != null) {
      setIntent(newIntent);
      processIntent();
      newIntent = null;
      intentPossessed = true;
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    mainView.onPause();
    Log.d(TAG, "onPause() called");
  }

  @Override
  public void onStop() {
    super.onStop();
    Log.d(TAG, "onStop() called");
    mainView.saveStateAndStop();
  }

  @Override
  protected void onDestroy() {
    Log.d(TAG, "onDestroy() called");
    mainView.removeAllPlayerViews();
    super.onDestroy();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    Log.d(TAG, "onWindowFocusChanged() called with: hasFocus = [" + hasFocus + "]");
  }

  // Internal methods
  private static void dumpViewHierarchy(View view) {
    dumpViewHierarchy(view, 0);
  }

  private static void dumpViewHierarchy(View view, int depth) {
    if (view instanceof ViewGroup) {
      ViewGroup viewGroup = (ViewGroup) view;
      logViewInfo(depth, view);
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        View child = viewGroup.getChildAt(i);
        if (child.getVisibility() == View.VISIBLE) {
          // Recursively dump the child view
          dumpViewHierarchy(child, depth + 1);
        }
      }
    } else if (view.getVisibility() == View.VISIBLE) {
      logViewInfo(depth, view);
    }
  }

  private static void logViewInfo(int depth, View child) {
    StringBuilder sb = new StringBuilder();
    for (int j = 0; j < depth; j++) {
      sb.append("  ");
    }
    sb.append(child);
    callDebugMethod(child);
//    Log.i(TAG, sb.toString() + " - " + child.getWidth() + "x" + child.getHeight() + " layout: " + debugLayout(child));
  }

  private static String debugLayout(View child) {
    ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
    if (layoutParams instanceof LinearLayout.LayoutParams) {
      LinearLayout.LayoutParams linearLayoutParams = (LinearLayout.LayoutParams) layoutParams;
      return linearLayoutParams.debug("[linear: ") + " margins: " + linearLayoutParams.leftMargin + "," + linearLayoutParams.topMargin + "," + linearLayoutParams.rightMargin + "," + linearLayoutParams.bottomMargin + "]";
    } else if (layoutParams instanceof GridLayout.LayoutParams) {
      GridLayout.LayoutParams gridLayoutParams = (GridLayout.LayoutParams) layoutParams;
      return "[Grid, margin: " + gridLayoutParams.leftMargin + "," + gridLayoutParams.topMargin + "," + gridLayoutParams.rightMargin + "," + gridLayoutParams.bottomMargin + "]";
    } else if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
      ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
      return "[margin: " + marginLayoutParams.leftMargin + "," + marginLayoutParams.topMargin + "," + marginLayoutParams.rightMargin + "," + marginLayoutParams.bottomMargin + "]";
    } else if (layoutParams != null) {
      return "[layout: " + layoutParams.width + "x" + layoutParams.height + "]";
    } else {
      return "[no layout]";
    }
  }

  private static void callDebugMethod(View view) {
    try {
      // Access the debug() method using reflection
      Method debugMethod = view.getClass().getMethod("debug");

      // Call the debug() method
      debugMethod.invoke(view);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void processIntent() {
    Intent intent = getIntent();
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

    int rows = intent.getIntExtra(GRID_ROWS, 2);
    int columns = intent.getIntExtra(GRID_COLUMNS, 2);

    channelList = ImmutableList.copyOf(mediaItemsToPlay);
    switchViewToSize(rows, columns);
  }

  private void switchViewToSize(int rows, int columns) {
    Intent intent = getIntent();
    boolean fast_resync = intent.hasExtra(FAST_RESYNC);
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

    Iterator<MediaItem> it = channelList.iterator();
    MediaItem currentItem = null;
    int playbackCount = intent.getIntExtra(PLAYBACK_COUNT, mainView.getViewCount());
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

        case KeyEvent.KEYCODE_2:
          switchViewToSize(1, 2);
          break;

        case KeyEvent.KEYCODE_4:
          switchViewToSize(2, 2);
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
