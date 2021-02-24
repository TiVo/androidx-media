package com.tivo.exoplayer.library.metrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.analytics.PlaybackSessionManager;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsManifest;
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

        public long getCreationTimeMs() {
            return creationTime;
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

    public String getUrlForActiveSession() {
        SessionInformation sessionIdentifier = getCurrentSessionInformation();
        return  sessionIdentifier == null ? null : sessionIdentifier.url;
    }

    public SessionInformation getCurrentSessionInformation() {
        return sessionsById.get(currentSessionId);
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
    public void handleTimelineUpdate(AnalyticsListener.EventTime eventTime) {
        Timeline timeline = eventTime.timeline;
        if (timeline == Timeline.EMPTY) {
            Log.d(TAG, "timelineUpdated - was reset to EMPTY, eventTime: " + eventTime.realtimeMs + " sessionId: " + currentSessionId);
//        } else {
//            String url = getUrlFromTimeline(timeline);
//            Log.d(TAG, "timelineUpdated - with URL: " + url + " , eventTime: " + eventTime.realtimeMs + " sessionId: " + currentSessionId);
        }
        delegate.handleTimelineUpdate(eventTime);
    }


    @Override
    public void handlePositionDiscontinuity(AnalyticsListener.EventTime eventTime, int reason) {
        delegate.handlePositionDiscontinuity(eventTime, reason);
    }

    @Override
    public void finishAllSessions(AnalyticsListener.EventTime eventTime) {
        delegate.finishAllSessions(eventTime);
    }


    private String getUrlFromTimeline(Timeline timeline) {
        Timeline.Window window = timeline.getWindow(0, new Timeline.Window());
        String url = null;
        // TODO - this works for HLS, need something different for DASH.
        // This assumes a SinglePeriodTimeline (so that there is one PlaybackSessionManager session per prepare)
        if (window.manifest instanceof HlsManifest) {
            HlsManifest manifest = (HlsManifest) window.manifest;
            url = manifest.masterPlaylist.baseUri;
        }
        return url;
    }

    private String timelineDebugString(AnalyticsListener.EventTime eventTime) {
        String value = "";
        Timeline timeline = eventTime.timeline;
        if (timeline.isEmpty()) {
            value = "empty timeline, at realtimeMs: " + eventTime.realtimeMs;
        } else {
            Timeline.Window window = timeline.getWindow(0, new Timeline.Window());

            value = "active timeline, at realtimeMs: " + eventTime.realtimeMs;

            String url = getUrlFromTimeline(timeline);
            if (url == null) {
                value += " no URL";
            } else {
                value += " " + url;
            }
        }
        return value;

    }
}
