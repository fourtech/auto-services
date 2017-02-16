LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := auto-services

LOCAL_SRC_FILES := $(call all-java-files-under, java)

LOCAL_JAVA_LIBRARIES := auto-common

include $(BUILD_JAVA_LIBRARY)

# build jni
include $(LOCAL_PATH)/jni/Android.mk