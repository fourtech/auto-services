package com.fourtech.mcu;

public interface KeyCode {

	final public static int K_MUTE = (byte) 0;
	final public static int K_EJECT = (byte) 1;
	final public static int K_VOLUME_DEC = (byte) 2;
	final public static int K_VOLUME_INC = (byte) 3;
	final public static int K_SOURCE = (byte) 4;
	final public static int K_PAUSE = (byte) 5;
	final public static byte K_DISP = (byte) 6;
	final public static byte K_AUDIO_SEL = (byte) 7;
	final public static byte K_1_DISC = (byte) 8;
	final public static byte K_2_DISC = (byte) 9;
	final public static byte K_3_SCAN = (byte) 10;
	final public static byte K_4_REP = (byte) 11;
	final public static byte K_5_RDM = (byte) 12;
	final public static byte K_6_STOP = (byte) 13;

	final public static byte K_PS1 = (byte) 14;
	final public static byte K_PS2 = (byte) 15;
	final public static byte K_PS3 = (byte) 16;
	final public static byte K_PS4 = (byte) 17;
	final public static byte K_PS5 = (byte) 18;
	final public static byte K_PS6 = (byte) 19;
	final public static byte K_STEP_UP = (byte) 20;
	final public static byte K_STEP_DOWN = (byte) 21;
	final public static byte K_LOC_DX = (byte) 22;

	final public static byte K_OPEN = (byte) 23;
	final public static byte K_WIDE = (byte) 24;
	final public static byte K_ANGLE_DEC = (byte) 25;
	final public static byte K_ANGLE_INC = (byte) 26;
	final public static byte K_UP = (byte) 27;
	final public static byte K_DOWN = (byte) 28;
	final public static byte K_LEFT = (byte) 29;
	final public static byte K_RIGHT = (byte) 30;
	final public static byte K_ENTER = (byte) 31;

	final public static byte K_POWER = (byte) 32;
	final public static byte K_P_MODE = (byte) 33;
	final public static byte K_SETUP = (byte) 34;
	final public static byte K_PICTUR = (byte) 35;
	final public static byte K_STOP = (byte) 36;
	final public static byte K_GOTO = (byte) 37;
	final public static byte K_0 = (byte) 38;
	final public static byte K_1 = (byte) 39;
	final public static byte K_2 = (byte) 40;
	final public static byte K_3 = (byte) 41;
	final public static byte K_4 = (byte) 42;
	final public static byte K_5 = (byte) 43;
	final public static byte K_6 = (byte) 44;
	final public static byte K_7 = (byte) 45;
	final public static byte K_8 = (byte) 46;
	final public static byte K_9 = (byte) 47;
	final public static byte K_CLEAR = (byte) 48;

	final public static byte K_DVD_AUDIO = (byte) 49;
	final public static byte K_DVD_ANGLE = (byte) 50;
	final public static byte K_DVD_TITLE = (byte) 51;
	final public static byte K_DVD_S_TITLE = (byte) 52;
	final public static byte K_DVD_MENU = (byte) 53;
	final public static byte K_DVD_SETUP = (byte) 54;
	final public static byte K_DVD_SLOW = (byte) 55;
	final public static byte K_DVD_ZOOM = (byte) 56;
	final public static byte K_DISC_DEC = (byte) 57;
	final public static byte K_DISC_INC = (byte) 58;
	final public static byte K_TRACK_DEC = (byte) 59;
	final public static byte K_TRACK_INC = (byte) 60;
	final public static byte K_FAST_BACKWARD = (byte) 61;
	final public static byte K_FAST_FORWARD = (byte) 62;
	final public static byte K_SCAN = (byte) 63;
	final public static byte K_RANDOM = (byte) 64;
	final public static byte K_DVD_REPT = (byte) 65;
	final public static byte K_DVD_REPT_AB = (byte) 66;
	final public static byte K_AS = (byte) 67;
	final public static byte K_TV = (byte) 69;
	final public static byte K_DVD = (byte) 70;
	final public static byte K_TUNER = (byte) 71;
	final public static byte K_AUX_IN = (byte) 72;
	final public static byte K_DVDC = (byte) 73;
	final public static byte K_CAMERA = (byte) 74;
	final public static byte K_SD_CARD = (byte) 75;
	final public static byte K_STANDBY = (byte) 76;

	final public static byte K_BT = (byte) 77;
	final public static byte K_IPOD = (byte) 78;
	final public static byte K_IPOD_MUSIC = (byte) 79; // Switch to iPod GUI
														// interface

	final public static byte K_BAND = (byte) 81;
	final public static byte K_PIP = (byte) 82;
	final public static byte K_REAR = (byte) 83;

	final public static byte K_PLAY_PAUSE = (byte) 84;
	final public static byte K_DVD_RTN = (byte) 85;
	final public static byte K_SLOW_FORWARD = (byte) 86;
	final public static byte K_SLOW_BACKWARD = (byte) 87;
	final public static byte K_10 = (byte) 88;
	final public static byte K_ENCODER_UP = (byte) 89;
	final public static byte K_ENCODER_DOWN = (byte) 90;

	final public static int K_NAVI = (byte) 68;
	final public static int K_BACK = (byte) 91;
	final public static int K_HOME = (byte) 92;
	final public static int K_MENUMCU = (byte) 93;
	final public static int K_ANDROID = (byte) 95;
	final public static int K_HANGUP = (byte) 96;
	final public static int K_HANGDOWN = (byte) 97;

	final public static int K_EQ = (byte) 99;
	final public static int K_SPEECH = (byte) 100;
	final public static int K_STAR = (byte) 101; // *
	final public static int K_POUND = (byte) 102; // #

	final public static int K_BKTRIS = (byte) 103; 


	final public static int K_SCROLL_R = (byte) 104;
	final public static int K_SCROLL_L = (byte) 105;
	final public static int K_ASSISTANT = (byte) 106;
	final public static int K_ASSI_GPS = (byte) 107;
	final public static int K_KILL_SOURCE = (byte) 108;

	final public static int K_FAKE_POWER = (byte) 109; 

	final public static int K_MUTEONLY = (byte) 110;
	final public static int K_SD = (byte) 111;
	final public static int K_USB = (byte) 112;
	final public static int K_NEXT = (byte) 113;
	final public static int K_PREVIOUS = (byte) 114;
	final public static byte K_PS = (byte) 115;
	final public static byte K_RECORD= (byte) 116;
	final public static byte K_REVERSING= (byte) 117;
	final public static byte K_ALARM= (byte) 118;
}
