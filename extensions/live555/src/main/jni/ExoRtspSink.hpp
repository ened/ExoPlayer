//
// Created by Sebastian Roth on 6/24/16.
//

#ifndef ENED_EXOPLAYER_RTSPSINK_HPP
#define ENED_EXOPLAYER_RTSPSINK_HPP

#include <MediaSink.hh>
#include <pthread.h>
#include <liveMedia.hh>

class MediaSubsession;

// Define a data sink (a subclass of "MediaSink") to receive the data for each subsession (i.e., each audio or video 'substream').
// In practice, this might be a class (or a chain of classes) that decodes and then renders the incoming audio or video.
// Or it might be a "FileSink", for outputting the received data into a file (as is done by the "openRTSP" application).
// In this example code, however, we define a simple 'dummy' sink that receives incoming data, but does nothing with it.

class ExoRtspSink : public MediaSink {
public:
    static ExoRtspSink *createNew(UsageEnvironment &env,
                                  MediaSubsession &subsession, // identifies the kind of data that's being received
                                  char const *streamId = NULL); // identifies the stream itself (optional)

    int retrieveSPSPPS(u_int8_t **destination);
    int copySharedBuffer(u_int8_t **destination);

private:
    ExoRtspSink(UsageEnvironment &env, MediaSubsession &subsession, char const *streamId);

    // called only by "createNew()"
    virtual ~ExoRtspSink();

    static void afterGettingFrame(void *clientData, unsigned frameSize,
                                  unsigned numTruncatedBytes,
                                  struct timeval presentationTime,
                                  unsigned durationInMicroseconds);

    void afterGettingFrame(unsigned frameSize, unsigned numTruncatedBytes,
                           struct timeval presentationTime, unsigned durationInMicroseconds);

private:
    // redefined virtual functions:
    virtual Boolean continuePlaying();

private:
    u_int8_t *fReceiveBuffer;
    u_int8_t *fSharedBuffer;
    size_t bufferFrameSize;
    SPropRecord *sPropRecord;

    MediaSubsession &fSubsession;
    char *fStreamId;
    pthread_mutex_t bufferMutex;
};

#endif //ENED_EXOPLAYER_RTSPSINK_HPP
