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
package com.google.android.exoplayer2.upstream;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

/**
 * A {@link DataSource} that supports multiple URI schemes. The supported schemes are:
 *
 * <ul>
 * <li>file: For fetching data from a local file (e.g. file:///path/to/media/media.mp4, or just
 *     /path/to/media/media.mp4 because the implementation assumes that a URI without a scheme is a
 *     local file URI).
 * <li>asset: For fetching data from an asset in the application's apk (e.g. asset:///media.mp4).
 * <li>content: For fetching data from a content URI (e.g. content://authority/path/123).
 * <li>http(s): For fetching data over HTTP and HTTPS (e.g. https://www.something.com/media.mp4), if
 *     constructed using {@link #DefaultDataSource(Context, TransferListener, String, boolean)}, or
 *     any other schemes supported by a base data source if constructed using
 *     {@link #DefaultDataSource(Context, TransferListener, DataSource)}.
 * </ul>
 */
public final class DefaultDataSource implements DataSource {

  private static final String SCHEME_ASSET = "asset";
  private static final String SCHEME_CONTENT = "content";
  private static final String SCHEME_RTSP= "rtsp";

  private final DataSource baseDataSource;
  private final DataSource fileDataSource;
  private final DataSource assetDataSource;
  private final DataSource contentDataSource;
  private final DataSource rtspDataSource;

  private DataSource dataSource;

  /**
   * Constructs a new instance, optionally configured to follow cross-protocol redirects.
   *
   * @param context A context.
   * @param listener An optional listener.
   * @param userAgent The User-Agent string that should be used when requesting remote data.
   * @param allowCrossProtocolRedirects Whether cross-protocol redirects (i.e. redirects from HTTP
   *     to HTTPS and vice versa) are enabled when fetching remote data..
   */
  public DefaultDataSource(Context context, TransferListener listener, String userAgent,
      boolean allowCrossProtocolRedirects) {
    this(context, listener,
        new DefaultHttpDataSource(userAgent, null, listener,
            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
            DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, allowCrossProtocolRedirects));
  }

  /**
   * Constructs a new instance that delegates to a provided {@link DataSource} for URI schemes other
   * than file, asset and content.
   *
   * @param context A context.
   * @param listener An optional listener.
   * @param baseDataSource A {@link DataSource} to use for URI schemes other than file, asset and
   *     content. This {@link DataSource} should normally support at least http(s).
   */
  public DefaultDataSource(Context context, TransferListener listener, DataSource baseDataSource) {
    this.baseDataSource = Assertions.checkNotNull(baseDataSource);
    this.fileDataSource = new FileDataSource(listener);
    this.assetDataSource = new AssetDataSource(context, listener);
    this.contentDataSource = new ContentDataSource(context, listener);

    DataSource dataSource;
    try {
      Class<? extends DataSource> rtspDataSource = Class.forName("com.google.android.exoplayer2.ext.live555.RtspDataSource")
              .asSubclass(DataSource.class);
      dataSource = rtspDataSource.newInstance();
    } catch (ClassNotFoundException e) {
      dataSource = null;
    } catch (InstantiationException e) {
      dataSource = null;
    } catch (IllegalAccessException e) {
      dataSource = null;
    }
    this.rtspDataSource = dataSource;
  }

  @Override
  public long open(DataSpec dataSpec) throws IOException {
    Assertions.checkState(dataSource == null);
    // Choose the correct source for the scheme.
    String scheme = dataSpec.uri.getScheme();
    if (Util.isLocalFileUri(dataSpec.uri)) {
      if (dataSpec.uri.getPath().startsWith("/android_asset/")) {
        dataSource = assetDataSource;
      } else {
        dataSource = fileDataSource;
      }
    } else if (SCHEME_ASSET.equals(scheme)) {
      dataSource = assetDataSource;
    } else if (SCHEME_CONTENT.equals(scheme)) {
      dataSource = contentDataSource;
    } else if (SCHEME_RTSP.equals(scheme)) {
      if (rtspDataSource != null) {
        dataSource = rtspDataSource;
      } else {
        // TODO: Should warn that RTSP scheme is being opened with an baseDataSource instance!
        dataSource = baseDataSource;
      }
    } else {
      dataSource = baseDataSource;
    }
    // Open the source and return.
    return dataSource.open(dataSpec);
  }

  @Override
  public int read(byte[] buffer, int offset, int readLength) throws IOException {
    return dataSource.read(buffer, offset, readLength);
  }

  @Override
  public Uri getUri() {
    return dataSource == null ? null : dataSource.getUri();
  }

  @Override
  public void close() throws IOException {
    if (dataSource != null) {
      try {
        dataSource.close();
      } finally {
        dataSource = null;
      }
    }
  }

}
