//
// Created by Sebastian Roth on 6/24/16.
//

#include "ExoRtspClient.hpp"

#include "ExoRtspSink.hpp"


// A function that outputs a string that identifies each stream (for debugging output).  Modify this if you wish:
UsageEnvironment &operator<<(UsageEnvironment &env, const RTSPClient &rtspClient) {
    return env << "[URL:\"" << rtspClient.url() << "\"]: ";
}

// A function that outputs a string that identifies each subsession (for debugging output).  Modify this if you wish:
UsageEnvironment &operator<<(UsageEnvironment &env, const MediaSubsession &subsession) {
    return env << subsession.mediumName() << "/" << subsession.codecName();
}

// Implementation of the RTSP 'response handlers':

void exoContinueAfterDESCRIBE(RTSPClient *rtspClient, int resultCode, char *resultString) {
    do {
        UsageEnvironment &env = rtspClient->envir(); // alias
        StreamClientState &scs = ((ExoRtspClient *) rtspClient)->scs; // alias

        if (resultCode != 0) {
            env << *rtspClient << "Failed to get a SDP description: " << resultString << "\n";
            delete[] resultString;
            break;
        }

        char *const sdpDescription = resultString;
        env << *rtspClient << "Got a SDP description:\n" << sdpDescription << "\n";

        // Create a media session object from this SDP description:
        scs.session = MediaSession::createNew(env, sdpDescription);
        delete[] sdpDescription; // because we don't need it anymore
        if (scs.session == NULL) {
            env << *rtspClient <<
            "Failed to create a MediaSession object from the SDP description: " <<
            env.getResultMsg() << "\n";
            break;
        } else if (!scs.session->hasSubsessions()) {
            env << *rtspClient << "This session has no media subsessions (i.e., no \"m=\" lines)\n";
            break;
        }

        // Then, create and set up our data source objects for the session.  We do this by iterating over the session's 'subsessions',
        // calling "MediaSubsession::initiate()", and then sending a RTSP "SETUP" command, on each one.
        // (Each 'subsession' will have its own data source.)
        scs.iter = new MediaSubsessionIterator(*scs.session);
        exoSetupNextSubsession(rtspClient);
        return;
    } while (0);

    // An unrecoverable error occurred with this stream.
    exoShutdownStream(rtspClient);
}

// By default, we request that the server stream its data using RTP/UDP.
// If, instead, you want to request that the server stream via RTP-over-TCP, change the following to True:
#define REQUEST_STREAMING_OVER_TCP False

void exoSetupNextSubsession(RTSPClient *rtspClient) {
    UsageEnvironment &env = rtspClient->envir(); // alias
    StreamClientState &scs = ((ExoRtspClient *) rtspClient)->scs; // alias

    MediaSubsession *next = scs.iter->next();
    if (next != NULL) {
        if (!next->initiate()) {
            env << *rtspClient << "Failed to initiate the \"" << *next <<
            "\" subsession: " << env.getResultMsg() << "\n";
            exoSetupNextSubsession(rtspClient); // give up on this subsession; go to the next one
        } else {
            env << *rtspClient << "Initiated the \"" << *next << "\" subsession (";
            if (next->rtcpIsMuxed()) {
                env << "client port " << next->clientPortNum();
            } else {
                env << "client ports " << next->clientPortNum() << "-" <<
                next->clientPortNum() + 1;
            }
            env << ")\n";

            // Continue setting up this subsession, by sending a RTSP "SETUP" command:
            rtspClient->sendSetupCommand(*next, exoContinueAfterSETUP, False,
                                         REQUEST_STREAMING_OVER_TCP);

            scs.setSubsession(next);
        }
        return;
    }

    // We've finished setting up all of the subsessions.  Now, send a RTSP "PLAY" command to start the streaming:
    if (scs.session->absStartTime() != NULL) {
        // Special case: The stream is indexed by 'absolute' time, so send an appropriate "PLAY" command:
        rtspClient->sendPlayCommand(*scs.session, exoContinueAfterPLAY, scs.session->absStartTime(),
                                    scs.session->absEndTime());
    } else {
        scs.duration = scs.session->playEndTime() - scs.session->playStartTime();
        rtspClient->sendPlayCommand(*scs.session, exoContinueAfterPLAY);
    }
}

void exoContinueAfterSETUP(RTSPClient *rtspClient, int resultCode, char *resultString) {
    do {
        UsageEnvironment &env = rtspClient->envir(); // alias
        StreamClientState &scs = ((ExoRtspClient *) rtspClient)->scs; // alias
        MediaSubsession *subsession = scs.getSubsession();

        if (resultCode != 0) {
            env << *rtspClient << "Failed to set up the \"" << *subsession <<
            "\" subsession: " << resultString << "\n";
            break;
        }

        env << *rtspClient << "Set up the \"" << *subsession << "\" subsession (";
        if (subsession->rtcpIsMuxed()) {
            env << "client port " << subsession->clientPortNum();
        } else {
            env << "client ports " << subsession->clientPortNum() << "-" <<
            subsession->clientPortNum() + 1;
        }
        env << ")\n";

        // Having successfully setup the subsession, create a data sink for it, and call "startPlaying()" on it.
        // (This will prepare the data sink to receive data; the actual flow of data from the client won't start happening until later,
        // after we've sent a RTSP "PLAY" command.)

        subsession->sink = ExoRtspSink::createNew(env, *subsession, rtspClient->url());
        // perhaps use your own custom "MediaSink" subclass instead
        if (subsession->sink == NULL) {
            env << *rtspClient << "Failed to create a data sink for the \"" << *subsession
            << "\" subsession: " << env.getResultMsg() << "\n";
            break;
        }

        env << *rtspClient << "Created a data sink for the \"" << *subsession <<
        "\" subsession\n";
        subsession->miscPtr = rtspClient; // a hack to let subsession handler functions get the "RTSPClient" from the subsession
        subsession->sink->startPlaying(*(subsession->readSource()),
                                       exoSubsessionAfterPlaying, subsession);
        // Also set a handler to be called if a RTCP "BYE" arrives for this subsession:
        if (subsession->rtcpInstance() != NULL) {
            subsession->rtcpInstance()->setByeHandler(exoSubsessionByeHandler, subsession);
        }
    } while (0);
    delete[] resultString;

    // Set up the next subsession, if any:
    exoSetupNextSubsession(rtspClient);
}

void exoContinueAfterPLAY(RTSPClient *rtspClient, int resultCode, char *resultString) {
    Boolean success = False;

    do {
        UsageEnvironment &env = rtspClient->envir(); // alias
        StreamClientState &scs = ((ExoRtspClient *) rtspClient)->scs; // alias

        if (resultCode != 0) {
            env << *rtspClient << "Failed to start playing session: " << resultString << "\n";
            break;
        }

        // Set a timer to be handled at the end of the stream's expected duration (if the stream does not already signal its end
        // using a RTCP "BYE").  This is optional.  If, instead, you want to keep the stream active - e.g., so you can later
        // 'seek' back within it and do another RTSP "PLAY" - then you can omit this code.
        // (Alternatively, if you don't want to receive the entire stream, you could set this timer for some shorter value.)
        if (scs.duration > 0) {
            unsigned const delaySlop = 2; // number of seconds extra to delay, after the stream's expected duration.  (This is optional.)
            scs.duration += delaySlop;
            unsigned uSecsToDelay = (unsigned) (scs.duration * 1000000);
            scs.streamTimerTask = env.taskScheduler().scheduleDelayedTask(uSecsToDelay,
                                                                          (TaskFunc *) exoStreamTimerHandler,
                                                                          rtspClient);
        }

        env << *rtspClient << "Started playing session";
        if (scs.duration > 0) {
            env << " (for up to " << scs.duration << " seconds)";
        }
        env << "...\n";

        success = True;
    } while (0);
    delete[] resultString;

    if (!success) {
        // An unrecoverable error occurred with this stream.
        exoShutdownStream(rtspClient);
    }
}


// Implementation of the other event handlers:

void exoSubsessionAfterPlaying(void *clientData) {
    MediaSubsession *subsession = (MediaSubsession *) clientData;
    RTSPClient *rtspClient = (RTSPClient *) (subsession->miscPtr);

    // Begin by closing this subsession's stream:
    Medium::close(subsession->sink);
    subsession->sink = NULL;

    // Next, check whether *all* subsessions' streams have now been closed:
    MediaSession &session = subsession->parentSession();
    MediaSubsessionIterator iter(session);
    while ((subsession = iter.next()) != NULL) {
        if (subsession->sink != NULL) return; // this subsession is still active
    }

    // All subsessions' streams have now been closed, so shutdown the client:
    exoShutdownStream(rtspClient);
}

void exoSubsessionByeHandler(void *clientData) {
    MediaSubsession *subsession = (MediaSubsession *) clientData;
    RTSPClient *rtspClient = (RTSPClient *) subsession->miscPtr;
    UsageEnvironment &env = rtspClient->envir(); // alias

    env << *rtspClient << "Received RTCP \"BYE\" on \"" << *subsession << "\" subsession\n";

    // Now act as if the subsession had closed:
    exoSubsessionAfterPlaying(subsession);
}

void exoStreamTimerHandler(void *clientData) {
    ExoRtspClient *rtspClient = (ExoRtspClient *) clientData;
    StreamClientState &scs = rtspClient->scs; // alias

    scs.streamTimerTask = NULL;

    // Shut down the stream:
    exoShutdownStream(rtspClient);
}

void exoShutdownStream(RTSPClient *rtspClient) {
    UsageEnvironment &env = rtspClient->envir(); // alias
    StreamClientState &scs = ((ExoRtspClient *) rtspClient)->scs; // alias

    // First, check whether any subsessions have still to be closed:
    if (scs.session != NULL) {
        Boolean someSubsessionsWereActive = False;
        MediaSubsessionIterator iter(*scs.session);
        MediaSubsession *subsession;

        while ((subsession = iter.next()) != NULL) {
            if (subsession->sink != NULL) {
                Medium::close(subsession->sink);
                subsession->sink = NULL;

                if (subsession->rtcpInstance() != NULL) {
                    subsession->rtcpInstance()->setByeHandler(NULL,
                                                              NULL); // in case the server sends a RTCP "BYE" while handling "TEARDOWN"
                }

                someSubsessionsWereActive = True;
            }
        }

        if (someSubsessionsWereActive) {
            // Send a RTSP "TEARDOWN" command, to tell the server to shutdown the stream.
            // Don't bother handling the response to the "TEARDOWN".
            rtspClient->sendTeardownCommand(*scs.session, NULL);
        }
    }

    env << *rtspClient << "Closing the stream.\n";
    Medium::close(rtspClient);
}

// Implementation of "ExoRtspClient":

ExoRtspClient *ExoRtspClient::createNew(UsageEnvironment &env, char const *rtspURL,
                                        int verbosityLevel, char const *applicationName,
                                        portNumBits tunnelOverHTTPPortNum) {
    return new ExoRtspClient(env, rtspURL, verbosityLevel, applicationName, tunnelOverHTTPPortNum);
}

/* static */ void *ExoRtspClient::eventLooper(void *input) {
    ExoRtspClient *client = (ExoRtspClient *) input;

    client->envir().taskScheduler().doEventLoop(&client->stop);
}

void ExoRtspClient::stopClient() {
    exoShutdownStream(this);

    // Set the live555 stop variable.
    stop = 1;
}

ExoRtspClient::ExoRtspClient(UsageEnvironment &env, char const *rtspURL,
                             int verbosityLevel, char const *applicationName,
                             portNumBits tunnelOverHTTPPortNum)
        : RTSPClient(env, rtspURL, verbosityLevel, applicationName, tunnelOverHTTPPortNum, -1),
          stop(0) {
}

ExoRtspClient::~ExoRtspClient() {
}
