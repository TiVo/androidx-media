package com.tivo.exoplayer.library.multiview;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import com.tivo.exoplayer.library.util.AccessibilityHelper;
import java.util.Locale;

public class MultiPlayerAccessibilityHelper {
    private static final String TAG = "AccessibilityHelperListener";
    private @Nullable AccessibilityHelper accessibilityHelper;
    private boolean closeCaptionEnabled = true;
    private String closeCaptionLanguage = "und";

    public MultiPlayerAccessibilityHelper(Context context) {
        accessibilityHelper = new AccessibilityHelper(context, new AccessibilityHelper.AccessibilityStateChangeListener() {
            @Override
            public void captionStateChanged(boolean enabled, Locale captionLanguage) {
                closeCaptionLanguage = captionLanguage.getLanguage();
                closeCaptionEnabled = enabled;
                if (closeCaptionLanguage == null || closeCaptionLanguage.isEmpty()) {
                    closeCaptionLanguage = "und";
                }
                Log.d(TAG,"closeCaptionEnabled " + closeCaptionEnabled + " closeCaptionEnabled " + closeCaptionLanguage);
            }
        });
    }

    public boolean isCloseCaptionEnabled() {
        return closeCaptionEnabled;
    }

    public String getCloseCaptionLanguage() {
        return closeCaptionLanguage;
    }
}
