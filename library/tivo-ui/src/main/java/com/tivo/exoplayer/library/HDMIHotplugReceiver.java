package com.tivo.exoplayer.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Log;

public class HDMIHotplugReceiver {

    public interface HotplugListener {
        void hotPlugEventReceived(boolean Plugged);
    }

    public static final String HDMI_PLUGGED_EVENT = "android.media.action.HDMI_AUDIO_PLUG";
    public static final String HDMI_PLUGGED_STATE = "state";

    private static final String TAG = "HDMIHotplugReceiver";

    private final Context context;
    private final HotplugListener hotPlugListener;
    private final BroadcastReceiver hdmiHotplugBroadcastReceiver;
    private boolean registered;
    boolean hdmiPlugged;

    private Handler handler = new Handler();

    private Runnable sendEvent = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "sendEvent Waited for 10 seconds");
            hotPlugListener.hotPlugEventReceived(hdmiPlugged);
            handler.removeCallbacks(sendEvent);
        }
    };

    public HDMIHotplugReceiver(Context context, HotplugListener listener) {
        this.context = context;
        this.hotPlugListener = listener;
        hdmiHotplugBroadcastReceiver = new HDMIHotplugBroadcastReceiver();
        registered =false;
    }
    public void register() {
        if (registered) {
            return;
        }
        registered = true;
        IntentFilter hdmiIntentFilter = new IntentFilter();
        hdmiIntentFilter.addAction(HDMI_PLUGGED_EVENT);
        context.registerReceiver(hdmiHotplugBroadcastReceiver, hdmiIntentFilter);
        Log.d(TAG, "Registered");
    }
    public void unregister() {
        if (!registered) {
            return;
        }
        context.unregisterReceiver(hdmiHotplugBroadcastReceiver);
        registered = false;
        Log.d(TAG, "UnRegistered");
    }
    private void onHotplugEventReceived() {
        if (registered) {
            if (hdmiPlugged) {
                Log.d(TAG, "onHotplugEventReceived sending HotPlug plugged in");
                hotPlugListener.hotPlugEventReceived(hdmiPlugged);
            }
            else {
                // During TV on there will be multiple plug/unplug events.
                // It stabilizes within 10 seconds. So, delay sending unplug event to make
                // sure its real unPlug
                handler.postDelayed(sendEvent, 10000);
            }
        }
    }

    private class HDMIHotplugBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(HDMI_PLUGGED_EVENT)) {
                if (intent.getIntExtra(HDMI_PLUGGED_STATE, -1) == 1)
                {
                    hdmiPlugged = true;
                    Log.d(TAG, "onReceive HDMI Hotplug - plugged in");
                } else {
                    hdmiPlugged = false;
                    Log.d(TAG, "onReceive HDMI Hotplug - unplugged");
                }
                onHotplugEventReceived();
            }
        }
    }
}