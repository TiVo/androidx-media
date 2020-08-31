package com.tivo.exoplayer.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.Display;
import android.view.WindowManager;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.Log;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;

/*
 * An auxiliary class for applications that require
 * output copy protection. It monitors several output
 * parameters to ensure that protection meets set levels.
 */
public class OutputProtectionMonitor extends Handler {

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            HDCP_NONE,
            HDCP_1X,
            HDCP_2X
    })
    public @interface HdcpLevel {}

    public static final int HDCP_NONE = 0;
    public static final int HDCP_1X = 1;
    public static final int HDCP_2X = 2;

    private static final int MSG_UPDATE_HDCP_STATUS = 1;
    private static final String TAG = "OutputProtectionMonitor";

    // Check after 1, 4, 7, 10, 20, 30 sec and every 5 minutes
    private static int[] CHECK_DELAYS = { 1, 3, 3, 3, 10, 10, 60 * 5 };

    private int delayIdx = 0;
    // assume output is secure until discovered otherwise
    private boolean isSecure = true;
    private @HdcpLevel int requiredHdcpLevel;
    HandlerThread drmHandlerThread;
    DrmHandler drmHandler;
    Context context;
    BroadcastReceiver receiver;
    Runnable statusChangeRunnable;
    ProtectionChangedListener statusChangeCallback;

    /**
     * Called back if protection status changes, in the same thread that called
     * {@link OutputProtectionMonitor#start()}.
     */
    public interface ProtectionChangedListener {

        /**
         * Called when the HDCP secure status changes
         * @param isSecure - current status, true if is secure
         */
        void protectionChanged(boolean isSecure);

    }
    /**
     * Creates OutputProtectionMonitor instance.
     * @param context application context
     * @param requiredHdcpLevel HDCP level required to be secure
     * @param changeCallback callback to notify client about status change
     */
    public OutputProtectionMonitor(Context context, @HdcpLevel int requiredHdcpLevel, ProtectionChangedListener changeCallback) {
        super(context.getMainLooper());
        this.context = context;
        this.requiredHdcpLevel = requiredHdcpLevel;
        this.statusChangeCallback = changeCallback;
    }

    /**
     * Creates OutputProtectionMonitor instance.
     * @param context application context
     * @param requiredHdcpLevel HDCP level required to be secure
     * @param changeRunnable callback to notify client about status change
     */
    public OutputProtectionMonitor(Context context, @HdcpLevel int requiredHdcpLevel, Runnable changeRunnable) {
        super(context.getMainLooper());
        this.context = context;
        this.requiredHdcpLevel = requiredHdcpLevel;
        this.statusChangeRunnable = changeRunnable;
    }

    /**
     * Starts monitoring output security level.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void start() {
        Log.d(TAG, "Starting");
        drmHandlerThread = new HandlerThread("HdcpDrmThread");
        drmHandlerThread.start();
        drmHandler = new DrmHandler(drmHandlerThread.getLooper(), this);

        // HDMI hotplug events are not reliable on Android, so still
        // need to confirm true HDMI status via MediaDrm
        receiver = new BroadcastReceiver()
        {
            public void onReceive(Context context, Intent intent)
            {
                if (intent.getAction().equals
                        (AudioManager.ACTION_HDMI_AUDIO_PLUG)) {
                    if (intent.getIntExtra
                            (AudioManager.EXTRA_AUDIO_PLUG_STATE, -1) == 1) {
                        Log.w(TAG, "HDMI Hotplug - plugged in");
                        refreshState();
                    }
                    else {
                        Log.w(TAG, "HDMI Hotplug - unplugged");
                        refreshState();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.ACTION_HDMI_AUDIO_PLUG);
        context.registerReceiver(receiver, filter);

        // issue HDCP probe at start
        refreshState();
    }

    /**
     * Stops monitoring and releases all resources.
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public void stop() {
        Log.d(TAG, "Stopping");
        context.unregisterReceiver(receiver);

        drmHandler.removeCallbacksAndMessages(null);
        drmHandler.cleanupMediaDrm();
        drmHandlerThread.quit();
        drmHandlerThread = null;

        this.removeCallbacksAndMessages(null);
    }

    /**
     * Requests reevaluation of current protection status.
     */
    public void refreshState()
    {
        Log.i(TAG, "Refresh");
        delayIdx = 0;
        drmHandler.obtainMessage(MSG_UPDATE_HDCP_STATUS).sendToTarget();
    }

    /**
     * Returns true if output is secure.
     * @return
     */
    public boolean isOutputSecure() {
        return isSecure;
    }

    /**
     * Returns HDCP level required for the output to be secure.
     * @return
     */
    public int getRequiredHdcpLevel() {
        return requiredHdcpLevel;
    }

    @Override
    /**
     * Handles updates from DrmRequestHandler.
     */
    public void handleMessage(@NonNull Message msg) {
        switch(msg.what) {
            case MSG_UPDATE_HDCP_STATUS:
                boolean isHdcpSecure = (Boolean) msg.obj;
                boolean newIsSecure = evaluateSecureStatus(isHdcpSecure);
                if (isSecure != newIsSecure)
                {
                    isSecure = newIsSecure;
                    notifyStatusChange();
                }
                break;
            default:
                Log.e(TAG, "Unknown message: " + msg.what);
        }
    }

    private void notifyStatusChange()
    {
        if (statusChangeCallback != null) {
            Log.i(TAG, "Notify client callback");
            this.post(new Runnable() {
                @Override
                public void run() {
                    statusChangeCallback.protectionChanged(isSecure);
                }
            });
        }

        if (statusChangeRunnable != null) {
            Log.i(TAG, "Notify client runnable");
            this.post(statusChangeRunnable);
        }
    }

    private boolean evaluateSecureStatus(boolean isHdcpSecure) {
        if (!isHdcpSecure) {
            if (drmHandlerThread.isAlive()) {
                // Backoff algorithm is needed to mitigate a small memory leak that
                // occurs every time Widevine MediaDrm is accessed
                Log.w(TAG, String.format("HDCP is not secure, recheck in %ds", CHECK_DELAYS[delayIdx]));
                drmHandler.sendMessageDelayed(drmHandler.obtainMessage(MSG_UPDATE_HDCP_STATUS), CHECK_DELAYS[delayIdx]*1000);
                if (delayIdx < CHECK_DELAYS.length - 1) {
                    delayIdx++;
                }
            }
            return false;
        }

        WindowManager windowManager =
                (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int displayFlags = 0;
        if (android.os.Build.VERSION.SDK_INT >= 17) {
            displayFlags = windowManager.getDefaultDisplay().getFlags();
        }
        if ((displayFlags & Display.FLAG_SECURE) != Display.FLAG_SECURE) {
            isSecure = false;
            Log.w(TAG, "Display is not secure");
            return false;
        }
        if ((displayFlags &
                Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) !=
                Display.FLAG_SUPPORTS_PROTECTED_BUFFERS) {
            isSecure = false;
            Log.w(TAG,"Display does not support protected buffers");
            return false;
        }

        Log.i(TAG, "Output is secure");
        return true;
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static class DrmHandler extends Handler {

        MediaDrm widevineDrm;
        Method getConnectedHdcpLevelMethod;
        OutputProtectionMonitor protectionMonitor;
        @HdcpLevel int requiredHdcpLevel;

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public DrmHandler(Looper looper, OutputProtectionMonitor protectionMonitor) {
            super(looper);
            this.protectionMonitor = protectionMonitor;
            this.requiredHdcpLevel = protectionMonitor.getRequiredHdcpLevel();

            try {
                widevineDrm = new MediaDrm(C.WIDEVINE_UUID);
            } catch (UnsupportedSchemeException e) {
                Log.e(TAG, "Widevine UUID is not supported on this version: " + Build.VERSION.RELEASE);
            }
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            boolean isSecure = false;

            switch (msg.what) {
                case MSG_UPDATE_HDCP_STATUS:
                    removeMessages(MSG_UPDATE_HDCP_STATUS);
                    isSecure = checkHdcpStatus();

                    break;
                default:
                    Log.e(TAG, "Unhandled message: " + msg.what);
            }

            // post status update back to the monitor
            protectionMonitor.obtainMessage(msg.what, isSecure).sendToTarget();
        }

        private void cleanupMediaDrm() {
            if (widevineDrm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    widevineDrm.close();
                } else {
                    widevineDrm.release();
                }
            }
        }

        private boolean checkHdcpStatus() {
            Integer hdcpLevel = null;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && widevineDrm != null) {
                hdcpLevel = widevineDrm.getConnectedHdcpLevel();
            }

            if (hdcpLevel == null) {
                // FireTV devices have been tested as the only ones known to
                // not support getConnectedHdcpLevel(), and they already
                // disable all HDMI output when HDCP is not authorized so are
                // secure to play out on in all situations
                return true;
            }
            else {
                switch (hdcpLevel.intValue()) {
                    case MediaDrm.HDCP_LEVEL_UNKNOWN:
                        Log.w(TAG, "MediaDrm HDCP level is " +
                                "HDCP_LEVEL_UNKNOWN");
                        return false;

                    case MediaDrm.HDCP_NONE:
                        Log.w(TAG, "MediaDrm HDCP level is HDCP_NONE");
                        return false;

                    case MediaDrm.HDCP_NO_DIGITAL_OUTPUT:
                        Log.w(TAG, "There is no digital output");
                        return true;
                    default:
                        Log.w(TAG, "MediaDrm HDCP level indicates " + hdcpLevel);
                        if (requiredHdcpLevel == HDCP_1X && hdcpLevel >= MediaDrm.HDCP_V1) {
                            Log.w(TAG, "Required HDCP level of HDCP1.x is met");
                            return true;
                        }
                        else if (requiredHdcpLevel == HDCP_2X && hdcpLevel >= MediaDrm.HDCP_V2 ) {
                            Log.w(TAG, "Required HDCP level of HDCP2.x is met");
                            return true;
                        }
                        else {
                            Log.w(TAG, "Unhandled case for required level: " + requiredHdcpLevel);
                            return false;

                        }
                }
            }
        }
    }
}
