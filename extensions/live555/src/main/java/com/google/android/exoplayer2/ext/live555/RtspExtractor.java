package com.google.android.exoplayer2.ext.live555;

import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;
import java.util.Arrays;

public class RtspExtractor implements Extractor {

    private final static String TAG = RtspExtractor.class.getSimpleName();
    public static final int SPS_LENGTH = 21;

    private final boolean[] prefixFlags;

    private ExtractorOutput extractorOutput;
    private TrackOutput trackOutput;
//    private H264Reader reader;

    public RtspExtractor() {
        prefixFlags = new boolean[3];
    }

    @Override
    public void init(ExtractorOutput output) {
        Log.d(TAG, "init() called with: extractorOutput = [" + output + "]");

        this.extractorOutput = output;
        trackOutput = extractorOutput.track(0, C.TRACK_TYPE_VIDEO);
        extractorOutput.endTracks();


        output.seekMap(new SeekMap.Unseekable(C.TIME_UNSET));
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        Log.d(TAG, "sniff() called with: input = [" + input + "]");

        byte[] header = new byte[RtspDataSource.FAKE_HEADER.length];
        input.peekFully(header, 0, RtspDataSource.FAKE_HEADER.length);
        if (Arrays.equals(header, RtspDataSource.FAKE_HEADER)) {
            byte[] data = new byte[SPS_LENGTH];
            input.peekFully(data, 0, data.length);

            NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(data, 0, data.length);

            if (spsData != null) {
                Log.d(TAG, "sniff: width, height: " + spsData.width + "," + spsData.height);
                return true;
            }
        }

        return false;
    }

    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {
        byte[] data = new byte[4096];

        input.resetPeekPosition();

        byte[] header = new byte[RtspDataSource.FAKE_HEADER.length];
        input.peekFully(header, 0, RtspDataSource.FAKE_HEADER.length);
        if (Arrays.equals(header, RtspDataSource.FAKE_HEADER)) {

            input.skip(4);
            input.read(data, 0, SPS_LENGTH);

            try {
                NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(data, 0, SPS_LENGTH);

                trackOutput.format(Format.createVideoSampleFormat(null, MimeTypes.VIDEO_H264, null,
                        Format.NO_VALUE, Format.NO_VALUE, spsData.width, spsData.height, Format.NO_VALUE,
                        null, Format.NO_VALUE, spsData.pixelWidthAspectRatio, null));
            } catch (IllegalStateException e) {
                Log.e(TAG, "read: ", e);
                return RESULT_CONTINUE;
            }
        }


        int sizeRead = input.read(data, 0, data.length);

        ParsableByteArray parsableByteArray = new ParsableByteArray(data);

        trackOutput.sampleData(parsableByteArray, sizeRead);
        trackOutput.sampleMetadata(0, 0, sizeRead, 0, null);

        return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position, long timeUs) {
        Log.d(TAG, "seek() called with: position = [" + position + "]");
    }

    @Override
    public void release() {

    }
}
