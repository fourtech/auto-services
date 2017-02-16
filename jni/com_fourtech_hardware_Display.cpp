#define LOG_TAG "Display-jni"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>

#include <hardware/hardware.h>
#include <hardware/lights.h>

#include "DeviceUtils.h"

#define DEBUG          (1)
#define BACKLIGHT_PATH "/sys/class/leds/lcd-backlight/brightness"
#define DISPLAY_CLASS  "com/fourtech/hardware/Display"

#define LIGHT_FLASH_NONE     0
#define LIGHT_FLASH_TIMED    1
#define LIGHT_FLASH_HARDWARE 2

#define BRIGHTNESS_MODE_USER   0
#define BRIGHTNESS_MODE_SENSOR 1

namespace android {

static Mutex sMutex;
static light_device_t* sBacklight;

static void Display_setBacklight(JNIEnv *env, jobject clazz, jint brightness) {
	char buffer[8];
	buffer[0] = '0';
	sprintf(buffer, "%d", brightness);
	_writedev(BACKLIGHT_PATH, buffer, 8);
	if (DEBUG) ALOGI("Display_setBacklight( %s )", buffer);
}

static jint Display_getBacklight(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);

	int result, len;
	char buffer[8];
	buffer[0] = '0';
	len = _readdev(BACKLIGHT_PATH, buffer, 8);
	if (len >= 0) buffer[len] = '\0';
	result = atoi(buffer);

	if (DEBUG) ALOGI("Display_getBacklight() result=%d", result);

	return result;
}

static void Display_init_native(JNIEnv *env, jobject clazz) {
	int err;
	hw_module_t* module;

	err = hw_get_module(LIGHTS_HARDWARE_MODULE_ID, (hw_module_t const**) &module);
	if (err == 0) {
		hw_device_t* device;
		err = module->methods->open(module, LIGHT_ID_BACKLIGHT, &device);
		if (err == 0) {
			sBacklight = (light_device_t*) device;
		} else {
			sBacklight = NULL;
		}
	}

	if (DEBUG) ALOGI("Display_init_native() %s", (sBacklight != NULL ? "success" : "failed"));
}

static void Display_setBacklightBrightness(JNIEnv *env, jobject clazz,
		jint brightness) {
	int color;
	light_state_t state;

	if (sBacklight == NULL) {
		return;
	}

	color = brightness & 0x000000ff;
	color = 0xff000000 | (color << 16) | (color << 8) | color;

	memset(&state, 0, sizeof(light_state_t));
	state.color = color;
	state.flashMode = LIGHT_FLASH_NONE;
	state.flashOnMS = 0;
	state.flashOffMS = 0;
	state.brightnessMode = BRIGHTNESS_MODE_USER;

	{
		if (DEBUG) ALOGI("Display_setBacklightBrightness( %02x )", brightness);
		sBacklight->set_light(sBacklight, &state);
	}
}

static JNINativeMethod method_table[] = {
		{ "setBacklight", "(I)V", (void*) Display_setBacklight },
		{ "getBacklight", "()I", (void*) Display_getBacklight },
		{ "init_native", "()V", (void*) Display_init_native },
		{ "setBacklightBrightness", "(I)V", (void*) Display_setBacklightBrightness },
};

int register_fourtech_hardware_Display(JNIEnv *env) {
	return jniRegisterNativeMethods(env, DISPLAY_CLASS, method_table, NELEM(method_table));
}

}
