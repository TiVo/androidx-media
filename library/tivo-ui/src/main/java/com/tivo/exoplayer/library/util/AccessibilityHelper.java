// Copyright 2010 TiVo Inc.  All rights reserved.

package com.tivo.exoplayer.library.util;

import android.content.Context;
import android.os.Build;
import android.view.accessibility.CaptioningManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.util.Locale;

/**
 * Interfaces with Android settings to monitor accessibility settings (Captions)
 */
public class AccessibilityHelper {

  private final Context context;

  public interface AccessibilityStateChangeListener {

    /**
     * Notified with the initial caption settings and for every change.
     *
     * @param enabled captions are enabled
     * @param captionLanguage selected Locale, or system default locale if default
     */
    default void captionStateChanged(boolean enabled, Locale captionLanguage) {}

    /**
     * Notified with the initial caption style setting and for every change.
     *
     * @param style - user selected CaptionStyle
     * @param fontScale - font size
     */
    default void captionStyleChanged(CaptioningManager.CaptionStyle style, float fontScale) {}
  }

  private @Nullable CaptioningManager.CaptioningChangeListener captionChangeListener;

  /**
   * Construct this helper.  The caller is notified at the listener immediately of the
   * accessiblity state and everytime thereafter that it changes.
   *
   * @param context - android context
   * @param listener - notified of current state and changes
   */
  public AccessibilityHelper(Context context, AccessibilityStateChangeListener listener) {
    this.context = context;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      captionChangeListener = new CaptioningManager.CaptioningChangeListener() {

        @Override
        public void onEnabledChanged(boolean enabled) {
          listener.captionStateChanged(enabled, getCaptionLocale());
        }

        @Override
        public void onLocaleChanged(@Nullable Locale locale) {
          CaptioningManager captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
          listener.captionStateChanged(captioningManager.isEnabled(), getCaptionLocale());
        }

        @Override
        public void onUserStyleChanged(@NonNull CaptioningManager.CaptionStyle userStyle) {
          CaptioningManager captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
          listener.captionStyleChanged(userStyle, captioningManager.getFontScale());
        }

        @Override
        public void onFontScaleChanged(float fontScale) {
          CaptioningManager captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
          listener.captionStyleChanged(captioningManager.getUserStyle(), fontScale);
        }
      };
      CaptioningManager captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
      captioningManager.addCaptioningChangeListener(captionChangeListener);

      listener.captionStyleChanged(captioningManager.getUserStyle(), captioningManager.getFontScale());
      listener.captionStateChanged(captioningManager.isEnabled(), getCaptionLocale());
    }
  }

  /**
   * Call for destroy lifecycle
   */
  public void onDestroy() {
    if (captionChangeListener != null) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
        CaptioningManager captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
        captioningManager.removeCaptioningChangeListener(captionChangeListener);
      }
    }
  }


  /**
   * Get the Locale (language settings) preferred for closed captioning
   *
   * @return Locale for close caption language, this is selected via Accessability or Locale.getDefault() if none
   */
  @RequiresApi(19)
  private Locale getCaptionLocale() {
    Locale captionLocale = Locale.getDefault();
    CaptioningManager captioningManager = (CaptioningManager) context.getSystemService(Context.CAPTIONING_SERVICE);
    Locale selectedLocale = captioningManager.getLocale();
    if (selectedLocale != null) {
      captionLocale = selectedLocale;
    }
    return captionLocale;
  }

}
