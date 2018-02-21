/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.extractor.flv.FlvExtractor;
import com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.extractor.ogg.OggExtractor;
import com.google.android.exoplayer2.extractor.ts.Ac3Extractor;
import com.google.android.exoplayer2.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsExtractor;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader;
import com.google.android.exoplayer2.extractor.wav.WavExtractor;
import com.google.android.exoplayer2.util.TimestampAdjuster;
import java.lang.reflect.Constructor;

/**
 * An {@link ExtractorsFactory} that provides an array of extractors for the following formats:
 *
 * <ul>
 * <li>MP4, including M4A ({@link Mp4Extractor})</li>
 * <li>fMP4 ({@link FragmentedMp4Extractor})</li>
 * <li>Matroska and WebM ({@link MatroskaExtractor})</li>
 * <li>Ogg Vorbis/FLAC ({@link OggExtractor}</li>
 * <li>MP3 ({@link Mp3Extractor})</li>
 * <li>AAC ({@link AdtsExtractor})</li>
 * <li>MPEG TS ({@link TsExtractor})</li>
 * <li>MPEG PS ({@link PsExtractor})</li>
 * <li>FLV ({@link FlvExtractor})</li>
 * <li>WAV ({@link WavExtractor})</li>
 * <li>AC3 ({@link Ac3Extractor})</li>
 * <li>FLAC (only available if the FLAC extension is built and included)</li>
 * <li>RTSP (only available if the RTSP extension is built and included)</li>
 * </ul>
 */
public final class DefaultExtractorsFactory implements ExtractorsFactory {

  private static final Constructor<? extends Extractor> FLAC_EXTRACTOR_CONSTRUCTOR;
  static {
    Constructor<? extends Extractor> flacExtractorConstructor = null;
    try {
      // LINT.IfChange
      flacExtractorConstructor =
          Class.forName("com.google.android.exoplayer2.ext.flac.FlacExtractor")
              .asSubclass(Extractor.class)
              .getConstructor();
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the FLAC extension.
    } catch (Exception e) {
      // The FLAC extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating FLAC extension", e);
    }
    FLAC_EXTRACTOR_CONSTRUCTOR = flacExtractorConstructor;
  }

  private static final Constructor<? extends Extractor> RTSP_EXTRACTOR_CONSTRUCTOR;
  static {
    Constructor<? extends Extractor> rtspExtractorConstructor = null;
    try {
      // LINT.IfChange
      rtspExtractorConstructor =
          Class.forName("com.google.android.exoplayer2.ext.live555.RtspExtractor")
              .asSubclass(Extractor.class)
              .getConstructor();
      // LINT.ThenChange(../../../../../../../../proguard-rules.txt)
    } catch (ClassNotFoundException e) {
      // Expected if the app was built without the RTSP extension.
    } catch (Exception e) {
      // The RTSP extension is present, but instantiation failed.
      throw new RuntimeException("Error instantiating RTSP extension", e);
    }
    RTSP_EXTRACTOR_CONSTRUCTOR = rtspExtractorConstructor;
  }

  private @MatroskaExtractor.Flags int matroskaFlags;
  private @Mp4Extractor.Flags int mp4Flags;
  private @FragmentedMp4Extractor.Flags int fragmentedMp4Flags;
  private @Mp3Extractor.Flags int mp3Flags;
  private @TsExtractor.Mode int tsMode;
  private @DefaultTsPayloadReaderFactory.Flags int tsFlags;

  public DefaultExtractorsFactory() {
    tsMode = TsExtractor.MODE_SINGLE_PMT;
  }

  /**
   * Sets flags for {@link MatroskaExtractor} instances created by the factory.
   *
   * @see MatroskaExtractor#MatroskaExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setMatroskaExtractorFlags(
      @MatroskaExtractor.Flags int flags) {
    this.matroskaFlags = flags;
    return this;
  }

  /**
   * Sets flags for {@link Mp4Extractor} instances created by the factory.
   *
   * @see Mp4Extractor#Mp4Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setMp4ExtractorFlags(@Mp4Extractor.Flags int flags) {
    this.mp4Flags = flags;
    return this;
  }

  /**
   * Sets flags for {@link FragmentedMp4Extractor} instances created by the factory.
   *
   * @see FragmentedMp4Extractor#FragmentedMp4Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setFragmentedMp4ExtractorFlags(
      @FragmentedMp4Extractor.Flags int flags) {
    this.fragmentedMp4Flags = flags;
    return this;
  }

  /**
   * Sets flags for {@link Mp3Extractor} instances created by the factory.
   *
   * @see Mp3Extractor#Mp3Extractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setMp3ExtractorFlags(@Mp3Extractor.Flags int flags) {
    mp3Flags = flags;
    return this;
  }

  /**
   * Sets the mode for {@link TsExtractor} instances created by the factory.
   *
   * @see TsExtractor#TsExtractor(int, TimestampAdjuster, TsPayloadReader.Factory)
   * @param mode The mode to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setTsExtractorMode(@TsExtractor.Mode int mode) {
    tsMode = mode;
    return this;
  }

  /**
   * Sets flags for {@link DefaultTsPayloadReaderFactory}s used by {@link TsExtractor} instances
   * created by the factory.
   *
   * @see TsExtractor#TsExtractor(int)
   * @param flags The flags to use.
   * @return The factory, for convenience.
   */
  public synchronized DefaultExtractorsFactory setTsExtractorFlags(
      @DefaultTsPayloadReaderFactory.Flags int flags) {
    tsFlags = flags;
    return this;
  }

  @Override
  public synchronized Extractor[] createExtractors() {
    int numExtractors = 11;
    Extractor[] extractors = new Extractor[
            numExtractors +
            (FLAC_EXTRACTOR_CONSTRUCTOR == null ? 1 : 0) +
            (RTSP_EXTRACTOR_CONSTRUCTOR == null ? 1 : 0)
    ];
    extractors[0] = new MatroskaExtractor(matroskaFlags);
    extractors[1] = new FragmentedMp4Extractor(fragmentedMp4Flags);
    extractors[2] = new Mp4Extractor(mp4Flags);
    extractors[3] = new Mp3Extractor(mp3Flags);
    extractors[4] = new AdtsExtractor();
    extractors[5] = new Ac3Extractor();
    extractors[6] = new TsExtractor(tsMode, tsFlags);
    extractors[7] = new FlvExtractor();
    extractors[8] = new OggExtractor();
    extractors[9] = new PsExtractor();
    extractors[10] = new WavExtractor();
    if (FLAC_EXTRACTOR_CONSTRUCTOR != null) {
      try {
        extractors[11] = FLAC_EXTRACTOR_CONSTRUCTOR.newInstance();
      } catch (Exception e) {
        // Should never happen.
        throw new IllegalStateException("Unexpected error creating FLAC extractor", e);
      }
    }
    if (RTSP_EXTRACTOR_CONSTRUCTOR != null) {
      try {
        extractors[(FLAC_EXTRACTOR_CONSTRUCTOR == null ? 11 : 12)] = RTSP_EXTRACTOR_CONSTRUCTOR.newInstance();
      } catch (Exception e) {
        // Should never happen.
        throw new IllegalStateException("Unexpected error creating RTSP extractor", e);
      }
    }
    return extractors;
  }

}
