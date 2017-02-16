package com.fourtech;

import static android.os.ServiceManager.addService;
import static com.fourtech.autostate.AutoState.AUTO_STATE_SERVICE;
import static com.fourtech.eleradar.EleRadarManager.RADAR_SERVICE;
import static com.fourtech.fm.FmTransmitter.FMT_SERVICE;
import static com.fourtech.gps.GpsService.GPS_SERVICE;
import static com.fourtech.mcu.McuManager.MCU_SERVICE;
import static com.fourtech.mcu.McuUpgradeManager.UPGRADE_SERVICE;
import static com.fourtech.obd.ObdManager.OBD_SERVICE;
import android.content.Context;
import android.util.Log;
import android.util.Slog;

import com.fourtech.autostate.AutoStateService;
import com.fourtech.eleradar.EleRadarService;
import com.fourtech.fm.FmTransmitterService;
import com.fourtech.gps.GpsService;
import com.fourtech.mcu.McuService;
import com.fourtech.mcu.McuUpgradeService;
import com.fourtech.obd.ObdService;

public class AutoServer {
	private static final boolean DEBUG = true;
	private static final String TAG = "auto-services";

	public static void startup(Context context) {
		// load auto service native library
		System.loadLibrary("autoservices_jni");

		try {
			if (DEBUG) Slog.i(TAG, "Adding service '" + AUTO_STATE_SERVICE + "'");
			addService(AUTO_STATE_SERVICE, new AutoStateService(context));
		} catch (Throwable t) {
			Slog.e(TAG, "Adding service '" + AUTO_STATE_SERVICE + "' failed", t);
		}

		try {
			if (DEBUG) Slog.i(TAG, "Adding service '" + RADAR_SERVICE + "'");
			addService(RADAR_SERVICE, new EleRadarService(context));
		} catch (Throwable t) {
			Slog.e(TAG, "Adding service '" + RADAR_SERVICE + "' failed", t);
		}

		try {
			if (DEBUG) Slog.i(TAG, "adding service '" + OBD_SERVICE + "'");
			addService(OBD_SERVICE, new ObdService(context));
		} catch (Throwable e) {
			Log.e(TAG, "adding service '" + OBD_SERVICE + "'failed", e);
		}

		try {
			if (DEBUG) Log.i(TAG, "adding service '" + UPGRADE_SERVICE + "'");
			addService(UPGRADE_SERVICE, new McuUpgradeService(context));
		} catch (Throwable e) {
			Log.e(TAG, "adding service '" + UPGRADE_SERVICE + "'failed", e);
		}

		try {
			if (DEBUG) Slog.i(TAG, "Adding service '" + MCU_SERVICE + "'");
			addService(MCU_SERVICE, new McuService(context));
		} catch (Throwable t) {
			Slog.e(TAG, "Adding service '" + MCU_SERVICE + "' failed", t);
		}

		try {
			if (DEBUG) Slog.i(TAG, "Adding service '" + FMT_SERVICE + "'");
			addService(FMT_SERVICE, new FmTransmitterService());
		} catch (Throwable t) {
			Slog.e(TAG, "Adding service '" + FMT_SERVICE + "' failed", t);
		}

		try {
			if (DEBUG) Slog.i(TAG, "Adding service '" + GPS_SERVICE + "'");
			addService(GPS_SERVICE, GpsService.get());
		} catch (Throwable t) {
			Slog.e(TAG, "Adding service '" + GPS_SERVICE + "' failed", t);
		}
	}

}