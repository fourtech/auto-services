LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE := libautoservices_jni

LOCAL_SRC_FILES:= \
    onload.cpp \
    DeviceUtils.cpp \
    FmTransmitter.cpp \
    com_fourtech_hardware_Display.cpp \
    com_fourtech_hardware_EleRadar.cpp \
    com_fourtech_hardware_FmTransmitter.cpp \
    com_fourtech_hardware_Accelerometer.cpp \
    com_fourtech_autostate_AutoStateService.cpp

LOCAL_C_INCLUDES += \
    $(call include-path-for, libhardware)/hardware \
    $(call include-path-for, libhardware_legacy)/hardware_legacy

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libnativehelper \
    libhardware \
    libhardware_legacy

ifeq ($(WITH_MALLOC_LEAK_CHECK),true)
    LOCAL_CFLAGS += -DMALLOC_LEAK_CHECK
endif

include $(BUILD_SHARED_LIBRARY)
