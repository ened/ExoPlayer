//
// Created by Sebastian Roth on 6/24/16.
//

#ifndef ENED_EXOPLAYER_RTSPCLIENT_HPP
#define ENED_EXOPLAYER_RTSPCLIENT_HPP

#include <liveMedia.hh>

#include "StreamClientState.hpp"

// RTSP 'response handlers':
void exoContinueAfterDESCRIBE(RTSPClient* rtspClient, int resultCode, char* resultString);
void exoContinueAfterSETUP(RTSPClient* rtspClient, int resultCode, char* resultString);
void exoContinueAfterPLAY(RTSPClient* rtspClient, int resultCode, char* resultString);

// Other event handler functions:
void exoSubsessionAfterPlaying(void* clientData); // called when a stream's subsession (e.g., audio or video substream) ends
void exoSubsessionByeHandler(void* clientData); // called when a RTCP "BYE" is received for a subsession
void exoStreamTimerHandler(void* clientData);
  // called at the end of a stream's expected duration (if the stream has not already signaled its end using a RTCP "BYE")

// The main streaming routine (for each "rtsp://" URL):
void exoOpenURL(UsageEnvironment& env, char const* progName, char const* rtspURL);

// Used to iterate through each stream's 'subsessions', setting up each one:
void exoSetupNextSubsession(RTSPClient* rtspClient);

// Used to shut down and close a stream (including its "RTSPClient" object):
void exoShutdownStream(RTSPClient* rtspClient);

// If you're streaming just a single stream (i.e., just from a single URL, once), then you can define and use just a single
// "StreamClientState" structure, as a global variable in your application.  However, because - in this demo application - we're
// showing how to play multiple streams, concurrently, we can't do that.  Instead, we have to have a separate "StreamClientState"
// structure for each "RTSPClient".  To do this, we subclass "RTSPClient", and add a "StreamClientState" field to the subclass:

class ExoRtspClient: public RTSPClient {
public:
  static ExoRtspClient* createNew(UsageEnvironment& env, char const* rtspURL,
				  int verbosityLevel = 0,
				  char const* applicationName = NULL,
				  portNumBits tunnelOverHTTPPortNum = 0);

	static void *eventLooper(void *input);

	void stopClient();

protected:
  ExoRtspClient(UsageEnvironment& env, char const* rtspURL,
		int verbosityLevel, char const* applicationName, portNumBits tunnelOverHTTPPortNum);
    // called only by createNew();
  virtual ~ExoRtspClient();

public:
  StreamClientState scs;
private:
	char stop;
};

#endif //ENED_EXOPLAYER_RTSPCLIENT_HPP
