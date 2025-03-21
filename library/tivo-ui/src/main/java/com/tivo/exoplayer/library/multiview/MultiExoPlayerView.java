package com.tivo.exoplayer.library.multiview;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.util.Log;
import com.google.common.collect.ImmutableList;
import com.tivo.exoplayer.library.R;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
public class MultiExoPlayerView extends LinearLayout {

  private static final String TAG = "MultiExoPlayerView";
  private final View singleRowView;
  private final View multiGridView;

  private @Nullable MultiViewPlayerController[] playerControllers;
  private final Context context;
  private int viewCount;
  private @MonotonicNonNull MultiViewPlayerController selectedController;
  private FocusedPlayerListener focusedPlayerListener;
  private MultiPlayerAudioFocusManagerApi audioFocusManager;
  private boolean useQuickSelect;
  @Nullable private PlaybackState playbackState;
  private boolean viewsCreated;

  private static class PlaybackState {
    List<MediaItem> mediaItems;
    int selectedCell;

    public PlaybackState(MultiExoPlayerView mainView) {
      mediaItems = new ArrayList<>();
      for (int i = 0; i < mainView.getViewCount(); i++) {
        PlayerView playerView = mainView.getPlayerView(i);
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
  public interface FocusedPlayerListener {

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

    LayoutInflater inflater = LayoutInflater.from(context);
    inflater.inflate(R.layout.multi_view_single_row, this, true);
    singleRowView = getChildAt(getChildCount() - 1);

    inflater.inflate(R.layout.multi_view_grid, this, true);
    multiGridView = getChildAt(getChildCount() - 1);
    multiGridView.setVisibility(GONE);
    this.context = context;

    // TODO - when fixes are made to the library, replace this with the MultiPlayerAudioFocusManager as the default
//    this.audioFocusManager = new MultiPlayerAudioFocusManager(context);
    this.audioFocusManager = new NullMultiPlayerAudioFocusManager();
  }

  /**
   * Set the audio focus manager to use for the multi-view.  The default is to not handle audio focus and
   * expect the client to handle it externally.
   *
   * <p>This should be called once after the constructor and before {@link #createExoPlayerViews(int, int, SimpleExoPlayerFactory.Builder)}
   * or not at all to use the default.</p>
   *
   * @param handlesAudioFocus if true, the {@link MultiPlayerAudioFocusManager} is used to manage audio focus
   */
  public void setMultiViewHandlesAudioFocus(boolean handlesAudioFocus) {
    this.audioFocusManager = handlesAudioFocus ? new MultiPlayerAudioFocusManager(context) : new NullMultiPlayerAudioFocusManager();
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
    initializeLayout(rowCount, columnCount);

    int row=0;
    int column=0;
    playerControllers = new MultiViewPlayerController[viewCount];
    for (int viewIndex = 0; viewIndex < viewCount; viewIndex++) {
      PlayerView playerView = getPlayerView(viewIndex);

      boolean selected = viewIndex == 0;
      if (selected) {
        playerView.requestFocus();
      }
      setupListeners(playerView, viewIndex);

      GridLocation gridLocation = new GridLocation(row, column, viewIndex);
      MultiViewPlayerController multiViewPlayerController = new MultiViewPlayerController(builder, gridLocation, audioFocusManager);
      multiViewPlayerController.setQuickAudioSelect(useQuickSelect);
      SimpleExoPlayer player = multiViewPlayerController.createPlayer();
      playerView.setPlayer(player);
      playerControllers[viewIndex] = multiViewPlayerController;

      if (selected) {
        selectedController = multiViewPlayerController;
      }
      childPlayerSelectedChanged(viewIndex, selected);

      if (++column == columnCount) {
        column = 0;
        row++;
      }
    }
  }

  private void initializeLayout(int rowCount, int columnCount) {

    // Release any existing players from any prior view (TODO, we could swap them to the new views, if it increases)
    releaseActivePlayerControllers();

    // Then set the new viewCount and change the layout to match the new row/column count
    viewCount = rowCount * columnCount;
    if (viewCount <= 2) {
      singleRowView.setVisibility(VISIBLE);
      multiGridView.setVisibility(GONE);
    } else {
      singleRowView.setVisibility(GONE);
      GridLayout gridLayout = multiGridView.findViewById(R.id.player_grid_layout);

      // If the grid layout is not yet created or does not match the desired row/column count, reset it
      if (gridLayout.getChildCount() != viewCount || gridLayout.getRowCount() != rowCount || gridLayout.getColumnCount() != columnCount) {
        resetGridLayout(rowCount, columnCount, gridLayout);
      }
      multiGridView.setVisibility(VISIBLE);
    }
  }

  private void releaseActivePlayerControllers() {
    if (playerControllers != null) {
      audioFocusManager.setSelectedPlayer(null);
      for (int i = 0; i < playerControllers.length; i++) {
        MultiViewPlayerController playerController = playerControllers[i];
        playerController.releasePlayer();
        PlayerView playerView = getPlayerView(i);
        playerView.setPlayer(null);
      }
      playerControllers = null;
    }
  }

  /**
   * Create a GridLayout view with the specified number of rows and columns of PlayerView cells.
   *
   * <p>The pre-inflated template layout contains a single grid cell, R.id.player_view_template
   * which is used to create the PlayerView's for each cell in the grid.  Th inflation process
   * creates the correct {@link GridLayout.LayoutParams}, based on values in the layout
   * for each cell in the grid.</p>
   *
   * @param rowCount
   * @param columnCount
   * @param gridLayout
   */
  private void resetGridLayout(int rowCount, int columnCount, GridLayout gridLayout) {
    gridLayout.removeAllViews();
    gridLayout.setRowCount(rowCount);
    gridLayout.setColumnCount(columnCount);

    LayoutInflater inflater = LayoutInflater.from(context);

    // Create the PlayerView's for each cell in the grid by inflating the template layout
    // then modifying the layout parameters to place the PlayerView in the correct cell

    for (int viewIndex = 0; viewIndex < rowCount * columnCount; viewIndex++) {

      // Inflate the new PlayerView from the template, add it to the parent grid layout
      inflater.inflate(R.layout.multi_view_grid_cell, gridLayout, true);

      // The last added child is the newly inflated PlayerView
      View playerView = gridLayout.getChildAt(gridLayout.getChildCount() - 1);

      // Retrieve the generated LayoutParams and modify them.  Note we force equal weight for each cell,
      // as the PlayerView cannot handle multi-pass layouts as the SurfaceView will not be instantiated with
      // 0x0 size (so you never get a first frame render)
      GridLayout.LayoutParams layoutParams = (GridLayout.LayoutParams) playerView.getLayoutParams();
      layoutParams.columnSpec = GridLayout.spec(viewIndex % columnCount, 1.0f);
      layoutParams.rowSpec = GridLayout.spec(viewIndex / columnCount, 1.0f);
      playerView.setLayoutParams(layoutParams);
    }
  }

  private void setupListeners(View selectableView, int viewIndex) {
    selectableView.setOnFocusChangeListener(new OnFocusChangeListener() {
      @Override
      public void onFocusChange(View v, boolean hasFocus) {
        if (playerControllers != null) {
          Log.d(TAG, "onFocusChange() - hasFocus: " + hasFocus + " index: " + viewIndex + " view:" + v);
          childPlayerSelectedChanged(viewIndex, hasFocus);
        }
      }
    });

    selectableView.setOnKeyListener(new OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (playerControllers != null) {
          if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            selectedPlayerViewClicked(viewIndex, event);
          }
        }
        return false;
      }
    });
  }

  /**
   * Should be called when the {@link Activity}'s onResume() method..  This method resumes all the child {@link PlayerView}'s
   * and, for Android 11 or later, selects the selected cell to restore audio focus.
   *
   */
  public void onResume() {
    if (playerControllers != null) {
      for (int i=0; i < getViewCount(); i++) {
        PlayerView playerView = getPlayerView(i);
        playerView.onResume();
        if (Build.VERSION.SDK_INT >= 30) {
          if (playerView.hasFocus() && !audioFocusManager.hasAudioFocus()) {
            audioFocusManager.setSelectedPlayer(playerView.getPlayer());
          }
        }
      }
    }
  }

  /**
   * Should be called when the {@link Activity}'s onPause() method..  This method pauses all the child {@link PlayerView}'s*
   */
  public void onPause() {
    if (playerControllers != null) {
      for (int i=0; i < getViewCount(); i++) {
        PlayerView playerView = getPlayerView(i);
        playerView.onPause();
      }
    }
  }

  /**
   * Should be called when the Activity state indicates the view will be hidden for an
   * extended period of time.  This will release the audio focus and pause the players.
   *
   */
  public void saveStateAndStop() {
    playbackState = new PlaybackState(this);
    stopAllPlayerViews();
  }

  /**
   * Should be called when the Activity state restores the multiview.  This will restore the
   * saved state from {@link #saveStateAndStop()} and restart the players.
   */
  public void restoreStateAndRestart() {
    if (playbackState != null) {
      for (int i=0; i < getViewCount(); i++) {
        Objects.requireNonNull(getPlayerController(i)).playMediaItemInternal(playbackState.mediaItems.get(i));
      }
      setSelectedPlayerView(playbackState.selectedCell);
    }
  }

  private boolean selectedPlayerViewClicked(int viewIndex, KeyEvent event) {
    boolean handled = false;
    if (playerControllers != null) {
      selectedController = playerControllers[viewIndex];
      PlayerView playerView = getPlayerView(viewIndex);
      focusedPlayerListener.focusPlayerClicked(playerView, selectedController);
      handled = true;
    }
    return handled;
  }

  private void childPlayerSelectedChanged(int i, boolean hasFocus) {
    if (playerControllers != null) {
      selectedController = playerControllers[i];
      selectedController.setSelected(hasFocus);
      PlayerView playerView = getPlayerView(i);
      audioFocusManager.setSelectedPlayer(playerView.getPlayer());
      View focusBorderFrame = playerView.findViewById(R.id.focus_border_frame);
      focusBorderFrame.setBackgroundResource(hasFocus ? R.drawable.selection_border : android.R.color.transparent);
      focusedPlayerListener.focusedPlayerChanged(playerView, selectedController, true);
    } else {
      Log.d(TAG, "childPlayerSelectedChanged() - no playerControllers, releasing audio focus");
      audioFocusManager.setSelectedPlayer(null);
    }
  }


  /**
   * Get the PlayerView child at the specified index.
   *
   * @param index - index of the child to get, 0..number of cells (rowCount * columnCount)
   * @return the {@link PlayerView} at the specified index
   */
  private PlayerView getPlayerView(int index) {
    PlayerView playerView = null;
    if (viewCount <= 2) {
      playerView = index == 0 ? findViewById(R.id.player_view_one) : findViewById(R.id.player_view_two);
    } else {
      GridLayout gridLayout = findViewById(R.id.player_grid_layout);
      playerView = (PlayerView) gridLayout.getChildAt(index);
    }
    return playerView;
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
    // doesn't).  Note single row is centered and letterboxed into the screen so size
    // is half the full screen height.
    DisplayMetrics metrics = context.getResources().getDisplayMetrics();
    int optimalWidth = metrics.widthPixels / getColumnCount();
    int optimalHeight = metrics.heightPixels / Math.min(getRowCount(), 2);

    return includeDensity
        ? new OptimalVideoSize((int) (optimalWidth * metrics.density), (int) (optimalHeight * metrics.density))
        : new OptimalVideoSize(optimalWidth, optimalHeight);
  }

  private int getRowCount() {
    if (getChildCount() > 0 && getChildAt(0) instanceof GridLayout) {
      return ((GridLayout) getChildAt(0)).getRowCount();
    } else {
      return 1;
    }
  }

  public int getColumnCount() {
    if (getChildCount() > 0 && getChildAt(0) instanceof GridLayout) {
      return ((GridLayout) getChildAt(0)).getColumnCount();
    } else {
      return viewCount;
    }
  }

  /**
   * Set the quick audio select mode.
   *
   * <p>In quick select mode, track selection will attempt to prioritize audio formats for
   * audio tracks that allow volume change to mute/un-mute the audio track rather than enable
   * and disable the renderer.  This greatly speeds up the selection change time</p>
   *
   * @param enable true to enable quick select, default is false
   */
  public void setQuickAudioSelect(boolean enable) {
    useQuickSelect = enable;
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
          selected = getPlayerView(i);
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
    getPlayerView(index).requestFocus();
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

  /**
   * Get the list of {@link MultiViewPlayerController}'s for each grid cell in the layout.
   *
   * <p>The returned list is in the same order as the grid cells.  The list is empty if
   * {@link #removeAllPlayerViews()} was called releasing all the players.  Otherwise, if
   * {@link #stopAllPlayerViews()} has been called the players are in IDLE state and have
   * no active {@link com.google.android.exoplayer2.MediaItem}</p>
   *
   * @return list of {@link MultiViewPlayerController}'s
   */
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
    playbackState = null;
    releaseActivePlayerControllers();
    releaseChildPlayerViews(this);
  }

  private void releaseChildPlayerViews(ViewGroup viewGroup) {
    for (int i = 0; i < viewGroup.getChildCount(); i++) {
      View child = viewGroup.getChildAt(i);
      if (child instanceof PlayerView) {
        PlayerView playerView = (PlayerView) child;
        if (playerView.getPlayer() != null) {
          playerView.getPlayer().release();
        }
        playerView.setPlayer(null);
      } else if (child instanceof ViewGroup) {
        releaseChildPlayerViews((ViewGroup) child);
      }
    }
  }
}
