//
// Created by Sebastian Roth on 6/24/16.
//

#include "ExoRtspSink.hpp"

#include <liveMedia.hh>
#include <ostream>

// Implementation of "ExoRtspSink":

// Even though we're not going to be doing anything with the incoming data, we still need to receive it.
// Define the size of the buffer that we'll use:
#define DUMMY_SINK_RECEIVE_BUFFER_SIZE 100000

ExoRtspSink *ExoRtspSink::createNew(UsageEnvironment &env, MediaSubsession &subsession,
                                    char const *streamId) {
    return new ExoRtspSink(env, subsession, streamId);
}

ExoRtspSink::ExoRtspSink(UsageEnvironment &env, MediaSubsession &subsession, char const *streamId)
        : MediaSink(env),
          fSubsession(subsession) {
    fStreamId = strDup(streamId);
    fReceiveBuffer = new u_int8_t[DUMMY_SINK_RECEIVE_BUFFER_SIZE];
    fSharedBuffer = NULL;

    pthread_mutex_init(&bufferMutex, NULL);

    env << "Stream ID: " << streamId << ", codec: " << fSubsession.codecName() << "\n";

    // bufferMutex = PTHREAD_MUTEX_INITIALIZER;
    iFrameSize = 0;
    sPropRecord = NULL;
}

ExoRtspSink::~ExoRtspSink() {
    // This buffer is used by Live555 internally to store the frame.
    delete[] fReceiveBuffer;

    delete[] fStreamId;

    // These flags are used for getting the data out.
    delete[] fSharedBuffer;
    delete[] sPropRecord;
}

void ExoRtspSink::afterGettingFrame(void *clientData, unsigned frameSize,
                                    unsigned numTruncatedBytes,
                                    struct timeval presentationTime,
                                    unsigned durationInMicroseconds) {
    ExoRtspSink *sink = (ExoRtspSink *) clientData;
    sink->afterGettingFrame(frameSize, numTruncatedBytes, presentationTime, durationInMicroseconds);
}

// If you don't want to see debugging output for each received frame, then comment out the following line:
// #define DEBUG_PRINT_EACH_RECEIVED_FRAME 1

void ExoRtspSink::afterGettingFrame(unsigned frameSize, unsigned numTruncatedBytes,
                                    struct timeval presentationTime,
                                    unsigned durationInMicroseconds) {
    // We've just received a frame of data.  (Optionally) print out information about it:
#ifdef DEBUG_PRINT_EACH_RECEIVED_FRAME
    if (fStreamId != NULL) envir() << "Stream \"" << fStreamId << "\"; ";
    envir() << fSubsession.mediumName() << "/" << fSubsession.codecName() << ":\tReceived " <<
    frameSize << " bytes";
    if (numTruncatedBytes > 0) envir() << " (with " << numTruncatedBytes << " bytes truncated)";
    char uSecsStr[6 + 1]; // used to output the 'microseconds' part of the presentation time
    sprintf(uSecsStr, "%06u", (unsigned) presentationTime.tv_usec);
    envir() << ".\tPresentation time: " << (int) presentationTime.tv_sec << "." << uSecsStr;
    if (fSubsession.rtpSource() != NULL &&
        !fSubsession.rtpSource()->hasBeenSynchronizedUsingRTCP()) {
        envir() <<
        "!"; // mark the debugging output to indicate that this presentation time is not RTCP-synchronized
    }
#ifdef DEBUG_PRINT_NPT
    envir() << "\tNPT: " << fSubsession.getNormalPlayTime(presentationTime);
#endif
    envir() << "\n";
#endif

    pthread_mutex_lock(&bufferMutex);

    delete[] fSharedBuffer;

    unsigned int records = 0;
    SPropRecord *pPropRecord = parseSPropParameterSets(fSubsession.fmtp_spropparametersets(),
                                                       records);

    if (records == 2) {
        delete[] sPropRecord;

        // TODO: Check.
//        envir() << "records: " << records << "\n";

        envir() << "1: " << pPropRecord[0].sPropBytes << ", 2: " << pPropRecord[1].sPropBytes << "\n";

        sPropRecord = pPropRecord;
    } else {
        // Keep the last record.
//        sPropRecord = NULL;
    }

    // Fill the shared buffer
    fSharedBuffer = new u_int8_t[frameSize];
    memcpy(fSharedBuffer, fReceiveBuffer, frameSize);
    iFrameSize = frameSize;

    // Remember this frames presentation time
    u32FrameTimeUs = presentationTime.tv_usec;
    u16DurationMs = durationInMicroseconds;

    pthread_mutex_unlock(&bufferMutex);

    // Then continue, to request the next frame of data:
    continuePlaying();
}

Boolean ExoRtspSink::continuePlaying() {
    if (fSource == NULL) return False; // sanity check (should not happen)

    // Request the next frame of data from our input source.  "afterGettingFrame()" will get called later, when it arrives:
    fSource->getNextFrame(fReceiveBuffer, DUMMY_SINK_RECEIVE_BUFFER_SIZE,
                          afterGettingFrame, this,
                          onSourceClosure, this);
    return True;
}

size_t ExoRtspSink::retrieveSPSPPS(u_int8_t **destination) {

    pthread_mutex_lock(&bufferMutex);

    if (sPropRecord == NULL) {
        pthread_mutex_unlock(&bufferMutex);
        return 0;
    }

    unsigned int size = sPropRecord->sPropLength;

    if (destination != NULL) {
        *destination = new u_int8_t[size];
        memcpy(*destination, sPropRecord->sPropBytes, size);
    }


    pthread_mutex_unlock(&bufferMutex);

    return size;
}

size_t ExoRtspSink::copySharedBuffer(u_int8_t **destination) {

    pthread_mutex_lock(&bufferMutex);

//    envir() << "framesize: " << iFrameSize << "\n";

    size_t size = iFrameSize;

    if (size == 0) {
        pthread_mutex_unlock(&bufferMutex);
        return 0;
    }

    if (destination != NULL) {
        // Initialization data.
        *destination = new u_int8_t[size];
        memcpy(*destination, fSharedBuffer, size);
    }

    pthread_mutex_unlock(&bufferMutex);

//    envir() << "byte#1, A: " << (*destination[0] == fSharedBuffer[0]) << " |" << fSharedBuffer[0] << "|\n";

//    envir() << "copying shared buffer:" << size << "\n";

    return size;
}
