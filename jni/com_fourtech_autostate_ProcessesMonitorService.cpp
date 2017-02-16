#define LOG_TAG "TopProcessesMonitorService-jni"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <utils/misc.h>
#include <utils/Log.h>
#include <stdio.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>

#define DEBUG                           (0)
#define PROCESSES_MONITOR_SERVICE_CLASS "com/fourtech/autostate/ProcessesMonitorService"
#define PROCESS_INFO_CLASS              "com/fourtech/autostate/ProcessesMonitorService$ProcessInfo"

namespace android {

extern "C" {
#include "ProcessesMonitor.h"
extern void init_procs(void);
extern int read_procs_out(struct proc_info **old_procs_out, struct proc_info **new_procs_out);
extern struct proc_info *find_old_proc(pid_t pid, pid_t tid);
extern void free_old_procs(void);
}

static Mutex sMutex;

static jclass gArrayListClass;

static struct {
	jmethodID cstor;
	jmethodID add;
} gArrayListMethods;

static jclass gProcessInfoClass;
static jmethodID gProcessInfoCstor;

static jmethodID gHandldProcessInfoListMethodID;

static void ProcessesMonitorService_init(JNIEnv* env, jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	ALOGI("init()");
	jclass processesMonitorServiceClass = env->FindClass(
			PROCESSES_MONITOR_SERVICE_CLASS);

	if (processesMonitorServiceClass == NULL) {
		ALOGE("Class %s not found", PROCESSES_MONITOR_SERVICE_CLASS);
	} else {
		gHandldProcessInfoListMethodID = env->GetMethodID(
				processesMonitorServiceClass,
				"handldProcessInfoList",
				"(Ljava/util/ArrayList;)V");

		jclass processInfoClass = env->FindClass(PROCESS_INFO_CLASS);
		gProcessInfoClass = (jclass) env->NewGlobalRef(processInfoClass);
		gProcessInfoCstor = env->GetMethodID(
				processInfoClass,
				"<init>",
				"(IIICIIILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

		jclass arrayListClass = env->FindClass("java/util/ArrayList");
		gArrayListClass = (jclass) env->NewGlobalRef(arrayListClass);
		gArrayListMethods.cstor = env->GetMethodID(
				arrayListClass,
				"<init>",
				"()V");
		gArrayListMethods.add = env->GetMethodID(
				arrayListClass,
				"add",
				"(Ljava/lang/Object;)Z");
	}

	init_procs();
}

static void ProcessesMonitorService_takeProcessInfoList(JNIEnv* env,
		jobject obj) {
	Mutex::Autolock autoLock(sMutex);
	jobject pis = env->NewObject(gArrayListClass, gArrayListMethods.cstor);

	// do read
	read_procs();
	print_procs(env, obj, pis);

	env->CallVoidMethod(obj, gHandldProcessInfoListMethodID, pis);

	free_old_procs();
	if (DEBUG) ALOGI("ProcessesMonitorService_takeProcessInfoList()");
}

static JNINativeMethod method_table[] = {
		{ "native_init", "()V", (void*) ProcessesMonitorService_init },
		{ "takeProcessInfoList", "()V", (void*) ProcessesMonitorService_takeProcessInfoList },
};

int register_fourtech_autostate_ProcessesMonitorService(JNIEnv *env) {
	return jniRegisterNativeMethods(env, PROCESSES_MONITOR_SERVICE_CLASS, method_table, NELEM(method_table));
}

}
