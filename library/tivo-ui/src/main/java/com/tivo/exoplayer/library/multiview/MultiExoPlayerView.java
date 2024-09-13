package com.tivo.exoplayer.library.multiview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.google.common.collect.ImmutableList;
import com.tivo.exoplayer.library.R;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import java.util.Arrays;

/**
 * A GridLayout (mosaic view) that contains and manages a set of N-PlayerView's and the {@link MultiViewPlayerController}'s for
 * each desired grid cell.
 *
 * <p>The method {@link #createExoPlayerViews(int, int, SimpleExoPlayerFactory.Builder)} is used to create the
 * child {@link PlayerView}'s and associated {@link com.google.android.exoplayer2.ExoPlayer}'s for each based
 * on the desired rows and colums in this container./p>
 *
 * <p>Uses the default multi_view_player.xml layout as the default layout for each grid cell, this can be overriden
 * by the users of this library.
 * </p>
 */
public class MultiExoPlayerView extends GridLayout {

  private static final String TAG = "MultiExoPlayerView";

  private MultiViewPlayerController[] playerControllers;
  private final Context context;
  private int viewCount;

  public static class OptimalVideoSize {
    public final int width;
    public final int height;

    public OptimalVideoSize(int width, int height) {
      this.width = width;
      this.height = height;
    }

    public boolean meetsOptimalSize(Format format) {
      boolean meets = format.height == Format.NO_VALUE || format.width == Format.NO_VALUE;
      if (!meets) {
        meets = format.width <= width && format.height < height;
      }
      return meets;
    }
  }

  public MultiExoPlayerView(Context context) {
    this(context, null);
  }

  public MultiExoPlayerView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MultiExoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  public MultiExoPlayerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);

    LayoutInflater.from(context).inflate(R.layout.multi_view_player_container, this, true);
    this.context = context;
  }

  public void createExoPlayerViews(int rowCount, int columnCount, SimpleExoPlayerFactory.Builder builder) {
    if (playerControllers != null) {
      for (int i = 0; i < playerControllers.length; i++) {
        MultiViewPlayerController playerController = playerControllers[i];
        playerController.handleStop();
        PlayerView playerView = (PlayerView) getChildAt(i);
        playerView.setPlayer(null);
      }

      removeAllViews();
    }

    viewCount = rowCount * columnCount;
    setRowCount(rowCount);
    setColumnCount(columnCount);
    LayoutInflater inflater = LayoutInflater.from(context);

    playerControllers = new MultiViewPlayerController[viewCount];
    int row=0;
    int column=0;
    for (int i = 0; i < viewCount; i++) {

      // Create a PlayerView in a GridLayout by expanding the multi_view_player which has a single grid cell
      // update it's layout to the correct row/column
      ViewGroup multiPlayerView = (ViewGroup) inflater.inflate(R.layout.multi_view_player, this, false);
      PlayerView playerView = multiPlayerView.findViewById(R.id.player_view_template);
      LayoutParams layoutParams = (LayoutParams) playerView.getLayoutParams();
      layoutParams.columnSpec = GridLayout.spec(column, 1.0f);
      layoutParams.rowSpec = GridLayout.spec(row, 1.0f);

      // Remove the PlayerView from the template layout ViewGroup and insert it in the activity ViewGroup
      multiPlayerView.removeView(playerView);
      addView(playerView);
      View selectableView = playerView.findViewById(R.id.exo_content_frame);

      boolean selected = i == 0;
      if (selected) {
        selectableView.requestFocus();
      }

      int viewIndex = i;
      selectableView.setOnFocusChangeListener(new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
          Log.d(TAG, "onFocusChange() - hasFocus: " + hasFocus + " index: " + viewIndex + " view:" + v);
          childPlayerSelectedChanged(viewIndex, hasFocus);
        }
      });
      MultiViewPlayerController multiViewPlayerController = new MultiViewPlayerController(builder, selected);
      playerView.setPlayer(multiViewPlayerController.createPlayer());
      playerControllers[i] = multiViewPlayerController;
      if (++column == columnCount) {
        column = 0;
        row++;
      }
    }
  }

  /**
   * Video is sized to fit by {@link com.google.android.exoplayer2.ui.AspectRatioFrameLayout} such as to
   * preserve the aspect ratio of the content.  This is sized to fit this view, filling each grid cell
   * with some minor amount of UX (padding/margins).
   *
   * <p>This method determines the optimal width/height to restrict the video content to in order to
   * reduce the bandwidth used while not requiring excessive scaling</p>
   *
   * <p>Note: only valid after call to {@link #createExoPlayerViews(int, int, SimpleExoPlayerFactory.Builder)}
   * sets the rows/columns</p>
   *
   * @param includeDensity - if true, consider display density (if UI is double pixeled)
   * @return OptimalVideoSize
   */
  public OptimalVideoSize calculateOptimalVideoSizes(boolean includeDensity) {

    // Using the DisplayMetrics is an upper limit, assuming this View fills the entire screen (which it
    // doesn't)
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    int optimalWidth = metrics.widthPixels / getColumnCount();
    int optimalHeight = metrics.heightPixels / getRowCount();

    return includeDensity
        ? new OptimalVideoSize((int) (optimalWidth * metrics.density), (int) (optimalHeight * metrics.density))
        : new OptimalVideoSize(optimalWidth, optimalHeight);
  }

  private void childPlayerSelectedChanged(int i, boolean hasFocus) {
    playerControllers[i].setSelected(hasFocus);
  }

  public int getViewCount() {
    return viewCount;
  }

  public MultiViewPlayerController getPlayerController(int index) {
    return playerControllers[index];
  }

  public ImmutableList<MultiViewPlayerController> getPlayerControllers() {
    return playerControllers == null
        ? ImmutableList.of()
        : new ImmutableList.Builder()
            .addAll(Arrays.asList(playerControllers))
            .build();
  }
}
