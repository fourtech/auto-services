package com.fourtech.mcu;

/** @hide */
public interface McuConstant {
	// Heads
	byte HEAD55 = (byte) 0x55;
	byte HEADA0 = (byte) 0xa0;

	// mcu version
	byte GID_VERSION = (byte) 0x04;
	byte SID_VERSION = (byte) 0x03;
	// mcu key
	byte GID_KEY = (byte) 0x06;
	byte SID_KEY = (byte) 0x03;
	// cpu temp 
	byte GID_CPU_TEMP = (byte) 0x08;
	byte SID_CPU_TEMP = (byte) 0x01;
	// mpu headbeat
	byte GID_HEADBEAT = (byte) 0x09;
	byte SID_HEADBEAT = (byte) 0x01;
	byte SID_SHUTDOWN = (byte) 0x80;

	// mcu Obd
	byte GID_POWER_REASON = (byte) 0x13;
	byte SID_POWER_REASON= (byte) 0x01;
	
	// mcu Obd
	byte GID_OBD = (byte) 0x30;
	byte SID_OBD = (byte) 0x01;

	// mcu Gps
	byte GID_GPS = (byte) 0x31;
	byte SID_GPS = (byte) 0x01;
	// mcu Poweroff time
	byte GID_POWEROFF = (byte) 0x50;
	byte SID_POWEROFF = (byte) 0x01;

	// mcu Poweroff voltage
	byte GID_POWEROFF_VOLTAGE = (byte) 0x53;
	byte SID_POWEROFF_VOLTAGE= (byte) 0x01;
	
	// mcu upgrade
	byte GID_UPGRADE = (byte) 0x80;
	byte SID_UPGRADE = (byte) 0x08;

	// Mcu State
	byte GID_MCU_STATE = (byte) 0x90;
	byte SID_MCU_AUTOSTATE = (byte) 0X01;

}