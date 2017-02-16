#define LOG_TAG "DeviceUtils-jni"

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

namespace android {

int _readdev(const char* dev, char* buf, int len) {
	int fd = -1;
	int ret = -1;

	if (0 > (fd = open(dev, O_RDONLY))) {
		ALOGE("_readdev() Failed to open device file '%s', error is '%s'", dev, strerror(errno));
		return -1;
	}

	ret = read(fd, buf, len);
	close(fd);

	if (0 > ret) {
		ALOGE("_readdev() Failed read device file '%s', error is '%s'", dev, strerror(errno));
		return -1;
	}

	return ret;
}

int _writedev(const char* dev, char* buf, int len) {
	int fd = -1;
	int ret = -1;

	if (0 > (fd = open(dev, O_WRONLY))) {
		ALOGE("_writedev( %s ) Failed to open device file '%s', error is '%s'", buf, dev, strerror(errno));
		return -1;
	}

	ret = write(fd, buf, len);
	close(fd);

	if (0 > ret) {
		ALOGE("_writedev() Failed write device file '%s', error is '%s'", dev, strerror(errno));
		return -1;
	}

	return ret;
}

}
