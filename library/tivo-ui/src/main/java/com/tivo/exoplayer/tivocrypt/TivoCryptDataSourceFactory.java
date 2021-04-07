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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;


public class TivoCryptDataSourceFactory implements DataSource.Factory {

    public static final String TAG = "TivoCryptDataSourceFactory";
    // Size of buffer to read in and decrypt.  Must be a multiple of 16 bytes,
    // should be a multiple of 188 bytes, so use multiples of 752 (which is
    // the least common multiple of 16 and 188), PLUS a prefix of 16 bytes for
    // the decryption IV.
    private static final int BUFFER_SIZE = (88 * 752) + 16; // just over 64KB

    // This is the DataSource.Factory which will produce DataSources that we
    // use for upstream data loading
    private DataSource.Factory mDelegateFactory;

    // delegateFactory is the DataSource.Factory which will create the
    // upstream data sources.
    public TivoCryptDataSourceFactory(DataSource.Factory delegateFactory, String wbKey,
                                      Context context, String deviceKey) {
        mDelegateFactory = delegateFactory;

        // Load the native library so that we can call the native methods
        if (wbKey != null) {
            if (TivoCryptSsUtil.loadLibrary(TivoCryptSsUtil.SS_DRM_LIB_NAME)) {
                int resultCode = TivoCryptSsUtil.initLibrary(context, deviceKey);
                if (TivoCryptSsUtil.isRootDeviceDetectionFatal(resultCode)) {
                    LogError("streaming rooted device");//TODO error handling later
                } else if (TivoCryptSsUtil.isRootDeviceDetectionIgnored(resultCode)) {
                    LogError("RootDeviceDetectionIgnored");//TODO  handling later
                } else {
                    LogMsg("Clear to stream");
                }
                TivoCryptSsUtil.setKBWData(wbKey);
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

        // Byte array used for zeroing ...
        private byte[] gZeroes = new byte[BUFFER_SIZE];

        // Buffers are pooled.
        private static final int BUFFER_POOL_SIZE = 3;
        private ByteBuffer[] gBuffers = new ByteBuffer[BUFFER_POOL_SIZE];
        private int gBuffersCount;


        // mSkip is the number of bytes to skip before beginning actually
        // reading data
        private int mSkip;

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
            // Don't allow bogus length
            if ((dataSpec.length != C.LENGTH_UNSET) &&
                    ((dataSpec.length < 0) ||
                            (dataSpec.length > Integer.MAX_VALUE))) {
                throwIOException("Invalid length: " + dataSpec.length);
            }

            // Don't allow bogus position
            if ((dataSpec.position < 0) ||
                    (dataSpec.position > Integer.MAX_VALUE)) {
                throwIOException("Invalid position: " + dataSpec.position);
            }

            // Start with a fresh buffer
            mBuffer = acquireBuffer();
            mBuffer.clear();

            // Start with a fresh IV too
            mIv.limit(mIv.capacity());
            mIv.position(0);

            // These values come from dataSpec and may need to be updated
            int position = (int) dataSpec.position;
            long length = dataSpec.length;

            if (position > 0) {
                // If the start position is beyond the first AES block, then
                // load starting at the previous AES block (as the IV)
                if (position > 15) {
                    int load_position = (position - (position % 16)) - 16;
                    mSkip = position - load_position;
                    position = load_position;
                    // The IV will be loaded into the first 16 bytes of
                    // mBuffer on first read
                    mReadIv = true;
                }
                // Else there is an offset, but it is within the first AES
                // block, so just start the read/decyption as normal and skip
                // the bytes
                else {
                    mSkip = position;
                    position = 0;
                    mBuffer.put(mIv);
                    mReadIv = false;
                }
                if (dataSpec.length != C.LENGTH_UNSET) {
                    length += mSkip;
                }
            } else {
                // Ensure that mSkip is 0 and mReadIv is false, in case this
                // DataSource is being re-used
                mSkip = 0;
                mBuffer.put(mIv);
                mReadIv = false;
            }

            // Create a new DataSpec if necessary
            if ((position != dataSpec.position) ||
                    (length != dataSpec.length)) {
                dataSpec = new DataSpec(dataSpec.uri, dataSpec.httpMethod,
                        dataSpec.httpBody, 0, position,
                        length, dataSpec.key, dataSpec.flags);
            }

            // At this point, dataSpec has position 0 and enough length to
            // cover the originally requested range

            // mBufferLength is how much data we will actually read from the
            // upstream data source.  It starts out as what the upstream data
            // source believes it has available.
            // Going to assume that the length will fit within an int ...
            // it would be kind of nonsensible to read more than 2 GB
            long available = mUpstream.open(dataSpec);

            // If the upstream source doesn't know how much it has available,
            // then we'll say that we can provide as much as was requested
            // (but may find later that we couldn't provide it all and will
            // return C.RESULT_END_OF_INPUT before this amount of data was
            // returned).  Remember that dataSpec.length now contains the
            // entire requested amount.
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
            // Don't let bogus mBufferLength happen
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

            // Haven't reached EOF on upstream yet
            mUpstreamEof = false;

            // mBuffer must now look like a buffer that has been completely
            // read to begin the read cycle
            mBuffer.position(mBuffer.limit());

            // What we'll deliver back to the caller of read() is the amount
            // that we request minus mSkip, since we read everything from 0 to
            // mSkip also but don't deliver it back via read().
            if (mBufferLength < 0) {
                return C.LENGTH_UNSET;
            } else {
                return mBufferLength - mSkip;
            }
        }

        public int read(byte[] buffer, int offset, int readLength)
                throws IOException {
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
                    // If supposed to read the IV as the first 16 bytes, then
                    // just load the whole buffer
                    if (mReadIv) {
                        mBuffer.position(0);
                        // Don't overwrite the IV from here on out
                        mReadIv = false;
                    }
                    // Else, the IV is already in the first 16 bytes, so don't
                    // read over top of it
                    else {
                        mBuffer.position(16);
                    }
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
                    mBuffer.position(16);
                    // Get the remaining data
                    toget = mBuffer.remaining();
                    // Now if supposed to skip data, do so
                    if (mSkip > 0) {
                        // Re-use toget as the amount to skip
                        if (toget > mSkip) {
                            toget = mSkip;
                        }
                        mBuffer.position(toget + 16);
                        continue;
                    }
                }
                // Now take as much as possible out of what remains
                if (toget > readLength) {
                    toget = readLength;
                }
                mBuffer.get(buffer, offset, toget);
                // Must clear out the just-read decrypted data according to
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
                releaseBuffer(mBuffer);
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


        // Zeroes out bytes from [start] to [start + length - 1] (inclusive).
        // Can only handle length <= BUFFER_SIZE.
        // Does not alter the buffer position.
        private void zeroBuffer(ByteBuffer buffer, int start, long length) {
            int saved_position = buffer.position();

            buffer.position(start);

            int to_put = gZeroes.length;
            if (to_put > length) {
                to_put = (int) length;
            }
            buffer.put(gZeroes, 0, to_put);
            buffer.position(saved_position);
        }

        private ByteBuffer acquireBuffer() {
            synchronized (gBuffers) {
                if (gBuffersCount > 0) {
                    // Use the last buffer
                    return gBuffers[--gBuffersCount];
                }
            }

            return ByteBuffer.allocateDirect(BUFFER_SIZE);
        }

        // The TivoCryptDataSource instances pool ByteBuffers
        private void releaseBuffer(ByteBuffer buffer) {
            synchronized (gBuffers) {
                // If the pool is not at capacity yet, then add this one to the
                // pool
                if (gBuffersCount < BUFFER_POOL_SIZE) {
                    gBuffers[gBuffersCount++] = buffer;
                }
                // Otherwise, just drop this buffer and let it get garbage
                // collected
            }
        }
    }

    private static void throwIOException(String msg) throws IOException {
        LogError(msg);
        throw new IOException(msg);
    }

}
