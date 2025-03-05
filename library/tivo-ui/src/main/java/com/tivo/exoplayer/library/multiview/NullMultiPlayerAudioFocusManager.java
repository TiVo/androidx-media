package com.tivo.exoplayer.library.multiview;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Player;

/**
 * Empty implementation of {@link MultiPlayerAudioFocusManagerApi}. This class is used when focus
 * is managed elsewhere and we can always assume we have audio focus.
 */
public class NullMultiPlayerAudioFocusManager implements MultiPlayerAudioFocusManagerApi { }
