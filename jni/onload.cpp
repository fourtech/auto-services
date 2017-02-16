#define LOG_TAG "AutoServer-jni-loader"

#include "JNIHelp.h"
#include "jni.h"
#include "utils/Log.h"
#include "utils/misc.h"

namespace android {
int register_fourtech_hardware_Display(JNIEnv *env);
int register_fourtech_hardware_EleRadar(JNIEnv *env);
int register_fourtech_hardware_FmTransmitter(JNIEnv *env);
int register_fourtech_hardware_Accelerometer(JNIEnv *env);
int register_fourtech_autostate_AutoStateService(JNIEnv *env);
// int register_fourtech_autostate_ProcessesMonitorService(JNIEnv *env);
}

using namespace android;

extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved) {
	JNIEnv* env = NULL;
	jint result = -1;

	if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		ALOGE("GetEnv failed!");
		return result;
	}

	register_fourtech_hardware_Display(env);
	register_fourtech_hardware_EleRadar(env);
	register_fourtech_hardware_Accelerometer(env);
	register_fourtech_autostate_AutoStateService(env);
	register_fourtech_hardware_FmTransmitter(env);
	// register_fourtech_autostate_ProcessesMonitorService(env);

	return JNI_VERSION_1_4;
}
