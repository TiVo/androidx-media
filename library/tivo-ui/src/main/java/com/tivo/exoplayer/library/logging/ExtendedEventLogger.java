package com.tivo.exoplayer.library.logging;

import android.os.SystemClock;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.LivePlaybackSpeedControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Timeline;
import java.io.IOException;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.tivo.exoplayer.library.util.LoggingUtils;
import java.util.Formatter;

/**
 * Extend the base EventLogger (after opening it a few methods to allow this) with our own
 * code to log more detail on load events and add buffered time to the base event
 * string
 *
 */
public class ExtendedEventLogger extends EventLogger {
    private static final String DEFAULT_TAG = "EventLogger";        // TODO we can change if we change docs
    protected final long startTimeMs;
    protected final String tag;

    private @Nullable Format currentLoadingVideoFormat;
    private @Nullable Format currentPlayingVideoFormat;
    private long lastTimelineUpdateMs = C.TIME_UNSET;
    private long lastWindowEndMs = C.TIME_UNSET;
    private boolean firstTimeLineUpdateSeen;

    private @Nullable LivePlaybackSpeedControl currentSpeedControl;

    public ExtendedEventLogger(@Nullable MappingTrackSelector trackSelector) {
        this(trackSelector, DEFAULT_TAG);
    }

    public ExtendedEventLogger(@Nullable MappingTrackSelector trackSelector, String tag) {
        super(trackSelector, tag);
        startTimeMs = SystemClock.elapsedRealtime();
        this.tag = tag;
    }

    public void setCurrentSpeedControl(@Nullable LivePlaybackSpeedControl currentSpeedControl) {
        this.currentSpeedControl = currentSpeedControl;
    }

    // Extends - call super, but add something

    @Override
    public void onTracksChanged(EventTime eventTime, TrackGroupArray ignored, TrackSelectionArray trackSelections) {
        super.onTracksChanged(eventTime, ignored, trackSelections);

        // Reset for showing level changes
        currentLoadingVideoFormat = null;
        currentPlayingVideoFormat = null;
    }

    @Override
    public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
        super.onDownstreamFormatChanged(eventTime, mediaLoadData);
        if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT) {
            if (currentPlayingVideoFormat == null) {
                logd(eventTime, "videoFormatInitial", LoggingUtils.getVideoLevelStr(mediaLoadData.trackFormat));
            } else {
                logd(eventTime, "videoFormatChanged",
                        "Old: " + LoggingUtils.getVideoLevelStr(currentPlayingVideoFormat) + " New: " + LoggingUtils.getVideoLevelStr(mediaLoadData.trackFormat));
            }
            currentPlayingVideoFormat = mediaLoadData.trackFormat;
        }
    }


    // Overrides - completely override super, mostly it does nothing for these events

    @Override
    public void onLoadStarted(EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        StringBuilder str = new StringBuilder();
        if (loadEventInfo.dataSpec.length != C.LENGTH_UNSET) {
            str.append(" range(o/l): ");
            str.append(loadEventInfo.dataSpec.position); str.append("/"); str.append(loadEventInfo.dataSpec.length);
        }
        boolean isPlaylistLoad = mediaLoadData.mediaStartTimeMs == C.TIME_UNSET;
        if (! isPlaylistLoad) {
            str.append(" timeMs(s/end): " + mediaLoadData.mediaStartTimeMs); str.append("/"); str.append(mediaLoadData.mediaEndTimeMs);
        }
        str.append(" uri: "); str.append(loadEventInfo.uri);
        if (isPlaylistLoad) {
            logd(eventTime,"loadStartedPlaylist", str.toString());
        } else {
            logd(eventTime,"loadStartedMedia", str.toString());
        }

        if (isVideoTrack(mediaLoadData)) {
            if (currentLoadingVideoFormat == null) {
                currentLoadingVideoFormat = mediaLoadData.trackFormat;
                logd(eventTime, "initialLoadingFormat", LoggingUtils.getVideoLevelStr(mediaLoadData.trackFormat));
            } else {
                if (! currentLoadingVideoFormat.equals(mediaLoadData.trackFormat)) {
                    logd(eventTime, "loadingFormatChanged", "Old: " + LoggingUtils.getVideoLevelStr(currentLoadingVideoFormat)
                            + ", New: " + LoggingUtils.getVideoLevelStr(mediaLoadData.trackFormat));
                    currentLoadingVideoFormat = mediaLoadData.trackFormat;
                }
            }

        }
    }

    @Override
    public void onLoadError(
            EventTime eventTime,
            LoadEventInfo loadEventInfo,
            MediaLoadData mediaLoadData,
            IOException error,
            boolean wasCanceled) {
        String uri = "";
        DataSpec dataSpec = loadEventInfo.dataSpec;
        if (dataSpec.position == 0 && dataSpec.length == C.LENGTH_UNSET) {
            uri = loadEventInfo.uri.toString();
        } else {
            String rangeRequest = "bytes=" + dataSpec.position + "-";
            if (dataSpec.length != C.LENGTH_UNSET) {
                rangeRequest += String.valueOf(dataSpec.position + dataSpec.length - 1);
            }
            uri = loadEventInfo.uri.toString() + " - Range: " + rangeRequest;
        }
        loge(eventTime, "internalError","loadError - URL: " + uri , error);
    }

    @Override
    public void onLoadCanceled(
            EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        logd(eventTime,"loadCanceled", loadEventInfo.toString());
    }

    @Override
    public void onLoadCompleted(
            EventTime eventTime, LoadEventInfo loadEventInfo, MediaLoadData mediaLoadData) {
        StringBuilder str = new StringBuilder();

        if (mediaLoadData.trackFormat != null) {
            long duration = mediaLoadData.mediaEndTimeMs - mediaLoadData.mediaStartTimeMs;
            str.append("trackId: "); str.append(mediaLoadData.trackFormat.id);
            str.append(" load-duration: "); str.append(loadEventInfo.loadDurationMs); str.append("ms");
            str.append(" codecs: "); str.append(mediaLoadData.trackFormat.codecs);
            str.append(" start(dur): "); str.append(mediaLoadData.mediaStartTimeMs);str.append("/");str.append(duration);
            if (loadEventInfo.dataSpec.length != C.LENGTH_UNSET) {
                str.append(" offset/len: ");
                str.append(loadEventInfo.dataSpec.position); str.append("/"); str.append(loadEventInfo.dataSpec.length);
            }
            str.append(" uri: "); str.append(loadEventInfo.uri);

            logd(eventTime, "loadCompletedMedia", str.toString());
        } else {
            str.append(" load-duration: "); str.append(loadEventInfo.loadDurationMs); str.append("ms");
            str.append(" uri: "); str.append(loadEventInfo.uri);
            logd(eventTime, "loadCompletedPlaylist", str.toString());
        }
    }

    @Override
    public void onBandwidthEstimate(
            EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
        float Mbps = (totalBytesLoaded * 8000.0f) / (totalLoadTimeMs * 1_000_000.0f);
        float avgMbps = bitrateEstimate / 1_000_000.0f;

        logd(eventTime, "bandwidthEstimate", "Received BW Estimate.  Loaded Bytes: " + totalBytesLoaded + ", sample: " + Mbps + "(Mbps), estimate: " + avgMbps + "(Mbps)");
    }

    @Override
    public void onPlaybackStateChanged(EventTime eventTime, int state) {
        logi(eventTime, "state", getStateString(state));
    }

    @Override
    public void onTimelineChanged(EventTime eventTime, @Player.TimelineChangeReason int reason) {
        super.onTimelineChanged(eventTime, reason);
        Timeline timeline = eventTime.timeline;
        switch (reason) {

            case Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED:
                lastTimelineUpdateMs = C.TIME_UNSET;
                lastWindowEndMs = C.TIME_UNSET;
                firstTimeLineUpdateSeen = false;
                break;
            case Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE:
                if (! timeline.isEmpty()) {
                    Timeline.Window window = timeline.getWindow(0, new Timeline.Window());

                    StringBuilder logString = new StringBuilder();
                    Formatter formatter = new Formatter(logString);
                    long windowEndMs = window.windowStartTimeMs + window.getDurationMs();
                    long currentUnixTimeMs = window.getCurrentUnixTimeMs();
                    long windowEndLiveOffsetMs = currentUnixTimeMs - windowEndMs;
                    long liveOffsetMs = currentUnixTimeMs - window.windowStartTimeMs - eventTime.eventPlaybackPositionMs;
                    long currentLiveOffsetTargetMs = C.TIME_UNSET;
                    if (currentSpeedControl != null) {
                        currentLiveOffsetTargetMs = C.usToMs(currentSpeedControl.getTargetLiveOffsetUs());
                    }
                    long deltaLastMs = 0;
                    if (lastTimelineUpdateMs != C.TIME_UNSET) {
                        deltaLastMs = eventTime.realtimeMs - lastTimelineUpdateMs;
                    }
                    lastTimelineUpdateMs = eventTime.realtimeMs;

                    long deltaMediaTimeMs = 0;
                    if (lastWindowEndMs != C.TIME_UNSET) {
                        deltaMediaTimeMs = windowEndMs - lastWindowEndMs;
                    }
                    lastWindowEndMs = windowEndMs;

                    String eventName = firstTimeLineUpdateSeen ? "timelineChanged" : "timelineInitialized";
                    if (!firstTimeLineUpdateSeen) {
                        firstTimeLineUpdateSeen = true;
                        if (window.isLive()) {
                            formatter.format("Live - duration: %2$f, endTime: %3$tF %3$tT.%3$tL (%3$d), window live setback: %4$3.5f, startOffset: %1$3.3f, targetLiveOffset: %5$3.5f",
                                (window.durationUs - window.defaultPositionUs) / 1000000.0,
                                window.getDurationMs() /1000.D,
                                windowEndMs,
                                windowEndLiveOffsetMs / 1000.0,
                                currentLiveOffsetTargetMs / 1000.0
                            );
                        } else if (window.windowStartTimeMs != C.TIME_UNSET) {
                            formatter.format("SOCU - duration: %1$f, startTime: %2$tF %2$tT.%2$tL (%2$d), endTime: %3$tF %3$tT.%3$tL (%3$d), defaultStartPosition: %4$3.3f",
                                window.getDurationMs() /1000.D,
                                window.windowStartTimeMs,
                                windowEndMs,
                                window.defaultPositionUs / 1000000.0
                            );
                        } else {
                            formatter.format("VOD - duration: %1$f,  startPosition: %2$3.3f",
                                window.getDurationMs() / 1000.D,
                                window.defaultPositionUs / 1000000.0
                            );
                        }
                    } else {
                        // isLive() must be true for more then one timeline update.
                        assert window.liveConfiguration != null;
                        formatter.format("deltaLast: %1$3.5f, addedLast: %9$3.5f, duration: %2$f, endTime: %3$tF %3$tT.%3$tL (%3$d), window live setback: %5$3.5f, position window offset: %6$f, currentLiveOffset: %4$3.5f, targetLiveOffset: %7$3.5f, currTargetLiveOffset: %8$3.5f",
                            deltaLastMs / 1000.0,
                            window.getDurationMs() / 1000.0,
                            windowEndMs,
                            liveOffsetMs / 1000.0,
                            (currentUnixTimeMs - windowEndMs) / 1000.0,
                            (window.getDurationMs() - eventTime.eventPlaybackPositionMs) / 1000.0,
                            window.liveConfiguration.targetOffsetMs / 1000.0,
                            currentLiveOffsetTargetMs / 1000.0,
                            deltaMediaTimeMs / 1000.0
                        );
                    }
                    logd(eventTime, eventName, logString.toString());
                }
                break;
        }
    }

    protected void logi(EventTime eventTime, String eventName, @Nullable String eventDescription) {
        Log.i(tag, getEventString(eventTime, eventName, eventDescription, /* throwable= */ null));
    }

    private void logd(EventTime eventTime, String eventName, @Nullable String eventDescription) {
        Log.d(tag, getEventString(eventTime, eventName, eventDescription, /* throwable= */ null));
    }

    private boolean isVideoTrack(MediaLoadData loadData) {
        Format format = loadData.trackFormat;
        return  format != null &&
                (loadData.trackType == C.TRACK_TYPE_VIDEO || format.height > 0 || MimeTypes.getVideoMediaMimeType(format.codecs) != null);
    }
}
