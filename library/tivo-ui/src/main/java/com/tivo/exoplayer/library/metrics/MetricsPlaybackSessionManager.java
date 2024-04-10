package com.tivo.exoplayer.library.metrics;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.SilenceMediaSource;
import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackSessionManager;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Log;

public class MetricsPlaybackSessionManager implements PlaybackSessionManager {
    private static final String TAG = "MetricsPlaybackSessionManager";

    private @Nullable String currentSessionId;
    private final PlaybackSessionManager delegate;

    // Sessions keyed by session id.
    private final Map<String, SessionInformation> sessionsById = new HashMap<>();
    private final Map<Long, SessionInformation> sessionsByCreateTime = new HashMap<>();

    public class SessionInformation {
        private @Nullable String url;
        private final long creationTime;

        public SessionInformation(long creationTime) {
            this.creationTime = creationTime;
        }

        public void setSessionUrl(@NonNull String url) {
            this.url = url;
        }

        public String getSessionUrl() {
            return url;
        }
    }

    /**
     * Wrap the delegate Listener implementation with local one so we can keep track of Session lifecycle
     * and keep our own list of information about the sessions (eg. the URL)
     */
    public class SessionLifecycleListener implements Listener {
        private Listener delegateListener;

        public SessionLifecycleListener(Listener delegateListener) {
            this.delegateListener = delegateListener;
        }

        @Override
        public void onSessionCreated(AnalyticsListener.EventTime eventTime, String sessionId) {
            Log.d(TAG, "session created, id: " + sessionId + " " + timelineDebugString(eventTime));
            delegateListener.onSessionCreated(eventTime, sessionId);
            SessionInformation info = new SessionInformation(eventTime.realtimeMs);
            sessionsById.put(sessionId, info);
            sessionsByCreateTime.put(eventTime.realtimeMs, info);
            info.setSessionUrl(getUrlFromTimeline(eventTime.timeline));
        }

        @Override
        public void onSessionActive(AnalyticsListener.EventTime eventTime, String sessionId) {
            Log.d(TAG, "session active, id: " + sessionId + " " + timelineDebugString(eventTime));
            currentSessionId = sessionId;
            delegateListener.onSessionActive(eventTime, sessionId);
        }

        @Override
        public void onAdPlaybackStarted(AnalyticsListener.EventTime eventTime, String contentSessionId, String adSessionId) {
            Log.d(TAG, "Ad playback started, adSessionId: " + adSessionId + " content sessionId: " + contentSessionId + " " + timelineDebugString(eventTime));
            delegateListener.onAdPlaybackStarted(eventTime, contentSessionId, adSessionId);
        }

        @Override
        public void onSessionFinished(AnalyticsListener.EventTime eventTime, String sessionId, boolean automaticTransitionToNextPlayback) {
            Log.d(TAG, "session finished, id: " + sessionId + " " + timelineDebugString(eventTime));
            delegateListener.onSessionFinished(eventTime, sessionId, automaticTransitionToNextPlayback);
            SessionInformation info = sessionsById.remove(sessionId);
            if (info != null) {
                sessionsByCreateTime.remove(info.creationTime);
            }
            currentSessionId = null;
        }
    }

    public MetricsPlaybackSessionManager(PlaybackSessionManager delegate) {
        this.delegate = delegate;
    }

    public SessionInformation getSessionInformationFor(Long createTime) {
        return sessionsByCreateTime.get(createTime);
    }

    @Override
    public void setListener(Listener listener) {
        this.delegate.setListener(new SessionLifecycleListener(listener));
    }

    @Override
    public String getSessionForMediaPeriodId(Timeline timeline, MediaSource.MediaPeriodId mediaPeriodId) {
        return delegate.getSessionForMediaPeriodId(timeline, mediaPeriodId);
    }

    @Override
    public boolean belongsToSession(AnalyticsListener.EventTime eventTime, String sessionId) {
        return delegate.belongsToSession(eventTime, sessionId);
    }

    @Override
    public void updateSessions(AnalyticsListener.EventTime eventTime) {
        Timeline timeline = eventTime.timeline;
        if (timeline != Timeline.EMPTY) {
            delegate.updateSessions(eventTime);
        }
    }

    @Override
    public void updateSessionsWithTimelineChange(AnalyticsListener.EventTime eventTime) {
        Timeline timeline = eventTime.timeline;
        if (timeline == Timeline.EMPTY) {
            Log.d(TAG, "timelineUpdated - was reset to EMPTY, eventTime: " + eventTime.realtimeMs + " sessionId: " + currentSessionId);
        } else if (currentSessionId == null){
            String mediaItemId = getMediaItemDescription(timeline);
            Log.d(TAG, "timelineUpdated - no current session, MediaItem: " + mediaItemId + " , eventTime: " + eventTime.realtimeMs + " sessionId: " + currentSessionId);
        }
        delegate.updateSessionsWithTimelineChange(eventTime);
    }

    @Override
    public void updateSessionsWithDiscontinuity(AnalyticsListener.EventTime eventTime, int reason) {
        delegate.updateSessionsWithDiscontinuity(eventTime, reason);
    }

    @Nullable
    @Override
    public String getActiveSessionId() {
        return delegate.getActiveSessionId();
    }

    @Override
    public void finishAllSessions(AnalyticsListener.EventTime eventTime) {
        delegate.finishAllSessions(eventTime);
    }

    private MediaItem getMediaItem(Timeline timeline) {
        Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
        return window.mediaItem;
    }

    private String getUrlFromTimeline(Timeline timeline) {
        MediaItem mediaItem = getMediaItem(timeline);
        Uri uri = mediaItem.playbackProperties != null
            ? mediaItem.playbackProperties.uri
            : null;
        return uri != null ? String.valueOf(uri) : mediaItem.mediaId;
    }

    @NonNull
    private String getMediaItemDescription(Timeline timeline) {
        String value = "MediaItem [";
        MediaItem mediaItem = getMediaItem(timeline);
        if (! mediaItem.mediaId.equals(MediaItem.DEFAULT_MEDIA_ID)) {
            value += "mediaId: " + mediaItem.mediaId;
        } else if (mediaItem.playbackProperties != null) {
            value += "URI: " + mediaItem.playbackProperties.uri;
        } else {
            value = mediaItem.toString();
        }
        return value + "]";
    }

    private String timelineDebugString(AnalyticsListener.EventTime eventTime) {
        String value = "";
        Timeline timeline = eventTime.timeline;
        if (timeline.isEmpty()) {
            value = "empty timeline, at realtimeMs: " + eventTime.realtimeMs;
        } else {
            value = "active timeline, at realtimeMs: " + eventTime.realtimeMs + " " + getMediaItemDescription(timeline);
        }
        return value;
    }
}
