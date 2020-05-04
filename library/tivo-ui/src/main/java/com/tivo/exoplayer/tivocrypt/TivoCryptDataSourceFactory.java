//
// Copyright 2019 TiVo Inc. All Rights Reserved.
//

package com.tivo.exoplayer.tivocrypt;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.tivo.android.utils.TivoCryptSsUtil;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.HlsDecryptingDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.tivo.android.utils.ByteBufferPool;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;


public class TivoCryptDataSourceFactory implements DataSource.Factory {

  public static final String TAG = "TivoCryptDataSourceFactory ";
  private static final int BUFFER_SIZE = 18800;//this is the number VO used

  private String mWBKey;

  // This is the DataSource.Factory which will produce DataSources that we
  // use for upstream data loading
  private DataSource.Factory mDelegateFactory;

  // Byte array used for zeroing ...
  private static byte[] gZeroes = new byte[BUFFER_SIZE];


  private Context mContext;

  // delegateFactory is the DataSource.Factory which will create the
  // upstream data sources.
  public TivoCryptDataSourceFactory(DataSource.Factory delegateFactory, String wbKey,
      Context context) {
    mDelegateFactory = delegateFactory;
    mContext = context;
    mWBKey = wbKey;

    // Load the native library so that we can call the native methods
    if (wbKey != null) {
      if (TivoCryptSsUtil.loadLibrary(TivoCryptSsUtil.SS_DRM_LIB_NAME)) {
        int resultCode = TivoCryptSsUtil.initLibrary(mContext);
        if (TivoCryptSsUtil.isRootDeviceDetectionFatal(resultCode)) {
          LogError("streaming rooted device");//TODO error handling later
        } else if (TivoCryptSsUtil.isRootDeviceDetectionIgnored(resultCode)) {
          LogError("RootDeviceDetectionIgnored");//TODO  handling later
        } else {
          LogMsg("Clear to stream");
        }
        TivoCryptSsUtil.setKBWData(mWBKey);
      } else {
        LogError("Can not stream");//TODO  handling later
      }
    }
  }

  public DataSource createDataSource() {
    return new TivoHlsDecryptingDataSourceImpl(mDelegateFactory.createDataSource());
  }

  private static void LogError(String msg) {
    Log.e(TAG, msg);
  }

  private static void LogMsg(String msg) {
    Log.i(TAG, msg);
  }

  /**
   * This is the DataSource instance that ExoPlayer will be used for tivo encrypted content;
   * The additional method getDecryptingDataSource
   * will be invoked if the segment is encrypted, giving us a chance to create a new DataSource
   * instance to be used to do the whole segment TiVo decryption.
   **/
  private class TivoHlsDecryptingDataSourceImpl implements HlsDecryptingDataSource {
    private DataSource mUpstream;

    public TivoHlsDecryptingDataSourceImpl(DataSource upstream) {
      mUpstream = upstream;
    }

    public void addTransferListener(TransferListener transferListener) {
      mUpstream.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
      return mUpstream.open(dataSpec);
    }

    public int read(byte[] buffer, int offset, int readLength)
        throws IOException {
      return mUpstream.read(buffer, offset, readLength);
    }

    public @Nullable
    Uri getUri() {
      return mUpstream.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
      return mUpstream.getResponseHeaders();
    }

    public void close() throws IOException {
      mUpstream.close();
    }

    public DataSource getDecryptingDataSource(Uri keyUri, byte[] iv) {
      return new TivoCryptDataSource(mUpstream, keyUri, iv);
    }
  }

  private class TivoCryptDataSource implements DataSource {

    private static final int IV_SIZE = 16;
    // Input parameters -- the upstream data source, key uri, and iv
    private DataSource mUpstream;
    private Uri mKeyUri;
    //      private ByteBuffer mIv;
    private ByteBuffer mIv;

    // This is non-null if open() is supposed to throw an exception because
    // provisioning failed
    private IOException mOpenException;

    // Before we read from the upstream source this is how much we want to
    // read (if less than 0, then read as much as possible).
    private int mBufferLength;

    // If mReadIv is true, then the IV will be read in on the next read,
    // else the IV is already in the buffer
    private boolean mReadIv;

    // This is true only when we've reached EOF on upstream
    private boolean mUpstreamEof;

    // This is the data buffer
    private ByteBuffer mBuffer;

    // Intermediate copy buffer
    private byte[] mIntermediate;

    public TivoCryptDataSource(DataSource upstream, Uri keyUri, byte[] iv) {
      mUpstream = upstream;
      mKeyUri = keyUri;
      mIv = ByteBuffer.allocate(IV_SIZE);
      //As per HLS spec IV attribute indicates that the Media Sequence Number is to be used
      //   as the IV when decrypting a Media Segment, by putting its big-endian
      //   binary representation into a 16-octet (128-bit) buffer and padding
      //   (on the left) with zeros.
      byte[] ivData = new BigInteger(iv).toByteArray();

      if (ivData.length > IV_SIZE) {
        LogError("Initialization vector data is greater than IV_SIZE " +
            "bytes for keyUri " + keyUri + " and iv " + iv +
            ": " + ivData.length);
      }

      for (int i = 0; i < (IV_SIZE - ivData.length); i++) {
        mIv.put((byte) 0);
      }
      mIv.put(ivData);
    }

    public void addTransferListener(TransferListener transferListener) {
      mUpstream.addTransferListener(transferListener);
    }

    public long open(DataSpec dataSpec) throws IOException {
      //Check length is valid
      if ((dataSpec.length != C.LENGTH_UNSET) &&
          ((dataSpec.length < 0) ||
              (dataSpec.length > Integer.MAX_VALUE))) {
        throwIOException("Invalid length: " + dataSpec.length);
      }
      // Check position is valid
      if ((dataSpec.position < 0) ||
          (dataSpec.position > Integer.MAX_VALUE)) {
        throwIOException("Invalid position: " + dataSpec.position);
      }

      // Start with a fresh buffer
      mBuffer = ByteBufferPool.getInstance().acquireBuffer(BUFFER_SIZE);
      mBuffer.clear();

      long available = mUpstream.open(dataSpec);

      if (available == C.LENGTH_UNSET) {
        // If the requested amount is unknown also, then set
        // bufferLength to -1 meaning "read as long as there is
        // upstream data"
        if (dataSpec.length == C.LENGTH_UNSET) {
          mBufferLength = -1;
        } else {
          mBufferLength = (int) dataSpec.length;
        }
      }
      else if (available < 0) {
        throwIOException("Invalid result returned from upstream " +
            "open: " + available);
      }
      // There is a known available length; if it's greater than a known
      // requested length, then load just the requested length
      else if ((dataSpec.length == C.LENGTH_UNSET) ||
          (available < dataSpec.length)) {
        mBufferLength = (int) available;
      } else {
        mBufferLength = (int) dataSpec.length;
      }

      mUpstreamEof = false;
      // mBuffer must now look like a buffer that has been completely
      // read to begin the read cycle
      mBuffer.position(mBuffer.limit());

      if (mBufferLength < 0) {
        return C.LENGTH_UNSET;
      } else {
        return mBufferLength;
      }
    }

    public int read(byte[] buffer, int offset, int readLength)
        throws IOException {
      LogMsg("read called with offset =" + offset + " readLength = " + readLength);
      // ExoPlayer docs say that read() should return 0 if readLength is
      // 0, no matter the state of the DataSource otherwise.
      if (readLength == 0) {
        return 0;
      }
      int ret = 0;

      while ((readLength > 0) && (mBufferLength != 0)) {
        int toget = mBuffer.remaining();
        if (toget == 0) {
          if (mUpstreamEof) {
            break;
          }
          // Download more data
          mBuffer.limit(mBuffer.capacity());
          mBuffer.position(0);
          int toread = mBuffer.remaining();
          if ((mBufferLength > 0) && (toread > mBufferLength)) {
            toread = mBufferLength;
          }
          int amountRead = this.readBuffer(mUpstream, mBuffer, toread);

          // If didn't read all that was desired, then this must be
          // the last chunk
          if (amountRead < toread) {
            mUpstreamEof = true;
            if (amountRead == 0) {
              break;//done
            }
          }
          // Decrypt what was read in
          int result = TivoCryptSsUtil.decryptSegment(
              mKeyUri.toString(),
              mIv,
              mIv.position(),
              mBuffer,
              mBuffer.position(),
              null);
          if (result != 0) {
            LogError("Failed to  decrypt!");
            throw new IOException("Failed to  decrypt");
          }
          // Flip over to read mode, skipping the IV
          mBuffer.limit(mBuffer.position());
          mBuffer.position(0);
          // Get the remaining data
          toget = mBuffer.remaining();
        }

        // Now take as much as possible out of what remains
        if (toget > readLength) {
          toget = readLength;

        }
        mBuffer.get(buffer, offset, toget);
        //clear out the just-read decrypted data
        zeroBuffer(mBuffer, mBuffer.position() - toget, toget);
        ret += toget;
        readLength -= toget;
        offset += toget;
        if (mBufferLength != C.LENGTH_UNSET) {
          mBufferLength -= toget;
        }
      }

      return (ret == 0) ? C.RESULT_END_OF_INPUT : ret;
    }

    public @Nullable
    Uri getUri() {
      return mUpstream.getUri();
    }

    public Map<String, List<String>> getResponseHeaders() {
      return mUpstream.getResponseHeaders();
    }

    public void close() throws IOException {
      if (mUpstream != null) {
        this.closeUpstreamNoException();
      }

      if (mBuffer != null) {
        long remaining = mBuffer.remaining();
        if (remaining > 0) {
          zeroBuffer(mBuffer, mBuffer.position(), remaining);
        }
        ByteBufferPool.getInstance().releaseBuffer(mBuffer);
        mBuffer = null;
      }
    }

    // Reads up to count into buffer from upstream, returning the
    // number of bytes actually read
    private int readBuffer(DataSource upstream, ByteBuffer buffer,
        int count) throws IOException {
      // Try to read directly into the buffer's array.  This avoids
      // buffer copies.
      boolean native_bytes = false;
      byte[] bytes = null;
      int offset = 0;
      if (buffer.hasArray()) {
        try {
          bytes = buffer.array();
          offset = buffer.arrayOffset() + buffer.position();
          native_bytes = true;
        } catch (Exception e) {
        }
      }

      if (!native_bytes) {
        // Could not get the ByteBuffer's array to write directly
        // into, so read through an intermediate buffer
        if (mIntermediate == null) {
          mIntermediate = new byte[BUFFER_SIZE];
        }
        bytes = mIntermediate;
        offset = 0;
      }

      int pos = 0;

      while (pos < count) {
        int amountRead = upstream.read(bytes, pos + offset, count - pos);
        if (amountRead == C.RESULT_END_OF_INPUT) {
          // No more available
          break;
        }
        // The API for DataSource says that it will never return 0
        // since we didn't pass 0 in.
        else if (amountRead == 0) {
          throwIOException("Invalid read");
        }
        pos += amountRead;
      }

      if (native_bytes) {
        buffer.position(pos + buffer.position());
      } else {
        buffer.put(bytes, 0, pos);
      }

      return pos;
    }

    // IOException on close is a stupid concept.  What is the caller
    // supposed to do with an exception in this circumstance?
    private void closeUpstreamNoException() {
      try {
        mUpstream.close();
      } catch (IOException e) {
        // Swallow it
        LogError("IOException in upstream close: " + e);
      }
    }
  }

  // Zeroes out bytes from [start] to [start + length - 1] (inclusive).
  // Can only handle length <= BUFFER_SIZE.
  // Does not alter the buffer position.
  private static void zeroBuffer(ByteBuffer buffer, int start, long length) {
    int saved_position = buffer.position();

    buffer.position(start);

    int to_put = gZeroes.length;
    if (to_put > length) {
      to_put = (int) length;
    }
    buffer.put(gZeroes, 0, to_put);
    buffer.position(saved_position);
  }

  private static void throwIOException(String msg) throws IOException {
    LogError(msg);
    throw new IOException(msg);
  }

}
