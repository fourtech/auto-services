package com.fourtech.hardware;

import static android.os.SystemProperties.getInt;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.UserHandle;
import android.util.Slog;

import com.fourtech.autostate.AutoStateService;


public class FmTransmitter {
	private static FmTransmitter sMe = null;
	private static final int RESUME_TIME = getInt("persist.sys.fm_resume_time", 1) * 1000;
	private Handler mHandler;

	private FmTransmitter() {
		mHandler = new Handler();
	}

	public static FmTransmitter get() {
		return (sMe != null ? sMe : (sMe = new FmTransmitter()));
	}

	/**
	 * 进入休眠状态
	 */
	public void goToSleep(final Context context) {
		if (getNowMode() == 1) {
			Intent fmt = new Intent("com.rk.fm.ContronService");
			fmt.setPackage("com.rk.fm");
			fmt.putExtra("CMD", false);
			context.startService(fmt);
		}
	}

	/**
	 * 唤醒
	 */
	public void wakeUp(final Context context) {
		if (sIsLastPowerOn && RESUME_TIME > 0) {
			Slog.i("FmTransmitter", "wakeUp()");
			mHandler.removeCallbacksAndMessages(null);
			// Do TTS job
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (sIsLastPowerOn
							&& (getNowMode() == 0)
							&& AutoStateService.get().isAccOn()) {
						// textToSpeech(context, "即将打开FM发射");
						Intent fmt = new Intent("com.rk.fm.ContronService");
						fmt.setPackage("com.rk.fm");
						fmt.putExtra("TTS_START", true);
						context.startService(fmt);
					}
				}
			}, RESUME_TIME);
			// Do resume job
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (sIsLastPowerOn
							&& (getNowMode() == 0)
							&& AutoStateService.get().isAccOn()) {
						Intent fmt = new Intent("com.rk.fm.ContronService");
						fmt.setPackage("com.rk.fm");
						fmt.putExtra("CMD", true);
						context.startService(fmt);
					}
				}
			}, RESUME_TIME + 4000);
		}
	}

	public void textToSpeech(Context context, String content) {
		// FIXME speech voice only
		context.sendBroadcastAsUser(
				new Intent("aios.intent.action.TO_SPEAK")
				.putExtra("aios.intent.extra.PRIORITY", 2)
				.putExtra("aios.intent.extra.TEXT", content),
				UserHandle.ALL);
	}

	/* 休眠前状态 */
	public static boolean sIsLastPowerOn = false;

	public native boolean setPowerOn();
	public native boolean setPowerOff();
	public native boolean setFrequency(float freq);
	public native boolean setVol(int vol);
	public native boolean setSignaln(int sig);
	public native boolean setDivergence(int div);
	public native int getNowMode();
	public native int getChipId();
}
