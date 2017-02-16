package com.fourtech.hardware;

/**
 * 重量加速器
 */
public class Accelerometer {
	/**
	 * 设置G-Sensor待机唤醒灵敏度
	 * @param sen 灵敏度，取值范围：[0, 255]
	 * @return 是否设置成功
	 */
	public native static boolean setWakeUpSensitivity(int sen);
}