package com.google.android.exoplayer2.ext.live555;

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;

/**
 * @author Sebastian Roth <sebastianroth@n2.com.hk>
 */
public class RtspDataSource implements DataSource {

    public static final byte[] FAKE_HEADER = new byte[]{32, 32, 55, 11};
    /**
     * Whether the underlying live555 library is available.
     */
    private static final boolean IS_AVAILABLE;

    private static final String TAG = RtspDataSource.class.getSimpleName();

    static {
        boolean isAvailable;
        try {
            System.loadLibrary("live555JNI");
            isAvailable = true;
        } catch (UnsatisfiedLinkError exception) {
            isAvailable = false;
        }
        IS_AVAILABLE = isAvailable;
    }

    private long rtspClient;

    private static String getLive555Version() {
        return IS_AVAILABLE ? getLibLive555Version() : null;
    }

    /**
     * @throws IllegalAccessException if the native library was not found. {@link DefaultDataSource} will use this information.
     */
    public RtspDataSource() throws IllegalAccessException {
        if (IS_AVAILABLE) {
            Log.d(TAG, "RTSP Data source loaded, live555 version: " + getLive555Version());
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

        rtspClient = openUrl(dataSpec.uri.toString());

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
            byte[] tmpSpsPPS = null;
            while (tmpSpsPPS == null || tmpSpsPPS.length == 0) {
                tmpSpsPPS = retrieveSPSPPS(rtspClient);
            }

            byte[] tmpFrame = null;
            while (tmpFrame == null || tmpFrame.length == 0) {
                tmpFrame = getFrame(rtspClient);
            }

            byte[] fullFrame = new byte[tmpSpsPPS.length + tmpFrame.length + 4];

            System.arraycopy(FAKE_HEADER, 0, fullFrame, 0, FAKE_HEADER.length);
            System.arraycopy(tmpSpsPPS, 0, fullFrame, FAKE_HEADER.length, tmpSpsPPS.length);
            System.arraycopy(tmpFrame, 0, fullFrame, tmpSpsPPS.length + FAKE_HEADER.length, tmpFrame.length);

            frameWithSps = new ParsableByteArray(fullFrame);
        }

        int frameLength = Math.min(frameWithSps.bytesLeft(), readLength);
        frameWithSps.readBytes(buffer, offset, frameLength);

        return frameLength;
    }

    private static native String getLibLive555Version();

    public static native long openUrl(String url);

    public static native byte[] getFrame(long rtspClient);

    public static native byte[] retrieveSPSPPS(long rtspClient);
}
