package com.tivo.exoplayer.library.logging;

import android.os.SystemClock;
import androidx.annotation.Nullable;

import java.io.IOException;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSourceEventListener;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.tivo.exoplayer.library.util.LoggingUtils;

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

    public ExtendedEventLogger(@Nullable MappingTrackSelector trackSelector) {
        this(trackSelector, DEFAULT_TAG);
    }

    public ExtendedEventLogger(@Nullable MappingTrackSelector trackSelector, String tag) {
        super(trackSelector, tag);
        startTimeMs = SystemClock.elapsedRealtime();
        this.tag = tag;
    }

    // Extends - call super, but add something

    @Override
    public void onTracksChanged(EventTime eventTime, TrackGroupArray ignored, TrackSelectionArray trackSelections) {
        super.onTracksChanged(eventTime, ignored, trackSelections);

        // Reset for showing level changes
        currentLoadingVideoFormat = null;
    }

    @Override
    public void onDownstreamFormatChanged(EventTime eventTime, MediaLoadData mediaLoadData) {
        super.onDownstreamFormatChanged(eventTime, mediaLoadData);
        if (mediaLoadData.trackType == C.TRACK_TYPE_VIDEO || mediaLoadData.trackType == C.TRACK_TYPE_DEFAULT) {
            if (currentPlayingVideoFormat == null) {
                currentPlayingVideoFormat = mediaLoadData.trackFormat;
                logd(eventTime, "videoFormatInitial", LoggingUtils.getVideoLevelStr(mediaLoadData.trackFormat));
            } else {
                logd(eventTime, "videoFormatChanged",
                        "Old: " + LoggingUtils.getVideoLevelStr(currentPlayingVideoFormat) + " New: " + LoggingUtils.getVideoLevelStr(mediaLoadData.trackFormat));
            }
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
        str.append(" uri: "); str.append(loadEventInfo.uri);
        logd(eventTime,"loadStarted", str.toString());

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
        loge(eventTime, "internalError","loadError - URL: " + loadEventInfo.uri , error);
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
