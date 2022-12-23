package com.tivo.android.utils;

import com.google.android.exoplayer2.util.Log;

/**
 * TiVo specific versions of methods in {@link com.google.android.exoplayer2.util.Util} or other
 * simple general purpose static methods.
 */
public class SystemUtils {
  public static final String TAG = "SystemUtils";

  /**
   * Gets the Vendor Hardware Serial Number, this is specific to TiVo Managed Android devices
   *
   * @return HSNT or null if it does not exist.
   */
  public static String getHSNT() {
    try {
      String s = (String)
              (((Class.forName("android.os.SystemProperties"))
                      .getDeclaredMethod
                              ("get", new Class[] { String.class, String.class }))
                      .invoke
                              (null, new Object[] { "ro.product.hsnt", "" }));
      if ((s != null) && (s.length() > 0)) {
        return s;
      }
    }
    catch (Exception e) {
      Log.e(TAG, "Failed to get hardware serial number from ro.product.hsnt: " + e);
    }

    // HSNT could be in ro.vendor.hsnt. Look for that as well.
    try {
      String s = (String)
              (((Class.forName("android.os.SystemProperties"))
                      .getDeclaredMethod
                              ("get", new Class[] { String.class, String.class }))
                      .invoke
                              (null, new Object[] { "ro.vendor.hsnt", "" }));
      if ((s != null) && (s.length() > 0)) {
        return s;
      }
    }
    catch (Exception e) {
      Log.e(TAG, "Failed to get hardware serial number from ro.vendor.hsnt: " + e);
    }

    return null;
  }
}
