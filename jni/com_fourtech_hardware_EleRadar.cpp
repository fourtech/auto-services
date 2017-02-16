#define LOG_TAG "EleRadar-jni"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>

#include "DeviceUtils.h"

#define DEBUG           (0)
#define ELE_RADAR_PATH  "/sys/class/leds/ele-radar/brightness"
#define ELE_RADAR_CLASS "com/fourtech/hardware/EleRadar"

namespace android {

static Mutex sMutex;

static void EleRadar_start(JNIEnv *env, jobject clazz) {
	_writedev(ELE_RADAR_PATH, "1", 4);
}

static void EleRadar_stop(JNIEnv *env, jobject clazz) {
	_writedev(ELE_RADAR_PATH, "0", 4);
}

static jboolean EleRadar_isAlive(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);

	int len;
	char buffer[4];
	buffer[0] = '0';
	len = _readdev(ELE_RADAR_PATH, buffer, 4);
	if (len >= 0) buffer[len] = '\0';

	if (DEBUG) ALOGI("EleRadar_isAlive() result=%s", buffer);

	return (buffer[0] == '1') ? true : false;
}

static JNINativeMethod method_table[] = {
		{ "start", "()V", (void*) EleRadar_start },
		{ "stop", "()V", (void*) EleRadar_stop },
		{ "isAlive", "()Z", (void*) EleRadar_isAlive },
};

int register_fourtech_hardware_EleRadar(JNIEnv *env) {
	return jniRegisterNativeMethods(env, ELE_RADAR_CLASS, method_table, NELEM(method_table));
}

}
