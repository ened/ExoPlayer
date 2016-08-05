/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <jni.h>

#include <android/log.h>

#include <algorithm>
#include <cstdio>
#include <pthread.h>

#include "liveMedia.hh"  // NOLINT
#include "ExoRtspUsageEnvironment.hpp"  // NOLINT
#include "ExoRtspClient.hpp"
#include "ExoRtspSink.hpp"

#define RTSP_CLIENT_VERBOSITY_LEVEL 1 // by default, print verbose output from each "RTSPClient"

#define LOG_TAG "LIVE555_DEC"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))

#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, \
                                             __VA_ARGS__))

#define FUNC(RETURN_TYPE, NAME, ...) \
  extern "C" { \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_live555_Live555Jni_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__);\
  } \
  JNIEXPORT RETURN_TYPE \
    Java_com_google_android_exoplayer2_ext_live555_Live555Jni_ ## NAME \
      (JNIEnv* env, jobject thiz, ##__VA_ARGS__)\


static jmethodID allocateBuffers;
static jfieldID spsPpsField;
static jfieldID frameField;
static jfieldID presentationTimeUsField;


FUNC(jstring, getLibLive555Version) {
    return env->NewStringUTF(LIVEMEDIA_LIBRARY_VERSION_STRING);
}

FUNC(void, live555Init) {
    const jclass jniClass = env->FindClass(
            "com/google/android/exoplayer2/ext/live555/Live555Jni");

    allocateBuffers = env->GetMethodID(jniClass, "allocateBuffers", "(II)V");

    spsPpsField = env->GetFieldID(jniClass, "spsPps", "Ljava/nio/ByteBuffer;");
    frameField = env->GetFieldID(jniClass, "frame", "Ljava/nio/ByteBuffer;");
    presentationTimeUsField = env->GetFieldID(jniClass, "framePresentationTimeUs", "J");
}

FUNC(void, stop, jlong jClient) {
    ExoRtspClient *client = reinterpret_cast<ExoRtspClient *>(jClient);
    exoShutdownStream(client);
}

FUNC(jlong, openUrl, jstring url) {
    const char *rtspURL = env->GetStringUTFChars(url, 0);

    // Begin by setting up our usage environment:
    TaskScheduler *scheduler = BasicTaskScheduler::createNew();
    UsageEnvironment *liveEnv = ExoRtspUsageEnvironment::createNew(*scheduler);

    ExoRtspClient *pClient = ExoRtspClient::createNew(*liveEnv, rtspURL, RTSP_CLIENT_VERBOSITY_LEVEL,
                             "ExoPlayer2");

    if (!pClient) {
        *liveEnv << "Failed to create a RTSP client for URL \"" << rtspURL << "\": " <<
        liveEnv->getResultMsg() << "\n";
        return NULL;
    }

    // Next, send a RTSP "DESCRIBE" command, to get a SDP description for the stream.
    // Note that this command - like all RTSP commands - is sent asynchronously; we do not block, waiting for a response.
    // Instead, the following function call returns immediately, and we handle the RTSP response later, from within the event loop:
    pClient->sendDescribeCommand(exoContinueAfterDESCRIBE, NULL);

    pthread_t looperThread;
    pthread_create(&looperThread, NULL, pClient->eventLooper, (void *) pClient);

    return reinterpret_cast<intptr_t>(pClient);
}

int checkClient(ExoRtspClient *client);

FUNC(void, fetchFrame, jlong jClient, jobject jJniObject) {
    ExoRtspClient *client = reinterpret_cast<ExoRtspClient *>(jClient);

    if (checkClient(client) <= 0) {
        return;
    }

    ExoRtspSink *sink = (ExoRtspSink *) client->scs.getSubsession()->sink;

    u_int8_t *buffer = NULL;
    size_t size = sink->retrieveSPSPPS(&buffer);

    u_int8_t *buffer2 = NULL;
    size_t size2 = sink->copySharedBuffer(&buffer2);

    if (size == 0 || size2 == 0) {
        return;
    }

    env->CallVoidMethod(jJniObject, allocateBuffers, size, size2);

    const jobject spsPpsObject = env->GetObjectField(jJniObject, spsPpsField);
    uint8_t *const dst =
            reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(spsPpsObject));

    memcpy(dst, buffer, size);

    const jobject frameObject = env->GetObjectField(jJniObject, frameField);
    uint8_t *const dst2 =
            reinterpret_cast<uint8_t *>(env->GetDirectBufferAddress(frameObject));

    memcpy(dst2, buffer2, size2);

    env->SetLongField(jJniObject, presentationTimeUsField, 1);
}

FUNC(jbyteArray, retrieveSPSPPS, jlong jClient) {
    ExoRtspClient *client = reinterpret_cast<ExoRtspClient *>(jClient);

    if (checkClient(client) <= 0) {
        return NULL;
    }

    ExoRtspSink *sink = (ExoRtspSink *) client->scs.getSubsession()->sink;

    u_int8_t *buffer = NULL;
    int size = sink->retrieveSPSPPS(&buffer);

    if (size > 0) {
        jbyteArray result = env->NewByteArray(size);
        env->SetByteArrayRegion(result, 0, size, (const jbyte *) buffer);
        return result;
    }

    return NULL;
}
//
//FUNC(jbyteArray, getFrame, jlong jClient) {
//    ExoRtspClient *client = reinterpret_cast<ExoRtspClient *>(jClient);
//
//    if (!checkClient(client)) {
//        return NULL;
//    }
//
//    ExoRtspSink *sink = (ExoRtspSink *) client->scs.getSubsession()->sink;
//
//    u_int8_t *buffer = NULL;
//    int size = sink->copySharedBuffer(&buffer);
//
//    if (size > 0) {
//        jbyteArray result = env->NewByteArray(size);
//        env->SetByteArrayRegion(result, 0, size, (const jbyte *) buffer);
//
////        jbyte tmp[1];
////        env->GetByteArrayRegion(result, 0, 1, tmp);
////        client->envir() << "byte#1, B: " << (buffer[0] == tmp[0]) << " |" << tmp[0] << "|\n";
//
//        return result;
//    }
//
//    return NULL;
//}

int checkClient(ExoRtspClient *client) {
    if (client == NULL) {
        // LOGD("rtspClient is null");
        return -1;
    }

    if (client->scs.getSubsession() == NULL) {
        // LOGD("subsession is null");
        return -1;
    }

    if (client->scs.getSubsession()->sink == NULL) {
        // LOGD("sink is null");
        return -1;
    }

    return 1;
}