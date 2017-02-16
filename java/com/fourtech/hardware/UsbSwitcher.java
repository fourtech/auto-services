package com.fourtech.hardware;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;

import android.util.Slog;

public class UsbSwitcher {
	private static final String TAG = "UsbSwitcher";

	public static final int MODE_HOST = 0;
	public static final int MODE_NONE = 1;
	public static final int MODE_DEVICE = 2;

	private static final String USB_MODE_PATH = "/sys/kernel/debug/intel_otg/mode";
	private static final String USB_MODE_HOST = "host";
	private static final String USB_MODE_NONE = "none";
	private static final String USB_MODE_DEVICE = "peripheral";

	public void switchMode(int mode) {
		String modeStr = modeToString(mode);
		Slog.i(TAG, "switchMode( mode=" + modeStr + " ) ");

		FileWriter modeSwitcher = null;
		try {
			modeSwitcher = new FileWriter(USB_MODE_PATH, false);
			modeSwitcher.write(modeStr);
			modeSwitcher.flush();

			// wait 1 second
			Thread.sleep(1000);
		} catch (Throwable t) {
			Slog.i(TAG, "switchMode( mode=" + modeStr + " ) failed", t);
		} finally {
			if (modeSwitcher != null) {
				try {
					modeSwitcher.close();
				} catch (Throwable tt) {
				}
			}
		}
	}

	public int getMode() {
		String modeStr = null;
		BufferedReader modeReader = null;

		try {
			modeReader = new BufferedReader(new FileReader(USB_MODE_PATH));
			while ((modeStr = modeReader.readLine()) != null) {
				break;
			}
		} catch (Throwable t) {
			Slog.i(TAG, "getMode() failed", t);
		} finally {
			if (modeReader != null) {
				try {
					modeReader.close();
				} catch (Throwable tt) {
				}
			}
		}

		Slog.i(TAG, "getMode() mode=" + modeStr);
		return modeStringToInt(modeStr);
	}

	public String modeToString(int mode) {
		switch (mode) {
		case MODE_HOST: return USB_MODE_HOST;
		case MODE_NONE: return USB_MODE_NONE;
		case MODE_DEVICE: return USB_MODE_DEVICE;
		}
		return USB_MODE_DEVICE;
	}

	public int modeStringToInt(String modeStr) {
		if (USB_MODE_HOST.equals(modeStr)) return MODE_HOST;
		if (USB_MODE_NONE.equals(modeStr)) return MODE_NONE;
		if (USB_MODE_DEVICE.equals(modeStr)) return MODE_DEVICE;
		return -1;
	}

}