package com.fourtech.hardware;

/**
 * LCD(Liquid Crystal Display)
 */
public class Display {

	static {
		init_native();
	}

	/**
	 * @deprecated 设置背光 [0, 255]
	 */
	@Deprecated
	public native static void setBacklight(int brightness);

	/**
	 * 获取背光 [0, 255]
	 */
	public native static int getBacklight();

	/**
	 * 初始化
	 */
	public native static void init_native();

	/**
	 * 设置背光 [0, 255]
	 */
	public native static void setBacklightBrightness(int brightness);

}