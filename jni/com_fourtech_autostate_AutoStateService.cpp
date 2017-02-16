#define LOG_TAG "AutoStateService-jni"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>

#include "DeviceUtils.h"

#define DEBUG                    (0)
#define ACC_DEVICE_PATH          "/sys/class/gpio-detection/car-acc/status"
#define REVERSE_DEVICE_PATH      "/sys/class/gpio-detection/car-reverse/status"
#define CPU_TEMP_PATH            "/sys/class/thermal/thermal_zone2/temp"
#define GPU_TEMP_PATH            "/sys/class/thermal/thermal_zone5/temp"
#define CPU_FREQ_PATH            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq"
#define AUTO_STATE_SERVICE_CLASS "com/fourtech/autostate/AutoStateService"

namespace android {

static Mutex sMutex;

static jboolean AutoStateService_isAccOn(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	int len;
	char buffer[4];
	buffer[0] = '1';
	len = _readdev(ACC_DEVICE_PATH, buffer, 4);
	if (len >= 0) buffer[len] = '\0';

	if (DEBUG) ALOGI("AutoStateService_isAccOn() result=%s", buffer);

	return (buffer[0] == '1') ? true : false;
}

static jboolean AutoStateService_isReversing(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	int len;
	char buffer[4];
	buffer[0] = '0';
	len = _readdev(REVERSE_DEVICE_PATH, buffer, 4);
	if (len >= 0) buffer[len] = '\0';

	if (DEBUG) ALOGI("AutoStateService_isReversing() result=%s", buffer);

	return (buffer[0] == '1') ? true : false;
}

static jint AutoStateService_getCpuTemp(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	char buffer[32];
	int len = _readdev(CPU_TEMP_PATH, buffer, 32);
	if (len >= 0) buffer[len] = '\0';

	if (DEBUG) ALOGI("AutoStateService_getCpuTemp() result=%s", buffer);

	return atoi(buffer);
}

static jint AutoStateService_getGpuTemp(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	char buffer[32];
	int len = _readdev(GPU_TEMP_PATH, buffer, 32);
	if (len >= 0) buffer[len] = '\0';

	if (DEBUG) ALOGI("AutoStateService_getGpuTemp() result=%s", buffer);

	return atoi(buffer);
}

static jint AutoStateService_getCpuFreq(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	char buffer[32];
	int len = _readdev(CPU_FREQ_PATH, buffer, 32);
	if (len >= 0) buffer[len] = '\0';

	if (DEBUG) ALOGI("AutoStateService_getCpuFreq() result=%s", buffer);

	return atoi(buffer);
}

static JNINativeMethod method_table[] = {
		{ "native_isAccOn",     "()Z", (void*) AutoStateService_isAccOn },
		{ "native_isReversing", "()Z", (void*) AutoStateService_isReversing },
		{ "native_getCpuTemp",  "()I", (void*) AutoStateService_getCpuTemp },
		{ "native_getGpuTemp",  "()I", (void*) AutoStateService_getGpuTemp },
		{ "native_getCpuFreq",  "()I", (void*) AutoStateService_getCpuFreq },
};

int register_fourtech_autostate_AutoStateService(JNIEnv *env) {
	return jniRegisterNativeMethods(env, AUTO_STATE_SERVICE_CLASS, method_table, NELEM(method_table));
}

}
