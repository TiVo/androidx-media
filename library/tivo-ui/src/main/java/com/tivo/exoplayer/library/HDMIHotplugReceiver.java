package com.tivo.exoplayer.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Log;
import java.util.Timer;
import java.util.TimerTask;

public class HDMIHotplugReceiver {

    public interface HotplugListener {
        void hotPlugEventReceived(boolean Plugged);
    }

    public static final String HDMI_PLUGGED_EVENT = "android.media.action.HDMI_AUDIO_PLUG";
    public static final String HDMI_PLUGGED_STATE = "state";

    private static final String TAG = "HDMIHotplugReceiver";

    private final Context context;
    private final HotplugListener hotPlugListener;
    @Nullable
    private final BroadcastReceiver mHDMIPluggedReceiver;
    private boolean registered;
    boolean mHDMIPlugged;

    public HDMIHotplugReceiver(Context context, HotplugListener listener) {
        this.context = context;
        this.hotPlugListener = listener;
        mHDMIPluggedReceiver = new HDMIHotplugBroadcastReceiver();
        registered =false;
    }
    public void register()
    {
        if (registered) {
            return;
        }
        registered = true;
        if(mHDMIPluggedReceiver != null) {
            IntentFilter hdmiIntentFilter = new IntentFilter();
            hdmiIntentFilter.addAction(HDMI_PLUGGED_EVENT);
            context.registerReceiver(mHDMIPluggedReceiver, hdmiIntentFilter);
        }
        Log.d(TAG, "Registered");
    }
    public void unregister() {
        if (!registered) {
            return;
        }
        if(mHDMIPluggedReceiver != null) {
            context.unregisterReceiver(mHDMIPluggedReceiver);
        }
        registered = false;
        Log.d(TAG, "UnRegistered");
    }
    private void onHotplugEventReceived()
    {
        if (registered) {
            if (!mHDMIPlugged) {
                // During TV on there will be multiple plug/unplug events.
                // It stabilizes within 10 seconds. So, delay sending unplug event to make
                // sure its real unPlug
                sendDelayedEvent();
            }
            else {
                Log.d(TAG, "onHotplugEventReceived sending HotPlug plugged in");
                hotPlugListener.hotPlugEventReceived(mHDMIPlugged);
            }
        }
    }
    private void sendDelayedEvent() {
        TimerTask task = new TimerTask() {
            public void run() {
                Log.d(TAG, "sendDelayedEvent Waited for 10 seconds");
                hotPlugListener.hotPlugEventReceived(mHDMIPlugged);
                cancel();
            }
        };
        Timer timer = new Timer("Timer");

        timer.scheduleAtFixedRate(task, 10000L, 10000L);
    }

    public class HDMIHotplugBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(HDMI_PLUGGED_EVENT)) {
                if (intent.getIntExtra(HDMI_PLUGGED_STATE, -1) == 1)
                {
                    mHDMIPlugged = true;
                    Log.d(TAG, "onReceive HDMI Hotplug - plugged in");
                } else {
                    mHDMIPlugged = false;
                    Log.d(TAG, "onReceive HDMI Hotplug - unplugged");
                }
                onHotplugEventReceived();
            }
        }
    }
}