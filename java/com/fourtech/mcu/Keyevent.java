package com.fourtech.mcu;

import static android.os.SystemProperties.getBoolean;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.util.Xml;
import android.view.KeyEvent;

import com.android.internal.util.XmlUtils;
import com.fourtech.autostate.AutoStateService;
import com.fourtech.utilities.Utilities;

public class Keyevent implements KeyCode {
	private static final String TAG = "Keyevent";
	private static final boolean DEBUG = getBoolean("persist.debug.key", false);
	private final Context mContext;
	private final Handler mKeyHandel;
	private final HandlerThread mWorkingThread;
	private final KEYMAP mPanelMaps = new KEYMAP(SystemProperties.getInt("ro.adck.tolerance", 8));
	private final int mIrMaps = SystemProperties.getInt("ro.adck.Irtolerance", 5);

	private final KEYMAP mIrlMaps = new KEYMAP(SystemProperties.getInt("ro.adck.tolerance", 0));
	private static final String Customid = SystemProperties.get("persist.sys.irkeycustomid", "724410");
	private static final boolean mNeedCustomid = getBoolean("persist.sys.needcustomid", false);
	private AudioManager mAudioManager;
	private boolean mIsKeymapLoaded = false;
	private boolean mIsIrKeymapLoaded = false;
	private long         mIsIrCustomCode;
	private boolean mPressed = false;
    private static final String NAME_CAMERA_PACKAGE = "com.rk.carrecorder";
    private static final String NAME_CAMERA_CLASS = "com.rk.carrecorder.CameraActivity";
    private static final String NAME_CAMERA_SERVICE = "com.rockchip.CameraCrashService";
    private static final String ACTION_RECORD_BG = "com.rockchip.CameraCrashService";
    private static final String ACTION_PHYSICAL_KEY = "con.fourtech.physicalKey";
    private static final String KEY_SELCET_APPNAME = "persist.sys.navipackage";
    private static final String KEY_SELECT_MAINACT = "act";
    private static final  String PREF_NAME = "pref-select-appName"	;
    private static final int mIrKey =12;
    private int mCallMax, mMusicMax, mRingMax, mAlamMax;// 最大音量
	public Keyevent(Context context, McuService mcu) {
		mContext = context;
		mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
		mWorkingThread = new HandlerThread("fourtech.Keyevent");
		mWorkingThread.start();
		mKeyHandel = new Handler(mWorkingThread.getLooper());
		
	    mMusicMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
	    mAlamMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
	    mRingMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING); 
	    mCallMax = mAudioManager .getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
	}

	static {
		populateKeycodeSymbolicNames();
	}

	/**
	 * Send a single key event.
	 * 
	 * @param event
	 *            is a string representing the keycode of the key event you want
	 *            to execute.
	 */
	private void sendKeyEvent(final int eventCode) {
		
		if((eventCode==KeyEvent.KEYCODE_VOLUME_DOWN)||(eventCode== KeyEvent.KEYCODE_VOLUME_UP)){
		    int vol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
		    if((eventCode==KeyEvent.KEYCODE_VOLUME_DOWN)&&(vol>0)){
		    	vol=vol-1;
		    }else if((eventCode==KeyEvent.KEYCODE_VOLUME_UP)&&(vol<mMusicMax)){
		    	vol=vol+1;
		    }
			mAudioManager.setStreamVolume( AudioManager.STREAM_MUSIC, vol,AudioManager. FLAG_SHOW_UI);
			mAudioManager.setStreamVolume(AudioManager.STREAM_RING, mRingMax * vol / mMusicMax, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE); 
			mAudioManager.setStreamVolume( AudioManager.STREAM_ALARM, mAlamMax * vol / mMusicMax, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
			mAudioManager.setStreamVolume( AudioManager.STREAM_VOICE_CALL, mCallMax * vol / mMusicMax, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);
            return;
		}
		new Thread(new Runnable() {
			public void run() {
				long now = SystemClock.uptimeMillis();
				KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
						eventCode, 0);
				KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP,
						eventCode, 0);
				InputManager.getInstance().injectInputEvent(down,
						InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
				InputManager.getInstance().injectInputEvent(up,
						InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH);
			}
		}).start();
	}
	private void startActivityByAction(String action) {
		Intent i = new Intent(action);
		i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		try {
			mContext.startActivity(i);
		} catch (Exception e) {
			Log.w(TAG, "startActivityByAction()", e);
		}
	}
	

	
	private void LaunchNavigation() {
		String  mCurrPkg =android.provider.Settings.System.getString(mContext.getContentResolver(), KEY_SELCET_APPNAME);
		String  mCurrCls =android.provider.Settings.System.getString(mContext.getContentResolver(), KEY_SELECT_MAINACT);
	//	Log.i(TAG, "LaunchNavigation mCurrPkg " + mCurrPkg+"LaunchNavigation mCurrCls " + mCurrCls);
		final String navipkg =mCurrPkg;
		if(!AutoStateService.get().filterInputEvent())
			return;
		if (navipkg != null && navipkg.trim().length() > 0) {
			ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
			List<RunningTaskInfo> taskInfo = am.getRunningTasks(2);
			if (!taskInfo.isEmpty()) {
				String topActivity = taskInfo.get(0).topActivity.getPackageName();
				Log.i(TAG, "LaunchNavigation topActivity " + topActivity);
				if (navipkg.contains(topActivity)) {
					am.moveTaskToFront(taskInfo.get(1).id, 0, null);
					Log.i(TAG, "LaunchNavigation move " + taskInfo.get(1).topActivity + " to front.");
					return;
				}
			}
		}

		startActivityByAction("com.fourtech.navigationselect.2");
	}

	private void startCameraRecorder() {
		if (!AutoStateService.get().filterInputEvent())
			return;
		Intent recorder = new Intent();
		recorder.setAction(ACTION_PHYSICAL_KEY);
		recorder.putExtra("keyStype", "AV");
		mContext.sendBroadcastAsUser(recorder, UserHandle.ALL);
	}

	private void startCamerarReversing() {
		if (!AutoStateService.get().filterInputEvent())
			return;
		Intent Reversing = new Intent();
		Reversing.setAction(ACTION_PHYSICAL_KEY);
		Reversing.putExtra("keyStype", "DVR");
		mContext.sendBroadcastAsUser(Reversing, UserHandle.ALL);
	}

	private int isSystemKey(byte key) {
		Log.d(TAG, "isSystemKey key=" + key);
		int k = 0;
		switch (key) {
		case K_VOLUME_DEC:
	  	  k = KeyEvent.KEYCODE_VOLUME_DOWN;
			break;
		case K_VOLUME_INC:
			k = KeyEvent.KEYCODE_VOLUME_UP;
			break;
		case K_ENTER:
			k = KeyEvent.KEYCODE_ENTER;
			break;
		case K_STAR:
			k = KeyEvent.KEYCODE_STAR;
			break;
		case K_POUND:
			k = KeyEvent.KEYCODE_POUND;
			break;
		case K_UP:
			k = KeyEvent.KEYCODE_DPAD_UP;
			break;
		case K_DOWN:
			k = KeyEvent.KEYCODE_DPAD_DOWN;
			break;
		case K_LEFT:
			k = KeyEvent.KEYCODE_DPAD_LEFT;
			break;
		case K_RIGHT:
			k = KeyEvent.KEYCODE_DPAD_RIGHT;
			break;
		case K_CLEAR:
			k = KeyEvent.KEYCODE_DEL;
			break;

		case K_BACK:
			if (mPressed)
				break;
			mPressed = true;
			k = KeyEvent.KEYCODE_BACK;
			break;
		case K_ALARM:
			if (mPressed)
				break;
			mPressed = true;
			Intent  Alarm = new Intent("auto.fourtech.alarm");
			Alarm.putExtra("alarm", true);
			mContext.sendBroadcastAsUser(Alarm, UserHandle.ALL);
			Log.d(TAG, "isendBroadcastAsUser        K_ALARM");

			break;
		case K_MENUMCU:
			if (mPressed)
				break;
			mPressed = true;
			k = KeyEvent.KEYCODE_MENU;
			break;

		case K_MUTE:
			if (mPressed)
				break;
			mPressed = true;
			int Vol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) ;
			mAudioManager.setStreamVolume( AudioManager.STREAM_MUSIC, Vol,AudioManager. FLAG_SHOW_UI);
			Intent  MasterMute = new Intent("auto.fourtech.mute");
			MasterMute.putExtra("mute", false);
			//mContext.sendBroadcast(MasterMute);
			mContext.sendBroadcastAsUser(MasterMute, UserHandle.ALL);
			break;

		case K_MUTEONLY:
			if (mPressed)
				break;
			mPressed = true;
			break;

		case K_POWER:
			if (mPressed)
				break;
			mPressed = true;
			k = KeyEvent.KEYCODE_POWER;
			break;
		case K_HOME:
			k = KeyEvent.KEYCODE_HOME;
			break;
		case K_NAVI: 
			if (mPressed)
				break;
			mPressed = true;
			LaunchNavigation();
			break;
		case K_RECORD:
			if (mPressed)
				break;
			startCameraRecorder();
			break;
		case K_REVERSING:
			if (mPressed)
				break;
			startCamerarReversing();
			break;
		case K_BT:
			if (mPressed)
				break;
			mPressed = true;
			break;
		case K_SOURCE:
			break;
		default:
			break;
		}
		return k;
	}

	private void onKeyDown(byte key) {
		int keyev = isSystemKey(key);
		if(DEBUG)Log.i(TAG, "onKeyDown     keyev=" + keyev + ", key="+ key);
		if (keyev > 0) {
			sendKeyEvent(keyev);
		}
	}
	
	/**
	 * Get  key pkt.
	 * @param  key_cmd
	 *            is a    keycode of the key event you get  to execute.
	 */
	
	public void onReceivedKey(byte[] pkt) {
		int key_ksrc = (pkt[3] & 0xff);
		int key_cmd = (pkt[4] & 0xff);
		int key_state = (pkt[5] & 0xff);
		if (DEBUG)Log.i(TAG, "onReceivedKey() cmd=" + key_cmd + ", state="+ key_state);
		if (mIsKeymapLoaded == false) {
			mIsKeymapLoaded = true;
			load();
		}
		if(mIsIrKeymapLoaded==false){
			mIsIrKeymapLoaded=true;
			loadIrKey();
		}
		if (key_state == 0) {
			mPressed = false;
			return;
		}
		if(key_ksrc==mIrKey){
			mPressed = false;
		}
		if((key_ksrc==mIrKey)&&(key_state<mIrMaps)){
			return;
		}
		int key;
		if (key_ksrc == mIrKey) {
			if (mNeedCustomid) {
				long customid;
				StringBuffer buf = new StringBuffer("");
				for (int i = 0; i <= 2; i++) {
					buf.append(String.format("%2d", pkt[6 + i]));
				}
				SystemProperties
						.set("hw.sys.irkey.getcustomid", buf.toString());
				if (!Customid.equals(buf.toString())) {
					Log.w(TAG, "!str.equals  customid==" + buf.toString()
							+ "Customid==" + Customid);
					return;
				}
			}
			key = mIrlMaps.GetIrKeyCode(key_ksrc, key_cmd);
			onKeyDown((byte) (key & 0xff));
		} else {
			key = mPanelMaps.GetKeyCode(key_ksrc, key_cmd);
			onKeyDown((byte) (key & 0xff));
		}
	}

	private void loadIrKey() {
		File file = new File("/system/etc/Irkeys.xml");
		if (!file.exists())
			file = new File("/system/etc/Irkeys.xml");
		if (file.exists())
			mIrlMaps.load(file);
	}
	
	private void load() {
		File file = new File("/system/etc/panelkeys.xml");
		if (!file.exists())
			file = new File("/system/etc/panelkeys.xml");
		if (file.exists())
			mPanelMaps.load(file);
	}

	private class KEYMAP {
		private final int TOLERANCE;
		private final ArrayList<LIST> KeyMap = new ArrayList<LIST>();

		public KEYMAP(int tolerance) {
			TOLERANCE = tolerance;
		}

		void load(File file) {
			FileReader reader = null;
			try {
				reader = new FileReader(file);
			} catch (FileNotFoundException e) {
				Log.w(TAG, "Couldn't find or open " + file);
				return;
			}
			try {
				XmlPullParser parser = Xml.newPullParser();
				parser.setInput(reader);
				XmlUtils.beginDocument(parser, "keymaps");

				while (true) {
					XmlUtils.nextElement(parser);
					if (parser.getEventType() == XmlPullParser.END_DOCUMENT) {
						break;
					}
					String name = parser.getName();
					if ("key".equals(name)) {
						String value = parser.getAttributeValue(null, "value");
						String kname = parser.getAttributeValue(null, "name");

						if ((value != null) && (kname != null)) {
							value = value.trim();
							String v[] = value.split(",");
							kname = kname.trim().toUpperCase();
							int adc = -1;
							try {
								if (v.length == 1)
									adc = Utilities.parseInt(value.trim());
								else
									adc = (Utilities.parseInt(v[0].trim()) << 16)
											+ Utilities.parseInt(v[1].trim());

							} catch (NumberFormatException e) {
								Log.e(TAG, "NumberFormatException:" + value);
								XmlUtils.skipCurrentTag(parser);
								continue;
							}
							KeyMap.add(new LIST(adc, getkvol(kname)));
							if (DEBUG)
								Log.i(TAG, "KeyMap  adc " + adc + ", name="
										+ kname);
						}
					}

					XmlUtils.skipCurrentTag(parser);
				}
				reader.close();

			} catch (XmlPullParserException e) {
				Log.w(TAG, "Got execption remote keylayout.", e);
			} catch (IOException e) {
				Log.w(TAG, "Got execption remote keylayout.", e);
			}
		}

		public int GetIrKeyCode(int ch, int adc) {
			adc &= 0xffff;
			int akey =  adc;
			int tolrance = TOLERANCE;

			for (int i = 0; i < KeyMap.size(); i++) {
				int rmkey = KeyMap.get(i).adc();
			//	Log.i(TAG, "KeyMap  rmkey " + rmkey + ", akey="	+ akey);
				if (rmkey== akey)
					return KeyMap.get(i).keycode() & 0xff;
			}
			return -1;
		}
		
		
		public int GetKeyCode(int ch, int adc) {
			adc &= 0xffff;
			int akey = (ch << 16) + adc;
			int tolrance = TOLERANCE;

			for (int i = 0; i < KeyMap.size(); i++) {
				int rmkey = KeyMap.get(i).adc();
				if (((rmkey + tolrance) >= akey)
						&& ((rmkey - tolrance) <= akey))
					return KeyMap.get(i).keycode() & 0xff;
			}
			return -1;
		}

		private class LIST {
			private int adc;
			private int lkey;

			public LIST(int adc, int lkey) {
				LIST.this.adc = adc;
				LIST.this.lkey = lkey;
			}

			public int adc() {
				return LIST.this.adc;
			}

			public int keycode() {
				return LIST.this.lkey;
			}
		}
	}

	private static SparseArray<String> KEYCODE_SYMBOLIC_NAMES;

	private static void populateKeycodeSymbolicNames() {
		KEYCODE_SYMBOLIC_NAMES = new SparseArray<String>();
		SparseArray<String> names = KEYCODE_SYMBOLIC_NAMES;
		names.append(K_MUTE, "MUTE");
		names.append(K_MENUMCU, "MENU");
		names.append(K_NAVI, "GPS");
		names.append(K_BAND, "FM/AM");
		names.append(K_DVD, "DVD");
		names.append(K_HOME, "HOME");
		names.append(K_VOLUME_INC, "VOL+");
		names.append(K_VOLUME_DEC, "VOL-");
		names.append(K_HANGUP, "HANGUP");
		names.append(K_HANGDOWN, "HANG_DOWN");
		names.append(K_EJECT, "EJECT");
		names.append(K_BT, "BT");
		names.append(K_ENTER, "ENTER");
		names.append(K_PLAY_PAUSE, "PLAY/PAUSE");
		names.append(K_STOP, "STOP");
		names.append(K_EQ, "EQ");
		names.append(K_ANDROID, "ANDROID");
		names.append(K_AS, "AS");
		names.append(K_PS, "PS");
		names.append(K_BACK, "BACK");
		names.append(K_FAST_BACKWARD, "FAST_BACKWARD");
		names.append(K_FAST_FORWARD, "FAST_FORWARD");
		names.append(K_NEXT, "NEXT");
		names.append(K_PREVIOUS, "PREV");
		names.append(K_RANDOM, "RANDOM");
		names.append(K_4_REP, "REPEAT");
		names.append(K_SCAN, "SCAN");
		names.append(K_STANDBY, "BKLIGHT");
		names.append(K_SPEECH, "SPEECH");
		names.append(K_BKTRIS, "BKPINGPONG");
		names.append(K_SOURCE, "SOURCE");
		names.append(K_UP, "UP");
		names.append(K_DOWN, "DOWN");
		names.append(K_LEFT, "LEFT");
		names.append(K_RIGHT, "RIGHT");
		names.append(K_DVD_S_TITLE, "TITLE");
		names.append(K_DVD_ZOOM, "ZOOM");
		names.append(K_DVD_ANGLE, "ANGLE");
		names.append(K_DVD_REPT, "REPEAT");
		names.append(K_DVD_MENU, "DVD_MENU");
		names.append(K_DVD_AUDIO, "DVD_AUDIO");
		names.append(K_STAR, "ASTERISK");
		names.append(K_POUND, "POUND");
		names.append(K_CLEAR, "CLEAR");
		names.append(K_POWER, "POWER");
		names.append(K_0, "K_0");
		names.append(K_1, "K_1");
		names.append(K_2, "K_2");
		names.append(K_3, "K_3");
		names.append(K_4, "K_4");
		names.append(K_5, "K_5");
		names.append(K_6, "K_6");
		names.append(K_7, "K_7");
		names.append(K_8, "K_8");
		names.append(K_9, "K_9");
		names.append(K_AUX_IN, "AUXIN");
		names.append(K_SCROLL_R, "SCROLL_R");
		names.append(K_SCROLL_L, "SCROLL_L");
		names.append(K_ASSISTANT, "ASSIST");
		names.append(K_ASSI_GPS, "ASS/GPS");
		names.append(K_FAKE_POWER, "FAKE_POWER");
		names.append(K_SETUP, "SETUP");
		names.append(K_IPOD, "IPOD");
		names.append(K_MUTEONLY, "MUTEONLY");
		names.append(K_SD, "SD");
		names.append(K_USB, "USB");
		names.append(K_RECORD, "RECORD");
		names.append(K_REVERSING, "REVERSING");
		names.append(K_ALARM, "ALARM");
		
	}

	private int getkvol(final String symbolicName) {
		if (symbolicName == null) {
			throw new IllegalArgumentException("symbolicName must not be null");
		}

		final int N = KEYCODE_SYMBOLIC_NAMES.size();
		for (int i = 0; i < N; i++) {
			if (symbolicName.equals(KEYCODE_SYMBOLIC_NAMES.valueAt(i)))
				return KEYCODE_SYMBOLIC_NAMES.keyAt(i);
		}
		return -1;
	}

}
