/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.exoplayer2.source;

import androidx.annotation.Nullable;
import android.util.Pair;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSourceEventListener.EventDispatcher;
import com.google.android.exoplayer2.upstream.Allocator;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import java.io.IOException;

/**
 * A {@link MediaSource} that masks the {@link Timeline} with a placeholder until the actual media
 * structure is known.
 */
public final class MaskingMediaSource extends CompositeMediaSource<Void> {

  private final MediaSource mediaSource;
  private final boolean useLazyPreparation;
  private final Timeline.Window window;
  private final Timeline.Period period;

  private MaskingTimeline timeline;
  @Nullable private MaskingMediaPeriod unpreparedMaskingMediaPeriod;
  @Nullable private EventDispatcher unpreparedMaskingMediaPeriodEventDispatcher;
  private boolean hasStartedPreparing;
  private boolean isPrepared;

  /**
   * Creates the masking media source.
   *
   * @param mediaSource A {@link MediaSource}.
   * @param useLazyPreparation Whether the {@code mediaSource} is prepared lazily. If false, all
   *     manifest loads and other initial preparation steps happen immediately. If true, these
   *     initial preparations are triggered only when the player starts buffering the media.
   */
  public MaskingMediaSource(MediaSource mediaSource, boolean useLazyPreparation) {
    this.mediaSource = mediaSource;
    this.useLazyPreparation = useLazyPreparation;
    window = new Timeline.Window();
    period = new Timeline.Period();
    timeline = MaskingTimeline.createWithDummyTimeline(mediaSource.getTag());
  }

  /** Returns the {@link Timeline}. */
  public Timeline getTimeline() {
    return timeline;
  }

  @Override
  public void prepareSourceInternal(@Nullable TransferListener mediaTransferListener) {
    super.prepareSourceInternal(mediaTransferListener);
    if (!useLazyPreparation) {
      hasStartedPreparing = true;
      prepareChildSource(/* id= */ null, mediaSource);
    }
  }

  @Nullable
  @Override
  public Object getTag() {
    return mediaSource.getTag();
  }

  @Override
  @SuppressWarnings("MissingSuperCall")
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    // Do nothing. Source info refresh errors will be thrown when calling
    // MaskingMediaPeriod.maybeThrowPrepareError.
  }

  @Override
  public MaskingMediaPeriod createPeriod(
      MediaPeriodId id, Allocator allocator, long startPositionUs) {
    MaskingMediaPeriod mediaPeriod =
        new MaskingMediaPeriod(mediaSource, id, allocator, startPositionUs);
    if (isPrepared) {
      MediaPeriodId idInSource = id.copyWithPeriodUid(getInternalPeriodUid(id.periodUid));
      mediaPeriod.createPeriod(idInSource);
    } else {
      // We should have at most one media period while source is unprepared because the duration is
      // unset and we don't load beyond periods with unset duration. We need to figure out how to
      // handle the prepare positions of multiple deferred media periods, should that ever change.
      unpreparedMaskingMediaPeriod = mediaPeriod;
      unpreparedMaskingMediaPeriodEventDispatcher =
          createEventDispatcher(/* windowIndex= */ 0, id, /* mediaTimeOffsetMs= */ 0);
      unpreparedMaskingMediaPeriodEventDispatcher.mediaPeriodCreated();
      if (!hasStartedPreparing) {
        hasStartedPreparing = true;
        prepareChildSource(/* id= */ null, mediaSource);
      }
    }
    return mediaPeriod;
  }

  @Override
  public void releasePeriod(MediaPeriod mediaPeriod) {
    ((MaskingMediaPeriod) mediaPeriod).releasePeriod();
    if (mediaPeriod == unpreparedMaskingMediaPeriod) {
      Assertions.checkNotNull(unpreparedMaskingMediaPeriodEventDispatcher).mediaPeriodReleased();
      unpreparedMaskingMediaPeriodEventDispatcher = null;
      unpreparedMaskingMediaPeriod = null;
    }
  }

  @Override
  public void releaseSourceInternal() {
    isPrepared = false;
    hasStartedPreparing = false;
    super.releaseSourceInternal();
  }

  @Override
  protected void onChildSourceInfoRefreshed(
      Void id, MediaSource mediaSource, Timeline newTimeline) {
    if (isPrepared) {
      timeline = timeline.cloneWithUpdatedTimeline(newTimeline);
    } else if (newTimeline.isEmpty()) {
      timeline =
          MaskingTimeline.createWithRealTimeline(newTimeline, MaskingTimeline.DUMMY_EXTERNAL_ID);
    } else {
      // Determine first period and the start position.
      // This will be:
      //  1. The default window start position if no deferred period has been created yet.
      //  2. The non-zero prepare position of the deferred period under the assumption that this is
      //     a non-zero initial seek position in the window.
      //  3. The default window start position if the deferred period has a prepare position of zero
      //     under the assumption that the prepare position of zero was used because it's the
      //     default position of the DummyTimeline window. Note that this will override an
      //     intentional seek to zero for a window with a non-zero default position. This is
      //     unlikely to be a problem as a non-zero default position usually only occurs for live
      //     playbacks and seeking to zero in a live window would cause BehindLiveWindowExceptions
      //     anyway.
      newTimeline.getWindow(/* windowIndex= */ 0, window);
      long windowStartPositionUs = window.getDefaultPositionUs();
      if (unpreparedMaskingMediaPeriod != null) {
        long periodPreparePositionUs = unpreparedMaskingMediaPeriod.getPreparePositionUs();
        if (periodPreparePositionUs != 0) {
          windowStartPositionUs = periodPreparePositionUs;
        }
      }
      Pair<Object, Long> periodPosition =
          newTimeline.getPeriodPosition(
              window, period, /* windowIndex= */ 0, windowStartPositionUs);
      Object periodUid = periodPosition.first;
      long periodPositionUs = periodPosition.second;
      timeline = MaskingTimeline.createWithRealTimeline(newTimeline, periodUid);
      if (unpreparedMaskingMediaPeriod != null) {
        MaskingMediaPeriod maskingPeriod = unpreparedMaskingMediaPeriod;
        maskingPeriod.overridePreparePositionUs(periodPositionUs);
        MediaPeriodId idInSource =
            maskingPeriod.id.copyWithPeriodUid(getInternalPeriodUid(maskingPeriod.id.periodUid));
        maskingPeriod.createPeriod(idInSource);
      }
    }
    isPrepared = true;
    refreshSourceInfo(this.timeline);
  }

  @Nullable
  @Override
  protected MediaPeriodId getMediaPeriodIdForChildMediaPeriodId(
      Void id, MediaPeriodId mediaPeriodId) {
    return mediaPeriodId.copyWithPeriodUid(getExternalPeriodUid(mediaPeriodId.periodUid));
  }

  @Override
  protected boolean shouldDispatchCreateOrReleaseEvent(MediaPeriodId mediaPeriodId) {
    // Suppress create and release events for the period created while the source was still
    // unprepared, as we send these events from this class.
    return unpreparedMaskingMediaPeriod == null
        || !mediaPeriodId.equals(unpreparedMaskingMediaPeriod.id);
  }

  private Object getInternalPeriodUid(Object externalPeriodUid) {
    return externalPeriodUid.equals(MaskingTimeline.DUMMY_EXTERNAL_ID)
        ? timeline.replacedInternalId
        : externalPeriodUid;
  }

  private Object getExternalPeriodUid(Object internalPeriodUid) {
    return timeline.replacedInternalId.equals(internalPeriodUid)
        ? MaskingTimeline.DUMMY_EXTERNAL_ID
        : internalPeriodUid;
  }

  /**
   * Timeline used as placeholder for an unprepared media source. After preparation, a
   * MaskingTimeline is used to keep the originally assigned dummy period ID.
   */
  private static final class MaskingTimeline extends ForwardingTimeline {

    public static final Object DUMMY_EXTERNAL_ID = new Object();

    private final Object replacedInternalId;

    /**
     * Returns an instance with a dummy timeline using the provided window tag.
     *
     * @param windowTag A window tag.
     */
    public static MaskingTimeline createWithDummyTimeline(@Nullable Object windowTag) {
      return new MaskingTimeline(new DummyTimeline(windowTag), DUMMY_EXTERNAL_ID);
    }

    /**
     * Returns an instance with a real timeline, replacing the provided period ID with the already
     * assigned dummy period ID.
     *
     * @param timeline The real timeline.
     * @param firstPeriodUid The period UID in the timeline which will be replaced by the already
     *     assigned dummy period UID.
     */
    public static MaskingTimeline createWithRealTimeline(Timeline timeline, Object firstPeriodUid) {
      return new MaskingTimeline(timeline, firstPeriodUid);
    }

    private MaskingTimeline(Timeline timeline, Object replacedInternalId) {
      super(timeline);
      this.replacedInternalId = replacedInternalId;
    }

    /**
     * Returns a copy with an updated timeline. This keeps the existing period replacement.
     *
     * @param timeline The new timeline.
     */
    public MaskingTimeline cloneWithUpdatedTimeline(Timeline timeline) {
      return new MaskingTimeline(timeline, replacedInternalId);
    }

    /** Returns the wrapped timeline. */
    public Timeline getTimeline() {
      return timeline;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      timeline.getPeriod(periodIndex, period, setIds);
      if (Util.areEqual(period.uid, replacedInternalId)) {
        period.uid = DUMMY_EXTERNAL_ID;
      }
      return period;
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return timeline.getIndexOfPeriod(DUMMY_EXTERNAL_ID.equals(uid) ? replacedInternalId : uid);
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      Object uid = timeline.getUidOfPeriod(periodIndex);
      return Util.areEqual(uid, replacedInternalId) ? DUMMY_EXTERNAL_ID : uid;
    }
  }

  /** Dummy placeholder timeline with one dynamic window with a period of indeterminate duration. */
  private static final class DummyTimeline extends Timeline {

    @Nullable private final Object tag;

    public DummyTimeline(@Nullable Object tag) {
      this.tag = tag;
    }

    @Override
    public int getWindowCount() {
      return 1;
    }

    @Override
    public Window getWindow(
        int windowIndex, Window window, boolean setTag, long defaultPositionProjectionUs) {
      return window.set(
          tag,
          /* manifest= */ null,
          /* presentationStartTimeMs= */ C.TIME_UNSET,
          /* windowStartTimeMs= */ C.TIME_UNSET,
          /* isSeekable= */ false,
          // Dynamic window to indicate pending timeline updates.
          /* isDynamic= */ true,
          /* defaultPositionUs= */ 0,
          /* durationUs= */ C.TIME_UNSET,
          /* firstPeriodIndex= */ 0,
          /* lastPeriodIndex= */ 0,
          /* positionInFirstPeriodUs= */ 0);
    }

    @Override
    public int getPeriodCount() {
      return 1;
    }

    @Override
    public Period getPeriod(int periodIndex, Period period, boolean setIds) {
      return period.set(
          /* id= */ 0,
          /* uid= */ MaskingTimeline.DUMMY_EXTERNAL_ID,
          /* windowIndex= */ 0,
          /* durationUs = */ C.TIME_UNSET,
          /* positionInWindowUs= */ 0);
    }

    @Override
    public int getIndexOfPeriod(Object uid) {
      return uid == MaskingTimeline.DUMMY_EXTERNAL_ID ? 0 : C.INDEX_UNSET;
    }

    @Override
    public Object getUidOfPeriod(int periodIndex) {
      return MaskingTimeline.DUMMY_EXTERNAL_ID;
    }
  }
}
