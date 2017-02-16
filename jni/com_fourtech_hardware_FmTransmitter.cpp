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

#include "FmTransmitter.h"

#define DISPLAY_CLASS  "com/fourtech/hardware/FmTransmitter"

namespace android {

static Mutex sMutex;

static FmTransmitter myFmTransmitter;

/*
 * Class:     FmTransmitter_setPowerOn
 * Method:    PowerOn
 * Signature: ()Z
 */
static jboolean FmTransmitter_setPowerOn(JNIEnv *evn, jclass clazz) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.Power_on();
}

/*
 * Class:     FmTransmitter_setPowerOff
 * Method:    PowerOff
 * Signature: ()Z
 */
static jboolean FmTransmitter_setPowerOff(JNIEnv *, jclass clazz) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.Power_off();
}

/*
 * Class:     FmTransmitter_setFrequency
 * Method:    SetFreq
 * Signature: (F)Z
 */
static jboolean FmTransmitter_setFrequency(JNIEnv *, jclass clazz, jfloat f) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.Set_Freq(f * 100);
}

/*
 * Class:     FmTransmitter_setVol
 * Method:    VolSet
 * Signature: (I)Z
 */
static jboolean FmTransmitter_setVol(JNIEnv *, jclass clazz, jint p) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.VolSet(p);
}

/*
 * Class:     FmTransmitter_setSignaln
 * Method:    SignSet
 * Signature: (I)Z
 */
static jboolean FmTransmitter_setSignaln(JNIEnv *, jclass clazz, jint p) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.SignSet(p);
}

/*
 * Class:     FmTransmitter_setDivergence
 * Method:    PaCtl
 * Signature: (I)Z
 */
static jboolean FmTransmitter_setDivergence(JNIEnv *, jclass clazz, jint p) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.PaCtl(p);
}

/*
 * Class:     FmTransmitter_getNowMode
 * Method:    CurStatu
 * Signature: ()I
 */
static jint FmTransmitter_getNowMode(JNIEnv *, jclass clazz) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.CurStatus();
}

/*
 * Class:     com_softwinner_fmfire_activity_Tools
 * Method:    ChipId
 * Signature: ()I
 */
static jint FmTransmitter_getChipId(JNIEnv *, jclass clazz) {
	Mutex::Autolock autoLock(sMutex);
	return myFmTransmitter.ChipId();
}

static JNINativeMethod method_table[] = {
		{ "setPowerOn", "()Z", (void*) FmTransmitter_setPowerOn },
		{ "setPowerOff", "()Z", (void*) FmTransmitter_setPowerOff },
		{ "setFrequency", "(F)Z", (void*) FmTransmitter_setFrequency },
		{ "setVol", "(I)Z", (void*) FmTransmitter_setVol },
		{ "setSignaln", "(I)Z", (void*) FmTransmitter_setSignaln },
		{ "setDivergence", "(I)Z", (void*) FmTransmitter_setDivergence },
		{ "getNowMode", "()I", (void*) FmTransmitter_getNowMode },
		{ "getChipId", "()I", (void*) FmTransmitter_getChipId },
};

int register_fourtech_hardware_FmTransmitter(JNIEnv *env) {
	return jniRegisterNativeMethods(env, DISPLAY_CLASS, method_table, NELEM(method_table));
}

}
