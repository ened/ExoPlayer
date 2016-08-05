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

    size_t retrieveSPSPPS(u_int8_t **destination);
    size_t copySharedBuffer(u_int8_t **destination);

//    class SinkSharedBuffer {
//        friend class ExoRtspSink;
//
//        const SinkSharedBuffer(u_int8_t *frame, u_int8_t* sps, u_int8_t* pps, suseconds_t presentationTime, int durationMs)
//           : m_frame(frame), m_sps(sps), m_pps(pps), m_presentationTimeUs(presentationTime), m_durationMs(durationMs) {
//
//        }
//        const ~SinkSharedBuffer() {
//            delete[] m_frame, m_sps, m_pps;
//        }
//
//    private:
//        u_int8_t *m_frame;
//        u_int8_t *m_sps;
//        u_int8_t *m_pps;
//        suseconds_t m_presentationTimeUs;
//        int m_durationMs;
//    };
//
//    SinkSharedBuffer* maybeGetSharedBuffer(suseconds_t lastTimeUs) {
//        if (u32FrameTimeUs == lastTimeUs) {
//            return NULL;
//        }
//
//        return new SinkSharedBuffer(fSharedBuffer, NULL, NULL, 0, 0l);
//    }

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
    size_t iFrameSize;

    SPropRecord *sPropRecord;

    suseconds_t u32FrameTimeUs;
    int u16DurationMs;

    MediaSubsession &fSubsession;
    char *fStreamId;

    pthread_mutex_t bufferMutex;
};

#endif //ENED_EXOPLAYER_RTSPSINK_HPP
