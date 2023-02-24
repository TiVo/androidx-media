package com.tivo.exoplayer.library.metrics;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.analytics.PlaybackStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Simple static methods to dump the history saved in PlaybackStats of format, media time and
 * playback state in a simple text list.  Method can be called in the debugger watch window
 */
public class PlaybackMetricsDebug {

  // Debug method
  static String dumpStateHistory(@Nullable PlaybackStats playbackStats) {
      StringBuffer sb = new StringBuffer();

      if (playbackStats != null) {
          ArrayList<Object> all = new ArrayList<>();
          all.addAll(playbackStats.playbackStateHistory);
          all.addAll(playbackStats.videoFormatHistory);
          all.addAll(playbackStats.fatalErrorHistory);
          all.addAll(playbackStats.mediaTimeHistory);
          Collections.sort(all, new Comparator<Object>() {
              @Override
              public int compare(Object o, Object t1) {
                  return (int) (getEventRealTimeMs(o) - getEventRealTimeMs(t1));
              }
          });

          for (Object item : all) {
              sb.append(getItemAsString(item));
              sb.append('\n');
          }
      } else {
          sb.append("No PlaybackStats available");
      }
      return sb.toString();
  }

  private static String getItemAsString(Object item) {
      if (item instanceof PlaybackStats.EventTimeAndPlaybackState) {
          PlaybackStats.EventTimeAndPlaybackState timeAndPlaybackState = (PlaybackStats.EventTimeAndPlaybackState) item;
          return "eventTime: " + timeAndPlaybackState.eventTime.realtimeMs+ " playbackState: " + timeAndPlaybackState.playbackState;
      } else if (item instanceof PlaybackStats.EventTimeAndFormat) {
          PlaybackStats.EventTimeAndFormat timeAndFormat = (PlaybackStats.EventTimeAndFormat) item;
          return "eventTime: " + timeAndFormat.eventTime.realtimeMs + " format: " + Format.toLogString(timeAndFormat.format);
      } else if (item instanceof PlaybackStats.EventTimeAndException) {
          PlaybackStats.EventTimeAndException timeAndException = (PlaybackStats.EventTimeAndException) item;
          return "eventTime: " + timeAndException.eventTime.realtimeMs + " error: " + timeAndException.exception;
      } else if (item instanceof long[]) {
          long[] timeAndPosition = (long[]) item;
          return "eventTime: " + timeAndPosition[0] + " mediaPosition: " + timeAndPosition[1];
      } else {
          throw new RuntimeException();
      }
  }

  private static long getEventRealTimeMs(Object o) {
      if (o instanceof PlaybackStats.EventTimeAndPlaybackState) {
          return ((PlaybackStats.EventTimeAndPlaybackState) o).eventTime.realtimeMs;
      } else if (o instanceof PlaybackStats.EventTimeAndFormat) {
          return ((PlaybackStats.EventTimeAndFormat) o).eventTime.realtimeMs;
      } else if (o instanceof PlaybackStats.EventTimeAndException) {
          return ((PlaybackStats.EventTimeAndException) o).eventTime.realtimeMs;
      } else if (o instanceof long[]) {
          return ((long[]) o)[0];
      } else {
          throw new RuntimeException();
      }
  }
}
