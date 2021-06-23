package com.tivo.exoplayer.library.util;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Format;

/**
 * Utilities for formatting various ExoPlayer objects as strings for logging and analytics
 * purposes
 */
public class LoggingUtils {

    // When we subclass EventLogger in ExoPlayer move the method in there to use this one.
    /**
     * User displayable identifying string for an {@link Format} object for a video track
     * Display format like "{id} - widthXHeight@bitrate" or "@lt;none@gt;" if the format is null
     *
     * @param format null or Format object
     * @return identifying string.
     */
    public static String getVideoLevelStr(@Nullable Format format) {
        String videoLevel = "<none>";
        if (format != null) {
            String label = format.label == null ? format.id : format.label;
            videoLevel = label + " - " + format.width + "x" + format.height + "@" + format.bitrate;
        }
        return videoLevel;
    }
}
