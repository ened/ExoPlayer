package com.google.android.exoplayer2.ext.live555;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

class Live555Jni {

    private static final String TAG = Live555Jni.class.getSimpleName();

    private long rtspClient;

    /**
     * Whether the underlying live555 library is available.
     */
    static final boolean IS_AVAILABLE;

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

    // Data fields
    private ByteBuffer spsPps;
    private ByteBuffer frame;
    private long framePresentationTimeUs;

    public ByteBuffer getSpsPps() {
        return spsPps;
    }

    public ByteBuffer getFrame() {
        return frame;
    }

    public long getFramePresentationTimeUs() {
        return framePresentationTimeUs;
    }

    public Live555Jni() {
        Live555Jni.live555Init();
    }

    /**
     * Retrieve a URL.
     *
     * @param uri RTSP URL.
     * @throws IOException
     */
    void openUrl(Uri uri) throws IOException {
        rtspClient = Live555Jni.openUrl(uri.toString());

        if (rtspClient == 0) {
            throw new IOException("Opening the URL failed");
        }
    }

    void stop() {
        Live555Jni.stop(rtspClient);
    }

    void allocateBuffers(int spsPpsSize, int frameSize) {
        Log.d(TAG, "allocateBuffers() called with: spsPpsSize = [" + spsPpsSize + "], frameSize = [" + frameSize + "]");
        if (spsPps == null || spsPps.capacity() < spsPpsSize) {
            spsPps = ByteBuffer.allocateDirect(spsPpsSize);
        } else {
            spsPps.position(0);
            spsPps.limit(spsPpsSize);
        }

        if (frame == null || frame.capacity() < frameSize) {
            frame = ByteBuffer.allocateDirect(frameSize);
        } else {
            frame.position(0);
            frame.limit(frameSize);
        }
    }

    boolean fetchFrame() {
        long presentationPrev = framePresentationTimeUs;

        Live555Jni.fetchFrame(rtspClient, this);

        return (presentationPrev != framePresentationTimeUs);
    }

    public static native String getLibLive555Version();

    private static native void live555Init();

    private static native void stop(long rtspClient);

    private static native long openUrl(String url);

    private static native void fetchFrame(long rtspClient, Live555Jni object);

//    private static native byte[] getFrame(long rtspClient);
//
//    private static native byte[] retrieveSPSPPS(long rtspClient);

}
