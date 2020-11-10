package com.tivo.exoplayer.demo;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider;
import com.google.android.exoplayer2.ui.TrackNameProvider;
import com.google.android.exoplayer2.util.MimeTypes;
import com.tivo.exoplayer.library.SimpleExoPlayerFactory;
import com.tivo.exoplayer.library.tracks.TrackInfo;

import java.util.List;

/**
 * Simple single choice list to present override based track selections
 * based on [@link {@link com.tivo.exoplayer.library.tracks.TrackInfo}
 */
public class TrackInfoSelectionDialog extends DialogFragment {

  private String title;
  private List<TrackInfo> choices;
  private SimpleExoPlayerFactory exoPlayerFactory;
  private TrackNameProvider nameProvider;

  /**
   * Create dialog with default name provider.
   *  @param title
   * @param choices
   * @param exoPlayerFactory
   */
  public static TrackInfoSelectionDialog createForChoices(String title, List<TrackInfo> choices,
      SimpleExoPlayerFactory exoPlayerFactory) {
    return createForChoices(title, choices, exoPlayerFactory, null);
  }

  /**
   * Create dialog
   *  @param title
   * @param choices
   * @param exoPlayerFactory
   * @param nameProvider
   */
  public static TrackInfoSelectionDialog createForChoices(String title, List<TrackInfo> choices,
      SimpleExoPlayerFactory exoPlayerFactory,
      @Nullable TrackNameProvider nameProvider) {
    return new TrackInfoSelectionDialog(title, choices, exoPlayerFactory, nameProvider);
  }

  private TrackInfoSelectionDialog(String title, List<TrackInfo> choices,
        SimpleExoPlayerFactory exoPlayerFactory,
        @Nullable TrackNameProvider nameProvider) {
    super();
    this.title = title;
    this.choices = choices;
    this.exoPlayerFactory = exoPlayerFactory;
    this.nameProvider = nameProvider;
  }

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return super.onCreateView(inflater, container, savedInstanceState);
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    if (nameProvider == null) {
      nameProvider = new DefaultTrackNameProvider(getResources());
    }
    CharSequence trackNames[] = new CharSequence[choices.size() + 1];
    trackNames[0] = "None";
    // Assume none is selected
    int currentSelectedTrackIndex = 0;

    for (int i = 1; i < trackNames.length; i++) {
      TrackInfo choice = choices.get(i - 1);
      trackNames[i] = choice.setDescWithProvider(nameProvider);
      if (choice.isSelected) {
        currentSelectedTrackIndex = i;
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    builder.setTitle(title);
    builder.setSingleChoiceItems(trackNames, currentSelectedTrackIndex, (dialog, which) -> {
      setSelectedTrackIndex(which);
      dismiss();
    });
    builder.setNegativeButton("Cancel", (dialog, which) -> {
      dismiss();
    });

    return builder.create();
  }

  private void setSelectedTrackIndex(int which) {
    if (which == 0) {   // None choice
      exoPlayerFactory.setRendererState(choices.get(0).type, true);
    } else {
      TrackInfo trackInfo = choices.get(which - 1);
      exoPlayerFactory.setRendererState(trackInfo.type, false);
      exoPlayerFactory.selectTrack(trackInfo);
    }
  }
}
