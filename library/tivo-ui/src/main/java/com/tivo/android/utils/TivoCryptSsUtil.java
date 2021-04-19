/*
 * Copyright 2019 TiVo Inc. All rights reserved.
 */

package com.tivo.android.utils;
import android.content.Context;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class TivoCryptSsUtil {
  private static final String TAG = "TivoCrypt";
  public static final String SS_DRM_LIB_NAME = "tivoCrypt";

  public static final int A_FAILED = 100;
  public static final int B_FAILED = 200;
  public static final int C_FAILED = 300;
  public static final int D_FAILED = 400;
  public static final int E_FAILED = 500;
  public static final int F_FAILED = 600;
  public static final int G_FAILED = 700;
  public static final int LIBRARY_LOAD_ERROR = 800;

  public static final int SSDRM_AES_KEY_LENGTH_BYTES = 16; // bytes

  /**
   * sets the white-box key into the DRM module.
   * returns zero on success otherwise an error code.
   */
  public static native int setKBWData(String wbKey);
  /**
   * sets the DeviceKey into the DRM module.
   * returns zero on success otherwise an error code.
   */
  public static native int setDevData(String deviceKey);

  /**
   * Detect root environment for android devices
   * @return if Root detected then returns 1 otherwise 0
   */
  public static native int dread();

  /**
   * Initialization of native layer
   * @return 0 if success else 1
   */
  private static native int init(Context context, byte[] deviceAesKey);

  /**
   * encrypts the data.
   */
  public static native String encrypt(Context context, String plainText);

  /**
   *
   * get the hash of the plainText data.
   */
  public static native String getHash(Context context, String plainText);

  /**
   * decrypts the encrypted data. Must pass the hash of the plainText data.
   * After decrypting the data, it will calculate the hash against and then compare it against the supplied hash
   * If the two hash don't match then it will return an empty string.
   */
  public static native String decrypt(Context context, String encryptedData, String hashOfPlainText);

  /**
   * decrypts the encrypted video content. Returns 0 is content was successfully decrypted
   * The inBuf is updated with decrypted data.
   */
  public static native int decryptSegment(String keyLine, ByteBuffer inBuf, int datalength, String key);

  /**
   * sets the new segments IV
   * @param iv
   */
  public static native void setIV(ByteBuffer iv);

  /**
   * Cleaning of native layer
   * @return
   */
  public static native int close();


  public static boolean loadLibrary(String name) {
    try {
      LogMessage( "loadLibrary "+name);
      System.loadLibrary(name);
    } catch (UnsatisfiedLinkError e) {
      LogMessage( "loadLibrary failed with "+e.toString());

      return false;
    }
    return true;
  }

  public static int initLibrary(Context context, String deviceKey) {
    byte[] key = getDeviceKeyBytes(deviceKey);
    printKey(key);
    return init(context, key);
  }

  private static void printKey(byte[] key) {
    StringBuilder builder = new StringBuilder();
    for (byte b : key) {
      int unsignedInt = (b & 0xFF);
      builder.append(unsignedInt).append(" ");
    }
    Log.d(TAG, "Key being passed to native layer is: " + builder.toString());
  }


  private static byte[] getDeviceKeyBytes(String deviceKey) {
    MessageDigest md = null;

    try {
      md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      Log.e(TAG, "Algorigthm to create digest not found.", e);
    }

    if (md != null) {
      byte[] key = new byte[SSDRM_AES_KEY_LENGTH_BYTES];

      byte[] digest = new byte[0];
      try {
        digest = md.digest(deviceKey.getBytes("UTF-8"));
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }

      if (digest.length < SSDRM_AES_KEY_LENGTH_BYTES) {
        return new byte[0];
      }

      System.arraycopy(digest, 0, key, 0, SSDRM_AES_KEY_LENGTH_BYTES);

      return key;
    } else {
      return new byte[0];
    }
  }

  public static boolean isRootDeviceDetectionFatal(int errorCode) {
    return errorCode == TivoCryptSsUtil.A_FAILED || errorCode == TivoCryptSsUtil.D_FAILED ||
            errorCode == TivoCryptSsUtil.E_FAILED || errorCode == TivoCryptSsUtil.G_FAILED;
  }

  public static boolean isRootDeviceDetectionIgnored(int errorCode) {
    return errorCode == TivoCryptSsUtil.B_FAILED || errorCode == TivoCryptSsUtil.C_FAILED || errorCode == TivoCryptSsUtil.F_FAILED;
  }

  private static void LogMessage(String msg)
  {
    Log.i(TAG, msg);
  }

}
