package com.tivo.exoplayer.library.multiview;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
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
