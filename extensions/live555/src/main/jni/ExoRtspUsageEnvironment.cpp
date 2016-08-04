//
// Created by Sebastian Roth on 6/24/16.
//

#include "ExoRtspUsageEnvironment.hpp"

#include <android/log.h>

#define LOG_TAG "LIVE555_DEC"
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, \
                                             __VA_ARGS__))

#define LOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, \
                                             __VA_ARGS__))

#define ENABLE_ENV_DEBUG 1

ExoRtspUsageEnvironment::ExoRtspUsageEnvironment(TaskScheduler &taskScheduler)
        : BasicUsageEnvironment(taskScheduler),
          buffer("") {
}

ExoRtspUsageEnvironment::~ExoRtspUsageEnvironment() {
}

ExoRtspUsageEnvironment *
ExoRtspUsageEnvironment::createNew(TaskScheduler &taskScheduler) {
    return new ExoRtspUsageEnvironment(taskScheduler);
}

UsageEnvironment &ExoRtspUsageEnvironment::operator<<(char const *str) {
    if (str == NULL) str = "(NULL)"; // sanity check

#ifdef ENABLE_ENV_DEBUG
    buffer << str;
    maybeLog();
#endif

    return *this;
}

UsageEnvironment &ExoRtspUsageEnvironment::operator<<(int i) {
#ifdef ENABLE_ENV_DEBUG
    buffer << i;
    maybeLog();
#endif
    return *this;
}

UsageEnvironment &ExoRtspUsageEnvironment::operator<<(unsigned u) {
#ifdef ENABLE_ENV_DEBUG
    buffer << u;
    maybeLog();
#endif
    return *this;
}

UsageEnvironment &ExoRtspUsageEnvironment::operator<<(double d) {
#ifdef ENABLE_ENV_DEBUG
    buffer << d;
    maybeLog();
#endif
    return *this;
}

UsageEnvironment &ExoRtspUsageEnvironment::operator<<(void *p) {
#ifdef ENABLE_ENV_DEBUG
    buffer << p;
    maybeLog();
#endif
    return *this;
}


void ExoRtspUsageEnvironment::maybeLog() {

    std::string str = buffer.str();

    if (str.length() > 0 && str.at(str.length() - 1) == '\n') {
        LOGD("%s", str.c_str());
        buffer.str("");
        buffer.clear();
    }

}