package com.google.android.exoplayer2.ext.live555;

import android.util.Log;

import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.SeekMap;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.io.IOException;

public class RtspExtractor implements Extractor {

    private final static String TAG = RtspExtractor.class.getSimpleName();
    private final boolean[] prefixFlags;

    private ExtractorOutput output;
//    private H264Reader reader;

    public RtspExtractor() {
        prefixFlags = new boolean[3];
    }

    @Override
    public void init(ExtractorOutput output) {
        this.output = output;

        output.seekMap(new SeekMap.Unseekable(1));
    }

    @Override
    public boolean sniff(ExtractorInput input) throws IOException, InterruptedException {
        byte[] data = new byte[1024];

        input.read(data, 0, data.length);

        NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(data, 0, 100);

        if (spsData != null) {
            Log.d(TAG, "sniff: width, height: " + spsData.width + "," + spsData.height);
            return true;
        }

        return false;
    }


    @Override
    public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException, InterruptedException {

        byte[] data = new byte[4096];

        int size = input.read(data, 0, data.length);

        ParsableByteArray array = new ParsableByteArray(data);

//        if (reader == null) {
//            TrackOutput track = output.track(0);
//            NalUnitUtil.SpsData spsData = NalUnitUtil.parseSpsNalUnit(data, 0, 100);
//
//            if (spsData == null) {
//                return RESULT_END_OF_INPUT;
//            }
//
////            reader = new H264Reader(track, new SeiReader(track), true, true);
//        }

//        reader.consume(array);

        return RESULT_CONTINUE;
    }

    @Override
    public void seek(long position) {

    }

    @Override
    public void release() {

    }
}
