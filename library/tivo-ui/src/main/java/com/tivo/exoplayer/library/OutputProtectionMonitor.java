package com.tivo.exoplayer.library;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaDrm;
import android.media.MediaDrmResetException;
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

    public static final int SKIP_HDCP_CHECK = 0;
    public static final int FORCE_HDCP_CHECK = 1;

    private static final int MSG_UPDATE_HDCP_STATUS = 1;
    private static final String TAG = "OutputProtectionMonitor";

    // Check after 1, 4, 7, 10, 20, 30 sec and every 5 minutes
    private static int[] CHECK_DELAYS = { 1, 3, 3, 3, 10, 10, 60 * 5 };

    private int delayIdx = 0;
    // assume output is secure until discovered otherwise
    private boolean isSecure = true;
    // assume HDCP is below 2.2 until discovered otherwise
    private boolean isHdcpLevelV2_2 = false;
    private int hdcpLevelOfConnectedTV = MediaDrm.HDCP_NO_DIGITAL_OUTPUT;
    private boolean isRefreshStateRequested = false;
    private boolean isHotPlugged = true;
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
                        isHotPlugged = true;
                        refreshState();
                    }
                    else {
                        Log.w(TAG, "HDMI Hotplug - unplugged");
                        isHotPlugged = false;
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
        drmHandlerThread.quit();
        drmHandlerThread = null;

        this.removeCallbacksAndMessages(null);
    }

    public void refreshState()
    {
        refreshState(this.requiredHdcpLevel, true);
    }

    /**
     * Requests reevaluation of current protection status.
     * @param requiredHdcpLevel HDCP level required to be reached
     * @param checkImmediate if true the HDCP is level is checked immediately.
     * Otherwise the HDCP is checked on next HDMI hotplug event
     */
    public void refreshState(int requiredHdcpLevel, boolean checkImmediate)
    {
        Log.i(TAG, "Refresh");
        delayIdx = 0;
        isRefreshStateRequested = true;
        this.requiredHdcpLevel = requiredHdcpLevel;
        // 1 == force check HDCP
        // 0 == skip the HDCP check
        int checkNow = checkImmediate? FORCE_HDCP_CHECK : SKIP_HDCP_CHECK;
        drmHandler.obtainMessage(MSG_UPDATE_HDCP_STATUS, requiredHdcpLevel, checkNow).sendToTarget();
    }

    /**
     * Returns true if output is secure.
     * @return
     */
    public boolean isOutputSecure() {
        // Output is secured either HDCP_1X or HDCP_2X is reached
        return isSecure || isHdcpLevelV2_2;
    }

    /**
     * Returns true if HDCP 2.2
     * @return
     */
    public boolean isHdcpV2_2() {
        return isHdcpLevelV2_2;
    }

    public boolean isHotPlugged() {
        return isHotPlugged;
    }

    /**
     * @return one of {@link #HDCP_LEVEL_UNKNOWN}, {@link #HDCP_NONE}, {@link
     * #HDCP_V1}, {@link #HDCP_V2}, {@link #HDCP_V2_1}, {@link #HDCP_V2_2} or
     * {@link #HDCP_NO_DIGITAL_OUTPUT}.
     */
    public int getHdcpLevelOfConnectedTv()
    {
        return hdcpLevelOfConnectedTV;
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
                boolean hdcpLevelChanged = isLevelAtLeastHdcp2_2(msg.arg1);
                boolean newIsSecure = evaluateSecureStatus(isHdcpSecure);
                hdcpLevelOfConnectedTV = msg.arg1;
                if (isSecure != newIsSecure)
                {
                    isSecure = newIsSecure;
                    notifyStatusChange();
                }
                // Notify the change in HDCP level. Notify always after refresh is called.
                if ((isHdcpLevelV2_2 != hdcpLevelChanged) || isRefreshStateRequested) {
                    Log.w(TAG, "hdcpLevel HDCP_V2_2 is changed to " + hdcpLevelChanged);
                    isHdcpLevelV2_2 = hdcpLevelChanged;
                    notifyStatusChange();
                    isRefreshStateRequested = false;
                }
                break;
            default:
                Log.e(TAG, "Unknown message: " + msg.what);
        }
    }

    private boolean isLevelAtLeastHdcp2_2(/* @MediaDrm.HdcpLevel */ int hdcpLevel) 
    {
        return hdcpLevel == MediaDrm.HDCP_V2_2 ||
            // HDCP 2.3 only in > Pie 
            Build.VERSION.SDK_INT > 28 && hdcpLevel == MediaDrm.HDCP_V2_3;
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
                drmHandler.sendMessageDelayed(drmHandler.obtainMessage(MSG_UPDATE_HDCP_STATUS, requiredHdcpLevel, FORCE_HDCP_CHECK), CHECK_DELAYS[delayIdx]*1000);
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

        Method getConnectedHdcpLevelMethod;
        OutputProtectionMonitor protectionMonitor;
        @HdcpLevel int requiredHdcpLevel;
        int hdcpLevel = 0; /* MediaDrm.HDCP_LEVEL_UNKNOWN - only in Android Pie */
        boolean isErrorGettingHdcpLevel = false;

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public DrmHandler(Looper looper, OutputProtectionMonitor protectionMonitor) {
            super(looper);
            this.protectionMonitor = protectionMonitor;
            this.requiredHdcpLevel = protectionMonitor.getRequiredHdcpLevel();
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            boolean isSecure = false;

            switch (msg.what) {
                case MSG_UPDATE_HDCP_STATUS:
                    removeMessages(MSG_UPDATE_HDCP_STATUS);
                    requiredHdcpLevel = msg.arg1;
                    if (msg.arg2 == SKIP_HDCP_CHECK) {
                        // Start the HDCP check only when its forced
                        return;
                    }
                    isSecure = checkHdcpStatus();

                    break;
                default:
                    Log.e(TAG, "Unhandled message: " + msg.what);
            }

            // post status update back to the monitor
            if (isErrorGettingHdcpLevel) {
                protectionMonitor.obtainMessage(msg.what, 0, 0, isSecure).sendToTarget();
            }
            else {
                protectionMonitor.obtainMessage(msg.what, hdcpLevel, 0, isSecure).sendToTarget();
            }
        }

        // Utility function to get HDCP for API level 28 and above
        private void getHdcpLevelV28()
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    MediaDrm widevineDrm = new MediaDrm(C.WIDEVINE_UUID);
                    if (evaluateValidityWidevineKey(widevineDrm)) {
                        hdcpLevel = widevineDrm.getConnectedHdcpLevel();
                    }
                    else {
                        // Widevine key is not valid, default HDCP to 1_x
                        hdcpLevel = MediaDrm.HDCP_V1;
                    }
                    widevineDrm.close();
                    isErrorGettingHdcpLevel = false;
                } catch (UnsupportedSchemeException e) {
                    Log.e(TAG, "Widevine UUID is not supported on this version: " + Build.VERSION.RELEASE);
                } catch (@SuppressLint({"NewApi", "LocalSuppress"}) MediaDrmResetException e) {
                    Log.e(TAG, "MediaDrmResetException" + Build.VERSION.RELEASE);
                } catch (Exception e) {
                    Log.e(TAG, "Unhandled exception :"+e.toString());
                }
            }
        }

        // Utility function to get HDCP for API level from 18 to 27
        private void getHdcpLevelV18To27()
        {
            // Do Not worry about API 17 and prior versions since there is no
            // Widevine support.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && 
                Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                try {
                    MediaDrm widevineDrm = new MediaDrm(C.WIDEVINE_UUID);
                    String hdcpLevelString = widevineDrm.getPropertyString("hdcpLevel");
                    switch (hdcpLevelString) {
                        case "HDCP-1.x":
                            hdcpLevel = MediaDrm.HDCP_V1;
                            break;
                        case "HDCP-2.2":
                            hdcpLevel = MediaDrm.HDCP_V2_2;
                            break;
                        case "HDCP-2.3":
                            hdcpLevel = MediaDrm.HDCP_V2_3;
                            break;
                        case "Disconnected":
                            hdcpLevel = MediaDrm.HDCP_NO_DIGITAL_OUTPUT;
                            break;
                        default:
                            hdcpLevel = MediaDrm.HDCP_NONE;
                    }
                    // In API versions 18 through 27, MediaDrm release
                    // should be called
                    widevineDrm.release();
                    isErrorGettingHdcpLevel = false;
                } catch (UnsupportedSchemeException e) {
                    Log.e(TAG, "Widevine UUID is not supported on this version: " + Build.VERSION.RELEASE);
                }
            } 
        }

        private boolean evaluateValidityWidevineKey(MediaDrm widevineDrm) {
            boolean valid = true;
            if ("SEI Robotics".equals(Build.MANUFACTURER)) {
                // SEI Robotics is the only device having a problem where the Widevine Keys are
                // invalid. For all other platforms its considered valid by default
                String systemId = widevineDrm.getPropertyString("systemId");
                String securityLevel = widevineDrm.getPropertyString("securityLevel");

                if ((systemId != null) && securityLevel.equals("L1")) {
                    Log.d(TAG, "Widevine Key is valid on systemId " + systemId + " " + securityLevel);
                    valid = true;
                } else {
                    Log.e(TAG, "Widevine Key is Invalid");
                    valid = false;
                }
            }
            return valid;
        }

        private boolean checkHdcpStatus() {
            isErrorGettingHdcpLevel = true;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getHdcpLevelV28();
            } else {
                getHdcpLevelV18To27();
            }

            if (isErrorGettingHdcpLevel) {
                // FireTV devices have been tested as the only ones known to
                // not support getConnectedHdcpLevel(), and they already
                // disable all HDMI output when HDCP is not authorized so are
                // secure to play out on in all situations
                return true;
            }
            else {
                switch (hdcpLevel) {
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
                        else if (requiredHdcpLevel == HDCP_2X && hdcpLevel >= MediaDrm.HDCP_V2_2 ) {
                            Log.w(TAG, "Required HDCP level of HDCP2.2 is met");
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
