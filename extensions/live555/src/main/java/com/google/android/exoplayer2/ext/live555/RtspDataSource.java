package com.google.android.exoplayer2.ext.live555;

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSource;

import java.io.IOException;

/**
 * @author Sebastian Roth <sebastianroth@n2.com.hk>
 */
public class RtspDataSource implements DataSource {

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

    private byte[] currentFrame;
    private byte[] spspps;

    @Override
    public int read(final byte[] buffer, final int offset, final int readLength) throws IOException {

//        boolean newFrame = false;

        while (spspps == null || spspps.length == 0) {
            spspps = retrieveSPSPPS(rtspClient);
//            newFrame = true;
        }

        while (currentFrame == null || currentFrame.length == 0) {
            currentFrame = getFrame(rtspClient);
        }


        int ppsLength = Math.min(spspps.length, readLength);
        System.arraycopy(spspps, 0, buffer, offset, ppsLength);

        int frameLength = Math.min(currentFrame.length, readLength - ppsLength);
        System.arraycopy(currentFrame, 0, buffer, offset + spspps.length, frameLength);

        // Reset
        currentFrame = null;

        return ppsLength + frameLength;

//        boolean newFrame = false;

//        while (currentFrame == null || currentFrame.length == 0) {
//            currentFrame = getFrame(rtspClient);
//            newFrame = true;
//        }

//        Log.d(TAG, "read(): buffer = [" + buffer.length + "], offset = [" + offset + "], readLength = [" + readLength + "]" + " frame.length: "
//                + currentFrame.length + (newFrame ? "!" : ""));

//        int toRead = Math.min(currentFrame.length, readLength);
//        System.arraycopy(currentFrame, 0, buffer, offset, toRead);
//
//        byte[] smallerData = new byte[currentFrame.length - toRead];
//        System.arraycopy(currentFrame, toRead, smallerData, 0, currentFrame.length - toRead);
//        currentFrame = smallerData;
//
//        return toRead;
    }

    private static native String getLibLive555Version();

    public static native long openUrl(String url);

    public static native byte[] getFrame(long rtspClient);

    public static native byte[] retrieveSPSPPS(long rtspClient);
}
