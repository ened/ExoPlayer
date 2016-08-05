package com.google.android.exoplayer2.ext.live555;

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Sebastian Roth <sebastianroth@n2.com.hk>
 */
public class RtspDataSource implements DataSource {

    public static final byte[] FAKE_HEADER = new byte[]{32, 32, 55, 11};

    private static final String TAG = RtspDataSource.class.getSimpleName();

    private Live555Jni live555Jni = new Live555Jni();

    /**
     * @throws IllegalAccessException if the native library was not found. {@link DefaultDataSource} will use this information.
     */
    public RtspDataSource() throws IllegalAccessException {
        if (Live555Jni.IS_AVAILABLE) {
            Log.d(TAG, "RTSP Data source loaded, live555 version: " + Live555Jni.getLibLive555Version());
        } else {
            throw new IllegalAccessException("Live555 was not found.");
        }
    }

    @Override
    public Uri getUri() {
        return null;
    }

    @Override
    public long open(final DataSpec dataSpec) throws IOException {

        Log.d(TAG, "open() called with: dataSpec = [" + dataSpec + "]");

        live555Jni.openUrl(dataSpec.uri);

        return C.LENGTH_UNBOUNDED;
    }

    @Override
    public void close() throws IOException {
        Log.d(TAG, "close() called");
    }

    private ParsableByteArray frameWithSps;

    @Override
    public int read(final byte[] buffer, final int offset, final int readLength) throws IOException {

        // Log.d(TAG, "read() called with: buffer = [" + buffer + "], offset = [" + offset + "], readLength = [" + readLength + "]");

        if (frameWithSps == null || frameWithSps.bytesLeft() == 0) {
            while (!live555Jni.fetchFrame()) {
                try {
                    Thread.sleep(100, 0);
                } catch (InterruptedException e) {
                    return C.RESULT_END_OF_INPUT;
                }
                // Try again for another frame.
            }

            ByteBuffer b1 = live555Jni.getSpsPps();
            ByteBuffer b2 = live555Jni.getFrame();

            byte[] fullFrame =new byte[b1.array().length + b2.array().length + 4];

            System.arraycopy(FAKE_HEADER, 0, fullFrame, 0, FAKE_HEADER.length);
            System.arraycopy(b1.array(), 0, fullFrame, FAKE_HEADER.length, b1.array().length);
            System.arraycopy(b2.array(), 0, fullFrame, b1.array().length + FAKE_HEADER.length, b2.array().length);

            frameWithSps = new ParsableByteArray(fullFrame);
        }

        int frameLength = Math.min(frameWithSps.bytesLeft(), readLength);
        frameWithSps.readBytes(buffer, offset, frameLength);

        return frameLength;
    }
}
