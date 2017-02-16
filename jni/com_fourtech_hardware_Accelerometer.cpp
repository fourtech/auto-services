#define LOG_TAG "Accelerometer-jni"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <stdio.h>
#include <time.h>
#include <signal.h>
#include <pthread.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <utils/Log.h>
#include <linux/ioctl.h>

#define DEBUG           (1)
#define ASENSOR_PATH    "/dev/mma8452_daemon"
#define ELE_RADAR_CLASS "com/fourtech/hardware/Accelerometer"

#define GSENSOR_IOCTL_MAGIC                     'a'
#define GSENSOR_IOCTL_SET_SENSITIVITY           _IOW(GSENSOR_IOCTL_MAGIC, 0x11, char)

namespace android {

static Mutex sMutex;

static jboolean Accelerometer_setWakeUpSensitivity(JNIEnv* env, jobject obj, jint sen) {
	Mutex::Autolock autoLock(sMutex);

	int fd = -1;
	if (0 > (fd = open(ASENSOR_PATH, O_RDONLY))) {
		ALOGE("failed to open acc file '%s', error is '%s'", ASENSOR_PATH, strerror(errno));
		return false;
	}

	char sensitivity = (sen&0xFF);
	if (0 > ioctl(fd, GSENSOR_IOCTL_SET_SENSITIVITY, &sensitivity)) {
		ALOGE("Failed to set sensitivity; error is '%s'", strerror(errno));
		return false;
	}

	close(fd);

	if (DEBUG) ALOGI("Accelerometer_setSensitivity( %d )", sensitivity);
	return true;
}

static JNINativeMethod method_table[] = {
		{ "setWakeUpSensitivity", "(I)Z", (void*) Accelerometer_setWakeUpSensitivity },
};

int register_fourtech_hardware_Accelerometer(JNIEnv *env) {
	return jniRegisterNativeMethods(env, ELE_RADAR_CLASS, method_table, NELEM(method_table));
}

}
