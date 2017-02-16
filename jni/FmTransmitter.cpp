#define TAG "FmTransmitter-jni"

#include "jni.h"
#include "JNIHelp.h"
#include "android_runtime/AndroidRuntime.h"

#include <stdlib.h>
#include <utils/Log.h>
#include <fcntl.h>
#include <errno.h>
#include <dirent.h>
#include <math.h>
#include <poll.h>

#include "FmTransmitter.h"

int rda5820 = 1;

namespace android {

static int get_sysfs_attr(char *class_path, const char *attr, char *value,
		int len) {
	char path[256];
	int fd;

	if (class_path == NULL || *class_path == '\0'
			|| attr == NULL || value == NULL
			|| len < 1) {
		return -EINVAL;
	}

	snprintf(path, sizeof(path), "%s/%s", class_path, attr);
	path[sizeof(path) - 1] = '\0';
	fd = open(path, O_RDONLY);
	if (fd < 0) {
		return -errno;
	}
	if (read(fd, value, len) < 0) {
		close(fd);
		return -errno;
	}
	close(fd);

	return 0;
}

FmTransmitter::FmTransmitter() {
	ALOGI("enter %s", __FUNCTION__);
	fd = -1;
}
FmTransmitter::~FmTransmitter() {
	ALOGI("enter %s", __FUNCTION__);
	if (fd > 0) close(fd);
}
bool FmTransmitter::Power_on() {
	Power_off();
	ALOGI("enter %s", __FUNCTION__);
	if (rda5820) {
		if (fd > 0) {
			ALOGW("%s is already open", FM_DIR_5820);
			return 1;
		}
		fd = open(FM_DIR_5820, O_RDONLY);
		if (fd < 0) {
			ALOGE( TAG, "Unable to open  %s", FM_DIR_5820);
			return 0;
		}
	} else {
		if (fd > 0) {
			ALOGW("%s is already open", FM_DIR_8027);
			return 1;
		}
		fd = open(FM_DIR_8027, O_RDONLY);
		if (fd < 0) {
			ALOGE( TAG, "Unable to open  %s", FM_DIR_8027);
			return 0;
		}
	}
	return 1;
}

bool FmTransmitter::Power_off() {
	ALOGI("enter %s", __FUNCTION__);
	if (fd) close(fd);
	fd = -1;
	return 1;
}

bool FmTransmitter::Set_Freq(float freq) {
	ALOGI("enter %s", __FUNCTION__);

	if (fd < 0)
		return 0;

	if (rda5820) {
		if (ioctl(fd, FreqSet, (int) freq) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_5820);
			return 0;
		}
	} else {
		if (ioctl(fd, FreqSet, (int) freq) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_8027);
			return 0;
		}
	}
	return 1;

}

int FmTransmitter::CurStatus() {
	int bytes;
	char buffer[4];

	ALOGI("enter %s", __FUNCTION__);
	if (rda5820) {
		get_sysfs_attr(FM_ATTR_PATH_5820, "status", buffer, 4);
	} else {
		get_sysfs_attr(FM_ATTR_PATH_8027, "status8027", buffer, 4);
	}
	bytes = atoi(buffer);
	ALOGI("lever %s bytes:%d\n", __FUNCTION__, bytes);
	return bytes;
}

int FmTransmitter::ChipId() {
	int bytes;
	char buffer[4];

	ALOGI("---enter %s", __FUNCTION__);
	get_sysfs_attr(FM_ATTR_PATH_8027, "chipidqn8027", buffer, 4);
	bytes = atoi(buffer);
	ALOGI("--lever %s bytes:%d\n", __FUNCTION__, bytes);
	if (bytes != 0) {
		ALOGI("------bytes != 0\n");
		rda5820 = 0;
	}
	if (bytes == 0) {
		ALOGI("------bytes == 0\n");
	}
	return bytes;
}

bool FmTransmitter::VolSet(int vol) {
	ALOGI("enter %s", __FUNCTION__);
	if (fd < 0)
		return 0;

	if (rda5820) {
		if (ioctl(fd, SetVolume, vol) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_5820);
			return 0;
		}
	} else {
		if (ioctl(fd, SetVolume, vol) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_8027);
			return 0;
		}
	}
	return 1;
}

bool FmTransmitter::SignSet(int sig) {
	ALOGI("enter %s", __FUNCTION__);
	if (fd < 0)
		return 0;

	if (rda5820) {
		if (ioctl(fd, SigGain, sig) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_5820);
			return 0;
		}
	} else {
		if (ioctl(fd, SigGain, sig) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_8027);
			return 0;
		}
	}
	return 1;
}

bool FmTransmitter::PaCtl(int p) {
	ALOGI("enter %s", __FUNCTION__);
	if (fd < 0)
		return 0;

	if (rda5820) {
		if (ioctl(fd, PaGain, p) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_5820);
			return 0;
		}
	} else {
		if (ioctl(fd, PaGain, p) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_8027);
			return 0;
		}
	}

	return 1;
}

bool FmTransmitter::mute(int p) {
	ALOGI("enter %s", __FUNCTION__);
	if (fd < 0)
		return 0;

	if (rda5820) {
		if (ioctl(fd, Mute, p) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_5820);
			return 0;
		}
	} else {
		if (ioctl(fd, Mute, p) < 1) {
			ALOGE( TAG, "ioctl err %s", FM_DIR_8027);
			return 0;
		}
	}

	return 1;
}


int main(int argc, char *argv[]) {
	int cmd1, cmd2;
	FmTransmitter myFmTransmitter;

	if (argc < 3) {
		printf("argc < 3!\n");
		return -1;
	}

	cmd1 = atoi(argv[1]);
	cmd2 = atoi(argv[2]);

	printf("argv[1]:%d argv[2]:%d", cmd1, cmd2);

	myFmTransmitter.Power_on();

	if (cmd1 == 1)
		myFmTransmitter.Set_Freq(cmd2);
	else if (cmd1 == 2)
		myFmTransmitter.VolSet(cmd2);
	else if (cmd1 == 3)
		myFmTransmitter.SignSet(cmd2);
	else if (cmd1 == 4)
		myFmTransmitter.PaCtl(cmd2);
	else if (cmd1 == 5)
		myFmTransmitter.mute(cmd2);

	myFmTransmitter.Power_off();
	return 0;
}

}
