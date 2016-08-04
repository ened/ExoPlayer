//
// Created by Sebastian Roth on 6/24/16.
//

#ifndef ENED_EXOPLAYER_STREAMCLIENTSTATE_HPP
#define ENED_EXOPLAYER_STREAMCLIENTSTATE_HPP

#include <MediaSession.hh>

typedef void *TaskToken;

class MediaSession;

class MediaSubsession;

class MediaSubsessionIterator;

// Define a class to hold per-stream state that we maintain throughout each stream's lifetime:

class StreamClientState {
public:
    StreamClientState();

    virtual ~StreamClientState();

    MediaSubsession *getSubsession() const {
        return this->subsession;
    }

    void setSubsession(MediaSubsession *session) {
        this->subsession = session;
    }

public:
    MediaSubsessionIterator *iter;
    MediaSession *session;
    TaskToken streamTimerTask;
    double duration;
private:
    MediaSubsession *subsession;
};

#endif //ENED_EXOPLAYER_STREAMCLIENTSTATE_HPP
