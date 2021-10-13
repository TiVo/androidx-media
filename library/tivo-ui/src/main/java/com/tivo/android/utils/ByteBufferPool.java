/*
 * Copyright 2019 TiVo Inc. All rights reserved.
 */

package com.tivo.android.utils;
import java.nio.ByteBuffer;
/**
 * Pooled buffers for reading from upstream
 */
public class ByteBufferPool {
  private static final int BUFFER_POOL_SIZE = 3;
  private static ByteBuffer[] buffersArray = new ByteBuffer[BUFFER_POOL_SIZE];;
  private static int buffersArrayCount;

  //pool ByteBuffers
  public static ByteBuffer acquireBuffer(int bufferSize) {
    synchronized (buffersArray) {
      if (buffersArrayCount > 0) {
        // Use the last buffer
        return buffersArray[--buffersArrayCount];
      }
    }
    return ByteBuffer.allocateDirect(bufferSize);
  }

  public static void releaseBuffer(ByteBuffer buffer) {
    synchronized (buffersArray) {
      // If the pool is not at capacity yet, then add this one to the
      // pool
      if (buffersArrayCount < BUFFER_POOL_SIZE) {
        buffersArray[buffersArrayCount++] = buffer;
      }
      // Otherwise, just drop this buffer and let it get garbage
      // collected
    }
  }
}