#
# Copyright (C) 2014 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

WORKING_DIR := $(call my-dir)

# build live555.so
include $(CLEAR_VARS)
include $(WORKING_DIR)/live555_sources.mk

LOCAL_PATH := $(WORKING_DIR)
LOCAL_MODULE := live555JNI
LOCAL_ARM_MODE := arm
LOCAL_CPP_EXTENSION := .cpp

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/live/BasicUsageEnvironment/include \
    $(LOCAL_PATH)/live/UsageEnvironment/include \
    $(LOCAL_PATH)/live/groupsock/include \
    $(LOCAL_PATH)/live/liveMedia/include
LOCAL_SRC_FILES := $(LIVE555_SOURCES)

LOCAL_CFLAGS += -D_REENTRANT -DPIC -DU_COMMON_IMPLEMENTATION -fPIC -DXLOCALE_NOT_USED -DDEBUG -DSOCKLEN_T=socklen_t -DLOCALE_NOT_USED
LOCAL_CFLAGS += -O3 -funroll-loops -finline-functions -fexceptions
LOCAL_CPPFLAGS += -fpermissive

LOCAL_LDLIBS := -llog -lz -lm
include $(BUILD_SHARED_LIBRARY)