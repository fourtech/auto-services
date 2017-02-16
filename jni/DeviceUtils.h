#ifndef _DEVICE_UTILS_H
#define _DEVICE_UTILS_H

namespace android {
int _readdev(const char* dev, char* buf, int len);
int _writedev(const char* dev, char* buf, int len);
}

#endif // _DEVICE_UTILS_H
