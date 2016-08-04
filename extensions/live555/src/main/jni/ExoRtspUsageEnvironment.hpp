//
// Created by Sebastian Roth on 6/24/16.
//

#ifndef ENED_EXOPLAYER_EXORTSPUSAGEENVIRONMENT_HPP
#define ENED_EXOPLAYER_EXORTSPUSAGEENVIRONMENT_HPP

#include <string>
#include <sstream>
#include "BasicUsageEnvironment.hh"

class ExoRtspUsageEnvironment : public BasicUsageEnvironment {
public:
  static ExoRtspUsageEnvironment* createNew(TaskScheduler& taskScheduler);

  virtual UsageEnvironment& operator<<(char const* str);
  virtual UsageEnvironment& operator<<(int i);
  virtual UsageEnvironment& operator<<(unsigned u);
  virtual UsageEnvironment& operator<<(double d);
  virtual UsageEnvironment& operator<<(void* p);

protected:
  ExoRtspUsageEnvironment(TaskScheduler& taskScheduler);
      // called only by "createNew()" (or subclass constructors)
  virtual ~ExoRtspUsageEnvironment();

private:
    std::stringstream buffer;

    void maybeLog();
};



#endif //ENED_EXOPLAYER_EXORTSPUSAGEENVIRONMENT_HPP
