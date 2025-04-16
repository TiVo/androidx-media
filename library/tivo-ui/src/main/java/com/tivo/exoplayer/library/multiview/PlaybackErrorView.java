package com.tivo.exoplayer.library.multiview;

import static com.google.android.exoplayer2.Player.STATE_IDLE;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.tivo.exoplayer.library.R;
import com.tivo.exoplayer.library.errorhandlers.PlayerErrorHandlerListener;

/**
 * A custom view that displays an error message and an optional progress indicator based on the state reported by
 * the {@link PlayerErrorHandlerListener}.
 */
public class PlaybackErrorView extends LinearLayout implements PlayerErrorHandlerListener {

  private static final String TAG = "PlaybackErrorView";
  private final TextView errorMessageView;
  private final ProgressBar indeterimateProgress;
  private final View progressView;
  private GridLocation gridLocation;
  private HandlingStatus lastHandlingStatus = HandlingStatus.SUCCESS;

  public PlaybackErrorView(Context context) {
    this(context, null);
  }

  public PlaybackErrorView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public PlaybackErrorView(Context context, AttributeSet attrs, int defStyleAttr) {
    this(context, attrs, defStyleAttr, 0);
  }

  public PlaybackErrorView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    LayoutInflater.from(context).inflate(R.layout.multi_view_playback_error_view, this);

    errorMessageView = findViewById(R.id.error_recovery_error_message);
    progressView = findViewById(R.id.error_recovery_progress_view);
    indeterimateProgress = findViewById(R.id.error_recovery_progress_indeterminate);
  }

  private String getLogTag() {
    return gridLocation != null ?  (TAG + "-" + gridLocation.getViewIndex()) : TAG;
  }

  @Override
  public void playerErrorProcessed(@Nullable PlaybackException error, HandlingStatus status, Player failingPlayer) {
    lastHandlingStatus = status;
    String errorMessage = error == null ? "unknown" : error.getMessage();
    Log.d(getLogTag(), "playerErrorProcessed() - error: " + errorMessage
        + ", status: " + status);
    switch (status) {
      case IN_PROGRESS:
        assert error != null;
        Log.d(getLogTag(), "playerErrorProcessed() - recovery in progress, error: " + error.getMessage());
        showErrorState(error, failingPlayer, status);
        break;

      case SUCCESS:
        Log.d(getLogTag(), "playerErrorProcessed() - recovered from " + errorMessage);
        showErrorState(error, failingPlayer, status);
        break;

      case WARNING:
        assert error != null;
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
          Log.e(getLogTag(), "playerErrorProcessed() - Unsupported format: " + reason, error);

        } else {
          Log.e(getLogTag(), "playerErrorProcessed() - no supported tracks found", error);
        }
        break;

      case FAILED:
        assert error != null;
        Log.e(getLogTag(), "playerErrorProcessed() - fatal error:  " + error.getMessage(), error);
        showErrorState(error, failingPlayer, status);
        break;

      case ABANDONED:
        Log.d(getLogTag(), "playerErrorProcessed() - playback abandoned, error: " + errorMessage);
        showErrorState(error, failingPlayer, status);
        break;
    }
    updateVisibility(status);
  }

  private void showErrorState(@Nullable PlaybackException error, Player failingPlayer, HandlingStatus status) {
    if (errorMessageView != null) {
      switch (status) {
        case IN_PROGRESS:
          assert error != null;
          errorMessageView.setText(getContext().getString(R.string.retrying_on_error, String.valueOf(error.errorCode)));
          break;
        case SUCCESS:
          errorMessageView.setText("");
          break;
        case WARNING:   // TODO - handle warning
          errorMessageView.setText("");
          break;
        case FAILED:
          assert error != null;
          errorMessageView.setText(getContext().getString(R.string.playback_error, String.valueOf(error.errorCode)));
          break;
      }
    } else {
      setVisibility(INVISIBLE);
    }
  }

  private void updateVisibility(HandlingStatus handlingStatus) {
    switch (handlingStatus) {
      case IN_PROGRESS:
        setVisibility(VISIBLE);
        progressView.setVisibility(View.VISIBLE);
        indeterimateProgress.setVisibility(View.VISIBLE);   // TODO - use deterministic progress
        break;
      case SUCCESS:
        setVisibility(INVISIBLE);
        break;
      case FAILED:
        setVisibility(VISIBLE);
        progressView.setVisibility(View.GONE);
        indeterimateProgress.setVisibility(View.GONE);
        break;
      case WARNING:
        // TODO - handle warning
        break;
      case ABANDONED:
        setVisibility(INVISIBLE);
        break;
    }
  }

  /**
   * Sets the location of this error view in the multi-view layout, this
   * is used for logging.
   *
   * @param gridLocation - the location of the grid view
   */
  void setGridLocation(GridLocation gridLocation) {
    this.gridLocation = gridLocation;
  }

  /**
   * Called when the playback state changes or player is removed from view.
   *
   * <p>If the state changes to other than IDLE, and an error view was showing
   * and the error was fatal, hide it.</p>
   *
   * @param playbackState - the new playback state
   * @param currentPlayer - the current player, or null if player removed from view
   */
  void onErrorStateChanged(int playbackState, @Nullable Player currentPlayer) {
    boolean playerRecoveredOrReleased = playbackState != STATE_IDLE || currentPlayer == null;
    if (playerRecoveredOrReleased && lastHandlingStatus == HandlingStatus.FAILED) {
      lastHandlingStatus = HandlingStatus.SUCCESS;
      updateVisibility(lastHandlingStatus);
    }
  }
}
