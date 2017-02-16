package com.fourtech.hardware;

import static android.os.SystemProperties.get;
import static android.os.SystemProperties.getBoolean;

/**
 * 电子雷达，用于监测流动测速等
 */
public class EleRadar {

	// 如果不可以则用作正负压控制
	public static final boolean ENABLED = getBoolean("hw.eleradar.enabled", false);

	// 风扇如果由cpu控制，则用于风扇控制
	public static /*final*/ String FAN_MODE = get("persist.sys.fan_mode", "");

	public static final String FAN_MODE_CPUCTL = "cpu_ctl";
	public static final String FAN_MODE_MCUCTL = "mcu_ctl";

	/**
	 * 开启监测
	 */
	public native static void start();

	/**
	 * 停止监测
	 */
	public native static void stop();

	/**
	 * 是否在工作状态
	 */
	public native static boolean isAlive();

	/**
	 * 进入休眠状态
	 */
	public static void goToSleep() {
		sIsAliveBeforeSleep = isAlive();
		stop();
	}

	/**
	 * 唤醒
	 */
	public static void wakeUp() {
		if (sIsAliveBeforeSleep || !ENABLED) {
			start();
		}
	}

	/* 休眠前状态 */
	private static boolean sIsAliveBeforeSleep = false;
}