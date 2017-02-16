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

#define FM_DIR_5820       "/dev/fm"
#define FM_DIR_8027       "/dev/qn"
#define FM_ATTR_PATH_5820 "/sys/class/fm_dev/fm/device"
#define FM_ATTR_PATH_8027 "/sys/class/qn_dev/qn/device"

#define FreqSet       0x1110
#define PaGain        0x1101
#define SigGain       0x1011
#define SetVolume     0x0111
#define CurrentStatus 0x1111
#define Mute          0x1100

#define FMDENUG 1

namespace android {

class FmTransmitter {
public:
	FmTransmitter();
	~FmTransmitter();

	bool Power_on(void);
	bool Power_off(void);
	bool Set_Freq(float);
	int CurStatus(void);
	int ChipId(void);
	bool VolSet(int vol);
	bool SignSet(int sig);
	bool PaCtl(int p);
	bool mute(int p);
	int fd;
};

}
