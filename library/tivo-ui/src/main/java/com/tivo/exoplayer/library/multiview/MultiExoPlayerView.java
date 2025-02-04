package com.tivo.exoplayer.library.multiview;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.google.common.collect.ImmutableList;
import com.tivo.exoplayer.library.R;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

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

  private @Nullable MultiViewPlayerController[] playerControllers;
  private final Context context;
  private int viewCount;
  private @MonotonicNonNull MultiViewPlayerController selectedController;
  private FocusedPlayerListener focusedPlayerListener;
  private final MultiPlayerAudioFocusManager audioFocusManager;

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
        meets = format.width <= width && format.height <= height;
      }
      return meets;
    }
  }

  public static class GridLocation {
    private final int row;
    private final int column;
    private final int viewIndex;

    public GridLocation(int row, int column, int viewIndex) {
      this.row = row;
      this.column = column;
      this.viewIndex = viewIndex;
    }

    /**
     * Location of the Grid Cell, 0..number of cells.  Independent of
     * if creation order is row or column major format.
     *
     * @return index of this Grid Cell
     */
    public int getViewIndex() {
      return viewIndex;
    }

    @Override
    public String toString() {
      return "GridLocation{" +
          "row=" + row +
          ", column=" + column +
          '}';
    }
  }

  /**
   * Clients of the {@link MultiExoPlayerView} use this call-back to be notified of events on the
   * focused {}
   */
  public static interface FocusedPlayerListener {

    /**
     * Called with "focused" true for the grid cell selection.  Subsequent changes call this method back
     * twice, with the cell loosing focus (focused is false) and then the cell gaining focus
     *
     * @param view       the {@PlayerView} of the cell (the player is exposed via {@link PlayerView#getPlayer()})
     * @param controller the {@link MultiViewPlayerController} for the cell gaining/loosing focus
     * @param focused    true if cell gained focus, false if it lost it
     */
    default void focusedPlayerChanged(PlayerView view, MultiViewPlayerController controller, boolean focused) {};

    /**
     * Called when a {@link PlayerView} in the multi-view is clicked, by definition it has focus.
     *
     * @param view  the {@PlayerView} of the clicked cell (the player is exposed via {@link PlayerView#getPlayer()})
     * @param controller the {@link MultiViewPlayerController} for the clicked cell
     */
    default void focusPlayerClicked(PlayerView view, MultiViewPlayerController controller) {}
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
    this.audioFocusManager = new MultiPlayerAudioFocusManager(context);
  }

  public void createExoPlayerViews(int rowCount, int columnCount, SimpleExoPlayerFactory.Builder builder) {
    createExoPlayerViews(rowCount, columnCount, builder, new FocusedPlayerListener() {});
  }

  /**
   * Creates {@link com.google.android.exoplayer2.Player} instances, then creates and associates them with
   * {@link PlayerView}'s for each grid cell.  rowCount * columnCount cells are created.
   *
   * <p>Each cell has an associated {@link MultiViewPlayerController} to use to set the {@link com.google.android.exoplayer2.MediaItem}
   * to play in the cell.</p>
   *
   * @param rowCount - number of rows of player grid cells
   * @param columnCount - number of columns of player grid cells
   * @param builder - the {@link SimpleExoPlayerFactory.Builder} to use to create the players.
   * @param listener - call back listener for when focus changes.
   */
  public void createExoPlayerViews(int rowCount, int columnCount, SimpleExoPlayerFactory.Builder builder, FocusedPlayerListener listener) {
    focusedPlayerListener = listener;

    removeAllPlayerViews();

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

      selectableView.setOnKeyListener(new OnKeyListener() {
        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
          if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            selectedPlayerViewClicked(viewIndex, event);
          }
          return false;
        }
      });

      GridLocation gridLocation = new GridLocation(row, column, viewIndex);
      MultiViewPlayerController multiViewPlayerController = new MultiViewPlayerController(builder, selected, gridLocation, audioFocusManager);
      SimpleExoPlayer player = multiViewPlayerController.createPlayer();
      playerView.setPlayer(player);
      playerControllers[i] = multiViewPlayerController;

      if (selected) {
        selectedController = multiViewPlayerController;
        audioFocusManager.setSelectedPlayer(player);
        focusedPlayerListener.focusedPlayerChanged(playerView, multiViewPlayerController, true);
      } else {
        focusedPlayerListener.focusedPlayerChanged(playerView, multiViewPlayerController, false);
      }
      multiViewPlayerController.setSelected(selected);

      if (++column == columnCount) {
        column = 0;
        row++;
      }
    }
  }

  private boolean selectedPlayerViewClicked(int viewIndex, KeyEvent event) {
    boolean handled = false;
    if (playerControllers != null) {
      selectedController = playerControllers[viewIndex];
      PlayerView playerView = (PlayerView) getChildAt(viewIndex);
      focusedPlayerListener.focusPlayerClicked(playerView, selectedController);
      handled = true;
    }
    return handled;
  }

  private void childPlayerSelectedChanged(int i, boolean hasFocus) {
    if (playerControllers != null) {
      selectedController = playerControllers[i];
      selectedController.setSelected(hasFocus);
      PlayerView playerView = (PlayerView) getChildAt(i);
      audioFocusManager.setSelectedPlayer(playerView.getPlayer());
      focusedPlayerListener.focusedPlayerChanged(playerView, selectedController, true);
    } else {
      Log.d(TAG, "childPlayerSelectedChanged() - no playerControllers, releaseing audio focus");
      audioFocusManager.setSelectedPlayer(null);
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

  /**
   * Get the number of {@link PlayerView} cells in the layout
   *
   * @return count of cells.
   */
  public int getViewCount() {
    return viewCount;
  }

  /**
   * Only one cell is "selected" at a time, the player in the selected cell has audio focus,
   * the remaining players do not load or play their audio track.   There is always one and
   * only one selected controller once {@link #createExoPlayerViews(int, int, SimpleExoPlayerFactory.Builder)}
   * has been called.
   *
   * @return the {@link MultiViewPlayerController} for the current selected controller
   */
  public @NonNull MultiViewPlayerController getSelectedController() {
    return selectedController;
  }

  /**
   * The {@link PlayerView} for the selected cell.  See {@link #getSelectedController()}.
   *
   * @return the {@link PlayerView} that is currently selected, or null if {@link #removeAllPlayerViews()} was called
   */
  public PlayerView getSelectedPlayerView() {
    PlayerView selected = null;
    if (selectedController != null && playerControllers != null) {
      for (int i = 0; i < playerControllers.length && selected == null; i++) {
        MultiViewPlayerController controller = playerControllers[i];
        if (controller == selectedController) {
          selected = (PlayerView) getChildAt(i);
        }
      }
    }
    return selected;
  }

  /**
   * Set the player in the indicated cell as "selected".  This focuses the {@link PlayerView}
   * in that grid cell and that triggers its player to have audio focus.
   *
   * @param index - grid location (row major index form) of the cell to select.
   */
  public void setSelectedPlayerView(int index) {
    getChildAt(index).requestFocus();
  }

  /**
   * Returns the {@link MultiViewPlayerController} at the specified index (grid indexes
   * are in row-major order)
   *
   * @param index index of controller to select
   * @return {@link MultiViewPlayerController} or null if {@link #removeAllPlayerViews()} called clearing contorller list
   */
  public @Nullable MultiViewPlayerController getPlayerController(int index) {
    return playerControllers == null ? null : playerControllers[index];
  }

  public ImmutableList<MultiViewPlayerController> getPlayerControllers() {
    return playerControllers == null
        ? ImmutableList.of()
        : new ImmutableList.Builder<MultiViewPlayerController>()
            .addAll(Arrays.asList(playerControllers))
            .build();
  }

  /**
   * This method can be called to switch out the multi-view for a single player view.
   * It calls {@link Player#stop()} to free up the memory associated with the players
   * for each view but leaves the player in place with any DRM session caching it may
   * contain.
   *
   * TODO - may want to detach the player from it's MultiPlayerView child if we want to use it elseware
   */
  public void stopAllPlayerViews() {
    if (playerControllers != null) {
      for (int i = 0; i < playerControllers.length; i++) {
        MultiViewPlayerController playerController = playerControllers[i];
        playerController.stopPlayer();
      }
    }
  }

  /**
   * Call this method to stop all the players and remove the child {@link PlayerView}'s
   * and calls {@link MultiViewPlayerController#releasePlayer()} on each of the players.
   * Once this method is called this {@link MultiExoPlayerView} is effectively destroyed.
   *
   * <p>This should be called when the activity is stopped.  See {@link #stopAllPlayerViews()}
   * for a method to just release memory held without completely destroying the players</p>
   */
  public void removeAllPlayerViews() {
    if (playerControllers != null) {
      audioFocusManager.setSelectedPlayer(null);
      for (int i = 0; i < playerControllers.length; i++) {
        MultiViewPlayerController playerController = playerControllers[i];
        playerController.releasePlayer();
        PlayerView playerView = (PlayerView) getChildAt(i);
        playerView.setPlayer(null);
      }

      removeAllViews();
      playerControllers = null;
    }
  }
}
