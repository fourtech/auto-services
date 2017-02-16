package com.fourtech.autostate;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.PowerManager.GO_TO_SLEEP_FLAG_NO_DOZE;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DEVICE_ADMIN;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static android.os.PowerManager.SCREEN_DIM_WAKE_LOCK;
import static android.os.SystemProperties.getBoolean;
import static android.os.SystemProperties.getInt;
import static com.fourtech.autostate.AutoState.ACTION_ACC_ON;
import static com.fourtech.autostate.AutoState.ACTION_ACC_OVER;
import static com.fourtech.autostate.AutoState.ACTION_AUTO_HEARTBEAT;
import static com.fourtech.autostate.AutoState.ACTION_REVERSE_ON;
import static com.fourtech.autostate.AutoState.ACTION_REVERSE_OVER;
import static com.fourtech.autostate.AutoState.AUTO_STATE_SERVICE;
import static com.fourtech.hardware.EleRadar.FAN_MODE;
import static com.fourtech.hardware.EleRadar.FAN_MODE_CPUCTL;
import static com.fourtech.mcu.McuService.writeToMcu;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IActivityManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UEventObserver;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.util.LongSparseArray;
import android.util.Slog;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import com.fourtech.hardware.Accelerometer;
import com.fourtech.hardware.Display;
import com.fourtech.hardware.EleRadar;
import com.fourtech.hardware.FmTransmitter;
import com.fourtech.hardware.UsbSwitcher;
import com.fourtech.mcu.McuConstant;

/**
 * 汽车状态维护服务
 */
@SuppressLint("Wakelock")
public class AutoStateService extends IAutoStateService.Stub implements
		McuConstant, SensorEventListener {
	private static final String TAG = "AutoStateService";
	private static final boolean DEBUG = getBoolean("persist.debug.auto_state", false);
	private static final String DEBUG_USB_MODE = SystemProperties.get("persist.debug.usb_mode", "host");
	private static final boolean BOOT_START_CAMERA = getBoolean("persist.sys.boot.start_camera", false);
	private static final boolean REVERSE_ENABLED = getBoolean("persist.sys.reverse_enabled", true);
	private static final boolean MCUHEARTBEAT_ENABLED= getBoolean("persist.sys.mcuhb_enabled", true);

	private static final boolean RTCWAKEUP_ENABLED = getBoolean("persist.sys.rtcwakeup_enabled", true);
	private static final String ACTION_RTCWAKEUP_CALLBACK = "com.fourtech.RTCWAKEUP_CALLBACK";
	private static final Intent IT_RTCWAKEUP_CALLBACK = new Intent(ACTION_RTCWAKEUP_CALLBACK);
	private static final int RTCWAKEUP_INTERVAL = getInt("persist.sys.rtcwakeup_interval", 3600) * 1000;
	private static final int RTCWAKEUP_DELAYTIME = getInt("persist.sys.rtcwakeup_delaytime", 60) * 1000;

	private static final int GSENSOR_WAKEUP_DELAYTIME = getInt("persist.sys.wakeup_delaytime", 63 * 1000);
	private static final int AUTO_HEARTBEAT_DELAYTIME = getInt("sys.auto.heartbeat_delaytime", 15 * 1000);
	private static final int AUTO_OPEN_WIND_TEMP = getInt("persist.sys.open_wind_temp", 100);
	private static final int AUTO_CLOSE_WIND_TEMP = getInt("persist.sys.close_wind_temp", 90);

	private static final int ACC_OVER_DELAY = getInt("persist.sys.accover_delay", 2000);

	private static final String ACTION_CLOSE_APP = "com.rmt.action.KILL_PACKAGE";
	private static final String ACTION_ACC_OVER_CALLBACK = "android.intent.action.ACC_OVER_CALLBACK";
	private static final String ACTION_RECORDER_REQUEST_RESTART = "com.fourtech.request.RECORDER.RESTART";

	private static final String NAME_CAMERA_PACKAGE = "com.rk.carrecorder";
	private static final String NAME_CAMERA_CLASS = "com.rk.carrecorder.CameraActivity";
	private static final String NAME_CAMERA_SERVICE = "com.rockchip.CameraCrashService";
	private static final String ACTION_RECORD_BG = "com.rockchip.CameraCrashService";
	private static final String KEY_NIGHT_MODE = "setnightmode_by_user";

	private static final int FLAG_ACC     = 0x0001; // 1: Acc On; 0: Acc Off
	private static final int FLAG_REVERSE = 0x0002; // 1: 倒车中; 0: 正常状态

	private static final int AID_APP = 10000; // first app user

	private Context mContext;
	private StateObserver mStateObserver;
	private AudioManager mAudioManager;
	private PowerManager mPowerManager;
	private PowerManager.WakeLock mWakeUpLock;
	private PowerManager.WakeLock mKeepActiveLock;
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private final byte[] mLock = new byte[0];
	private long mRequestUpdateStateTime = 0;
	private static final long UPDATE_STATE_TIMEOUT = 10000;

	// private ActivityManager mActivityManager;
	private ProcessesMonitorService mProcessesMonitor;
	private ConnectivityManager mConnManager;
	private UsbSwitcher mUsbSwitcher;

	private byte mMpuMode = 0;
	private int  mUpTimes = 0;

	// 亮度随温度改变模式
	private int mCpuTempRatio = Integer.MIN_VALUE;
	private static final boolean TEMP_BRIGHTNESS_MODE;

	//private byte mPrvWindState = 0;
	private Handler mHandler;
	private Handler mMcuHandler;
	private Handler mRtcHandler;
	private long mAccOffTime = Long.MAX_VALUE;    // 记录进入Acc Off的时间
	private boolean mHasDoneStandbyStep1 = false; // 是否已经执行待机Step1
	private boolean mHasDoneStandbyStep2 = false; // 是否已经执行待机Step2
	private static final int STANDBY_STEP1_DELAY; // 进入待机Step延迟的时间，该Step将关闭相关Devices和杀死相关应用
	private static final int STANDBY_STEP2_DELAY; // 进入待机Step延迟的时间，该Step将退出应用和服务
	private static final int STANDBY_STEP3_DELAY; // 进入待机Step延迟的时间，该Step进入真正的休眠

	static {
		// 获取休眠步骤时间
		String[] stepDelays = SystemProperties.get("persist.sys.standby.step_delays", "18000,22000,24000").split(",");
		STANDBY_STEP1_DELAY = Integer.parseInt(stepDelays[0]);
		STANDBY_STEP2_DELAY = Integer.parseInt(stepDelays[1]);
		STANDBY_STEP3_DELAY = Integer.parseInt(stepDelays[2]);

		// 获取亮度随温度改变模式
		String strSerialNo = SystemProperties.get("ro.serialno", "1234567890abcdef");
		Matcher m = Pattern.compile("venus16(\\d{9})4t").matcher(strSerialNo);
		TEMP_BRIGHTNESS_MODE = m.matches() && Integer.parseInt(m.group(1)) < 3000;
	}

	private static AutoStateService sMe;
	public static AutoStateService get() { return sMe; }

	@SuppressWarnings("deprecation")
	public AutoStateService(Context context) {
		super();
		mContext = context;
		mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
		mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
		mWakeUpLock = mPowerManager.newWakeLock(ACQUIRE_CAUSES_WAKEUP | SCREEN_DIM_WAKE_LOCK, TAG);
		(mKeepActiveLock = mPowerManager.newWakeLock(PARTIAL_WAKE_LOCK, TAG + ".Active")).setReferenceCounted(false);
		// mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
		mConnManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		// Power不进入深度休眠
		mKeepActiveLock.acquire();

		// 创建进程检测
		mProcessesMonitor = new ProcessesMonitorService(mContext);

		// 创建后台线程处理更新事件
		HandlerThread t;
		(t = new HandlerThread("auto-state-service")).start();
		mHandler = new Handler(t.getLooper()/* , null, true */);
		(t = new HandlerThread("auto-state-mcu-service")).start();
		mMcuHandler = new Handler(t.getLooper()/* , null, true */);
		if (RTCWAKEUP_ENABLED) {
			(t = new HandlerThread("auto-state-rtc-service")).start();
			mRtcHandler = new Handler(t.getLooper()/* , null, true */);
		}

		native_isAccOn2(); // init acc state
		native_isReversing2(); // init reversing state

		mUsbSwitcher = new UsbSwitcher();

		Global.putInt(mContext.getContentResolver(), KEY_NIGHT_MODE, 0);

		IntentFilter iFilter = new IntentFilter();
		iFilter.addAction(ACTION_CLOSE_APP);
		iFilter.addAction(ACTION_ACC_OVER_CALLBACK);
		iFilter.addAction(Intent.ACTION_SHUTDOWN);
		iFilter.addAction(Intent.ACTION_SCREEN_ON);
		iFilter.addAction(Intent.ACTION_BOOT_COMPLETED);
		iFilter.addAction(ACTION_RECORDER_REQUEST_RESTART);
		iFilter.addAction(ACTION_RTCWAKEUP_CALLBACK);
		mContext.registerReceiver(mReceiver, iFilter);

		mStateObserver = new StateObserver();
		mStateObserver.startAutoObserving();

		// 要保证GSensor有监听，GSensor唤醒才可用
		mHandler.postDelayed(new Job("RegisterAccelerometerJob") {
			@Override
			public boolean runJob() {
				mSensorManager.registerListener(AutoStateService.this,
						mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
				return true;
			}
		}, 100);

		// setup instance
		sMe = this;
	}

	@Override
	public boolean isAccOn() {
		return native_isAccOn2();
	}

	@Override
	public boolean isReversing() {
		return native_isReversing2();
	}

	@Override
	public boolean filterInputEvent() {
		// 如果进入了升级状态，则屏蔽所有输入事件
		if (mRequestUpdateStateTime > 0) {
			if (SystemClock.uptimeMillis() - mRequestUpdateStateTime < UPDATE_STATE_TIMEOUT) {
				return false;
			} else {
				releaseUpdateState();
			}
		}

		// 熄火和倒车状态下屏蔽所有输入事件
		return mIsAccOn && !mIsReversing;
	}

	@Override
	public boolean requestUpdateState() {
		final boolean isFirstRequest = (mRequestUpdateStateTime == 0);
		mRequestUpdateStateTime = SystemClock.uptimeMillis();
		long callingId = Binder.clearCallingIdentity();
		try {
			// 进入升级状态，关闭其他应用
			if (isFirstRequest) {
				// 模拟Acc Over事件
				mMcuHandler.removeCallbacksAndMessages(null);
				mHandler.removeCallbacksAndMessages(null);
				writeToMcu(GID_HEADBEAT, SID_HEADBEAT, new byte[] { 1, 0 });
				mContext.sendBroadcastAsUser(new Intent("android.intent.action.ACC_OVER"), UserHandle.ALL); // 兼容RK
				mContext.sendBroadcastAsUser(new Intent(ACTION_ACC_OVER), UserHandle.ALL);
				try { Thread.sleep(500); } catch (Throwable t) { }

				// 关闭设备
				standbyStep1();
				try { Thread.sleep(1000); } catch (Throwable t) { }

				// 关闭应用
				forceStopAppProcesses(true);
				try { Thread.sleep(500); } catch (Throwable t) { }
			}
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
		return true;
	}

	@Override
	public boolean releaseUpdateState() {
		mRequestUpdateStateTime = 0;
		long callingId = Binder.clearCallingIdentity();
		try {
			// do resume
			if (native_isAccOn2()) {
				resume();
			}
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
		return true;
	}

	private Runnable mInitJob = new Job("InitJob") {
		@Override
		public boolean runJob() {
			Slog.i(TAG, "Now initializing(0)... time=" + SystemClock.uptimeMillis());
			if (native_isAccOn2()) {
				try {
					// 通知界面和服务更新
					int notifyState = (FLAG_ACC << 16) | FLAG_ACC;
					ActivityManagerNative.getDefault().setAutoState(notifyState);
				} catch (Throwable t) {
					Slog.i(TAG, "Initializing... setAutoState failed", t);
				}
				// 通知ACC状态
				mContext.sendBroadcastAsUser(new Intent("com.cayboy.action.ACC_ON"), UserHandle.ALL); // 兼容RK
				mContext.sendBroadcastAsUser(new Intent(ACTION_ACC_ON).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES), UserHandle.ALL);
			} else {
				mMcuHandler.removeCallbacksAndMessages(null);
				mHandler.removeCallbacksAndMessages(null);
				mHandler.postDelayed(mAccOverJob, 100);
				Slog.i(TAG, "Now initializing(1)... Acc is over, time=" + SystemClock.uptimeMillis());
			}

			// 查看Camera是否被uboot占用，用作开机倒车视频
			while (getInt("boot.car.reverse", 0) == 1) {
				try {
					// 等1秒后再检查是否uboot已经释放Camera
					Thread.sleep(1000);
				} catch (Throwable t) {
				}
			}

			// 更新系统倒车属性
			SystemProperties.set("sys.car.reverse", "2");
			Slog.i(TAG, "Now initializing(2)... time=" + SystemClock.uptimeMillis());

			// do resume
			if (native_isAccOn2()) {
				resume();
			}

			// 如果是倒车状态则不要等待
			for (int i = 0; i < 12; i++) {
				if (!native_isReversing()) {
					try {
						Thread.sleep(500);
					} catch (Throwable t) {
					}
				}
			}

			Slog.i(TAG, "Now initializing(3)... time=" + SystemClock.uptimeMillis() + ", backlight=" + Display.getBacklight());

			// 启动录像界面
			if (native_isAccOn2()) {
				// 启动录像
				autoStartCamera();
			}

			return true;
		}
	};

	private Runnable mAccOnJob = new Job("AccOnJob") {
		@Override
		public boolean runJob() {
			if (native_isAccOn2()) {
				mHasDoneStandbyStep1 = false;
				mHasDoneStandbyStep2 = false;
				Slog.i(TAG, "Acc now is On");

				// reset night mode
				Global.putInt(mContext.getContentResolver(), KEY_NIGHT_MODE, 0);

				// 唤醒系统
				mPowerManager.wakeUp(SystemClock.uptimeMillis());
				mHandler.postDelayed(new Job("SetBacklightJob") {
					@Override
					public boolean runJob() {
						if (native_isAccOn2() && Display.getBacklight() <= 0) {
							try {
								int brightness = Settings.System.getIntForUser(
										mContext.getContentResolver(),
										Settings.System.SCREEN_BRIGHTNESS, 100,
										UserHandle.USER_CURRENT);
								Display.setBacklightBrightness(brightness);
								if (DEBUG) Slog.i(TAG, "Acc On: setBacklight( " + brightness + " )");
							} catch (Throwable t) {
								Slog.w(TAG, "Acc On: get SCREEN_BRIGHTNESS failed", t);
							}
						}
						return true;
					}
				}, 1000);

				// 更新屏幕长亮时间
				mWakeUpLock.acquire(1000);

				// 启动搜星程序
				SystemProperties.set("ctl.start", "faststartgps");

				// 恢复待机前状态
				resume();

				// 通知Acc状态
				try {
					// 通知界面和服务更新
					int notifyState = (FLAG_ACC << 16) | FLAG_ACC;
					ActivityManagerNative.getDefault().setAutoState(notifyState);
				} catch (Throwable t) {
					Slog.w(TAG, "Acc On: setAutoState failed", t);
				}
				mContext.sendBroadcastAsUser(new Intent("com.cayboy.action.ACC_ON"), UserHandle.ALL); // 兼容RK
				mContext.sendBroadcastAsUser(new Intent(ACTION_ACC_ON).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES), UserHandle.ALL);
				/*
				try {
					// ACC Over 时大部分应用已经退出，重发开机完成广播让其重新自动启动
					Intent intent = new Intent(Intent.ACTION_BOOT_COMPLETED, null);
					intent.putExtra(Intent.EXTRA_USER_HANDLE, ActivityManager.getCurrentUser());
					intent.addFlags(Intent.FLAG_RECEIVER_NO_ABORT);
					mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
				} catch (Throwable t) {
					Slog.i(TAG, "Acc On: send boot completed broadcast failed", t);
				}
				*/

				// 启动录像界面
				autoStartCamera();

				return true;
			}
			return false;
		}
	};

	private Runnable mAccOverJob = new Job("AccOverJob") {
		@Override
		@SuppressWarnings("deprecation")
		public boolean runJob() {
			if (DEBUG) Slog.i(TAG, "mAccOverJob::runJob() hasAccOverJob=" + mHandler.hasCallbacks(mAccOverJob));
			if (!native_isAccOn2() && !mHandler.hasCallbacks(mAccOverJob)) { // 只处理最后一个
				mHasDoneStandbyStep1 = false;
				mHasDoneStandbyStep2 = false;
				mAccOffTime = System.currentTimeMillis();
				Slog.i(TAG, "Acc now is Over");

				// 关背光
				Display.setBacklight(0); // 该方法在AccOver时候也可以生效
				launchHomeByKey(); // 返回主界面
				sendCloseSystemWindows("homekey");
				mPowerManager.goToSleep(SystemClock.uptimeMillis());
				if (DEBUG) Slog.i(TAG, "Acc Over: turn off screen brightness, sleep");

				// 进入静音状态
				if (!mAudioManager.isMasterMute()) {
					mAudioManager.setMasterMute(true, 0);
					if (DEBUG) Slog.i(TAG, "Acc Over: mute master");
				}

				// 设置麦克风进入静音状态
				//if (!mAudioManager.isMicrophoneMute()) {
				//	mAudioManager.setMicrophoneMute(true);
				//}

				// 关闭雷达
				EleRadar.goToSleep();

				// update camera bootup state
				if (getBoolean("persist.sys.carrecord.bootup", false)) {
					SystemProperties.set("sys.cam_has_bootup", "0");
				}

				// 如果2秒内收不到standby callback，主动进入休眠
				mMcuHandler.removeCallbacksAndMessages(null);
				mHandler.removeCallbacksAndMessages(null);
				mHandler.postDelayed(mStandbyJob, 2000);

				mHandler.postDelayed(new Job("SendAccOverBroadcastJob") {
					@Override
					public boolean runJob() {
						if (!native_isAccOn2()) {
							mContext.sendBroadcastAsUser(new Intent("android.intent.action.ACC_OVER"), UserHandle.ALL); // 兼容RK
							mContext.sendBroadcastAsUser(new Intent(ACTION_ACC_OVER), UserHandle.ALL);
						}
						return true;
					}
				}, 500);

				return true;
			}
			return false;
		}
	};

	private Runnable mReverseOnJob = new Job("ReverseOnJob") {
		@Override
		public boolean runJob() {
			if (native_isAccOn2() && native_isReversing2()) {
				Slog.i(TAG, "Now is Reversing");

				// 查看Camera是否被uboot占用
				if (getInt("boot.car.reverse", 0) == 1) {
					Slog.i(TAG, "Reversing: Camera it is using by uboot, return");
					return false;
				}

				// 更新系统倒车属性
				SystemProperties.set("sys.car.reverse", "2");

				// 进入倒车界面关闭音乐音量
				if (!mAudioManager.isStreamMute(AudioManager.STREAM_MUSIC)) {
					mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
				}

				// 通知倒车状态
				try {
					// 通知界面更新
					int notifyState = (FLAG_REVERSE << 16) | FLAG_REVERSE;
					ActivityManagerNative.getDefault().setAutoState(notifyState);
				} catch (Throwable t) {
					Slog.i(TAG, "Reversing: setAutoState failed", t);
				}
				sendCloseSystemWindows("homekey");
				if (DEBUG) Slog.i(TAG, "Reversing: send broadcast");
				mContext.sendBroadcastAsUser(new Intent(ACTION_REVERSE_ON), UserHandle.ALL);

				/* 启动倒车界面
				//if (!NAME_CAMERA_CLASS.equals(getRunningActivityName())) {
					try {
						if (DEBUG) Slog.i(TAG, "Reversing: start camera activity");
						Intent recorder = new Intent();
						recorder.setClassName(NAME_CAMERA_PACKAGE, NAME_CAMERA_CLASS);
						recorder.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						recorder.putExtra("CAR-REVERSE", "on");
						mContext.startActivity(recorder);
					} catch (Throwable t) {
						Slog.e(TAG, "Start auto reverse UI failed.");
					}
				//}*/
				return true;
			}
			return false;
		}
	};

	private Runnable mReverseOverJob = new Job("ReverseOverJob") {
		@Override
		public boolean runJob() {
			if (!native_isReversing2()) {
				// 倒车时关闭音乐声音
				mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
				mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);

				// 通知倒车状态
				try {
					// 通知界面更新
					int notifyState = (FLAG_REVERSE << 16);
					ActivityManagerNative.getDefault().setAutoState(notifyState);
				} catch (Throwable t) {
					Slog.i(TAG, "Reverse Over: setAutoState failed", t);
				}
				if (DEBUG) Slog.i(TAG, "Reverse Over: send broadcast");
				mContext.sendBroadcastAsUser(new Intent(ACTION_REVERSE_OVER), UserHandle.ALL);
				return true;
			}
			return false;
		}
	};

	private Intent mHbIntent = new Intent(ACTION_AUTO_HEARTBEAT);
	private Runnable mHeartbeatJob = new Job("HeartbeatJob") {
		@Override
		public boolean runJob() {
			if (native_isAccOn2()) {
				mContext.sendBroadcastAsUser(mHbIntent, UserHandle.ALL);
				mHandler.removeCallbacks(mHeartbeatJob);
				mHandler.postDelayed(mHeartbeatJob, AUTO_HEARTBEAT_DELAYTIME);
			}
			return false;
		}
	};

	private boolean mMcuHeartbeatEnabled = true;
	private Runnable mMcuHeartbeatJob = new Job("McuHeartbeatJob") {
		@Override
		public boolean runJob() {
			if (MCUHEARTBEAT_ENABLED && mMcuHeartbeatEnabled) {
				byte[] data = { 1, mMpuMode };
				Slog.i(TAG, "mMcuHeartbeatJob  data==" + data[1]);
				writeToMcu(GID_HEADBEAT, SID_HEADBEAT, data);
				mMcuHandler.removeCallbacks(mMcuHeartbeatJob);
				mMcuHandler.postDelayed(mMcuHeartbeatJob, 1000);
				requestWindStateIfNeeded();
				// 只dump最后一次
				clearSelf();
				// 加入dump队列
				return true;
			}
			return false;
		}
	};

	private void requestWindStateIfNeeded() {
		if (mUpTimes++ > 5) {
			mUpTimes = 0;
			int temp = native_getCpuTemp() / 1000;
			if (DEBUG) Slog.i(TAG, "mPrvWindState==" + temp);
			if ((temp > AUTO_OPEN_WIND_TEMP) && (temp < 130)) {
				if (FAN_MODE.equals(FAN_MODE_CPUCTL)) {
					EleRadar.start();
				} else {
					// mPrvWindState = 1;
					writeToMcu(GID_CPU_TEMP, SID_CPU_TEMP, new byte[] { 1 /* PrvWindState */});
					Slog.i(TAG, "requestWindStateIfNeeded() mPrvWindState=1, temp=" + temp);
				}
			} else if (temp < AUTO_CLOSE_WIND_TEMP) {
				if (FAN_MODE.equals(FAN_MODE_CPUCTL)) {
					EleRadar.stop();
				} else {
					// mPrvWindState = 0;
					writeToMcu(GID_CPU_TEMP, SID_CPU_TEMP, new byte[] { 0 /* PrvWindState */});
					Slog.i(TAG, "requestWindStateIfNeeded() mPrvWindState=0, temp=" + temp);
				}
			}

			// 更新亮度
			if (TEMP_BRIGHTNESS_MODE) {
				int old = mCpuTempRatio;
				mCpuTempRatio = temp / 5;
				if (old != Integer.MIN_VALUE && mCpuTempRatio != old) {
					try {
						int brightness = Settings.System.getIntForUser(
								mContext.getContentResolver(),
								Settings.System.SCREEN_BRIGHTNESS, 100,
								UserHandle.USER_CURRENT);
						Display.setBacklightBrightness(brightness);
						if (DEBUG) Slog.i(TAG, "requestWindStateIfNeeded() update brightness to " + brightness);
					} catch (Throwable t) {
						Slog.w(TAG, "requestWindStateIfNeeded() update brightness failed", t);
					}
				}
			}
		}
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		@SuppressWarnings("deprecation")
		public void onReceive(Context context, Intent intent) {
			String action = intent != null ? intent.getAction() : null;

			if (ACTION_CLOSE_APP.equals(action)) {
				if (DEBUG) Slog.i(TAG, "Receive close app action");
				// killAppByPackage(intent.getStringExtra("kill_package_name"));

			} else if (ACTION_ACC_OVER_CALLBACK.equals(action)) {
				Slog.i(TAG, "Auto acc over callback: standby");
				mMcuHandler.removeCallbacksAndMessages(null);
				mHandler.removeCallbacksAndMessages(null);
				mHandler.postDelayed(mStandbyJob, 1000);

			} else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
				new Thread(mInitJob, "InitJob").start();
				mMpuMode=(byte) (mMpuMode|0x01);//// 状态为 nomal
			} else if (Intent.ACTION_SHUTDOWN.equals(action)) {
				mMpuMode=(byte) (mMpuMode&0xfe);// 状态为 shutdown
			} else if (Intent.ACTION_SCREEN_ON.equals(action)) {
				// 如果是Acc Off状态，重新进入休眠
				if (!native_isAccOn2()) {
					Display.setBacklight(0); // 该方法在AccOver时候也可以生效;
					Slog.i(TAG, "Screen On: Acc is over");
					mHandler.removeCallbacks(mAccOnJob);
					mHandler.removeCallbacks(mReverseOnJob);
					mHandler.removeCallbacks(mHeartbeatJob);
					mHandler.removeCallbacks(mMcuHeartbeatJob);
					if (!mHandler.hasCallbacks(mAccOverJob)
							&& !mHandler.hasCallbacks(mStandbyJob)) {
						// mHandler.removeCallbacksAndMessages(null);
						mHandler.postDelayed(mAccOverJob, 4000);
					}
				}
			} else if (ACTION_RECORDER_REQUEST_RESTART.equals(action)) {
				// 重新启动记录仪
				autoStartCamera();
			} else if (ACTION_RTCWAKEUP_CALLBACK.equals(action)) {
				mRtcHandler.removeCallbacksAndMessages(null);
				mRtcHandler.postDelayed(new Job("RtcJob") {
					@Override
					public boolean runJob() {
						Slog.i(TAG, "Rtc Wake Up: isAccOn=" + native_isAccOn2());
						if (!native_isAccOn2()) {
							// 先唤醒系统
							// mPowerManager.wakeUp(SystemClock.uptimeMillis());
							Display.setBacklight(0); // 该方法在AccOver时候也可以生效;

							// 启动搜星程序
							SystemProperties.set("ctl.start", "faststartgps");
							Slog.i(TAG, "Rtc Wake Up: start faststartgps");

							mHandler.removeCallbacks(mAccOnJob);
							mHandler.removeCallbacks(mAccOverJob);
							mHandler.removeCallbacks(mReverseOnJob);
							mHandler.removeCallbacks(mHeartbeatJob);
							mHandler.removeCallbacks(mMcuHeartbeatJob);
							mHandler.postDelayed(mAccOverJob, RTCWAKEUP_DELAYTIME);
							return true;
						}
						return false;
					}
				}, 200);

				mKeepActiveLock.acquire(/*RTCWAKEUP_DELAYTIME + STANDBY_STEP3_DELAY + 3000*/);
			}
		}
	};

	private Runnable mStandbyJob = new Job("StandbyJob") {
		@Override
		public boolean runJob() {
			mName = "StandbyJob";
			boolean clear = true;
			mHandler.removeCallbacks(this);
			if (!native_isAccOn2() && !mHandler.hasCallbacks(mAccOverJob)) {
				final long delay = System.currentTimeMillis() - mAccOffTime;
				if (DEBUG) Slog.i(TAG, "Do standby job, delay=" + delay);

				if (!mHasDoneStandbyStep1) {
					if (delay >= STANDBY_STEP1_DELAY) {
						clear = false;
						mName = "StandbyStep1Job";
						standbyStep1();
						mHasDoneStandbyStep1 = true;
					}
				} else {
					if (!mHasDoneStandbyStep2) {
						if (delay >= STANDBY_STEP2_DELAY) {
							clear = false;
							mName = "StandbyStep2Job";
							standbyStep2();
							mHasDoneStandbyStep2 = true;
						}
					} else if (delay >= STANDBY_STEP3_DELAY) {
						mName = "StandbyStep3Job";
						standbyStep3();
						return true;
					}
				}

				// 如果有新的AccOverJob进队列，则停止当前任务
				if (!mHandler.hasCallbacks(mAccOverJob)) {
					mHandler.postDelayed(this, 1000);
				}

				if (clear) clearSelf();
				return true;
			}
			return false;
		}
	};

	private Runnable mGsensorWakeupJob = new Runnable() {
		@Override
		@SuppressWarnings("deprecation")
		public void run() {
			// 只在Acc Off状态下唤醒并且开启录制
			if (!native_isAccOn2()/* && mHasDoneStandbyStep1*/) {// 不需要mHasDoneStandbyStep1判断，因为没有休眠下去时不会发送改UEent
				// 先唤醒系统
				mPowerManager.wakeUp(SystemClock.uptimeMillis());
				Display.setBacklight(0); // 该方法在AccOver时候也可以生效;
				/*mPowerManager.userActivity(
						SystemClock.uptimeMillis(),
						USER_ACTIVITY_EVENT_OTHER,
						USER_ACTIVITY_FLAG_NO_CHANGE_LIGHTS);*/
				mAccOffTime = Long.MAX_VALUE;
				mMcuHandler.removeCallbacksAndMessages(null);
				mHandler.removeCallbacksAndMessages(null);

				// 取消RTC唤醒
				// cancelRtcWakeup();
				// 启动搜星程序
				SystemProperties.set("ctl.start", "faststartgps");

				// 把USB切换到Host模式
				mUsbSwitcher.switchMode(UsbSwitcher.MODE_HOST);
				// 恢复飞行模式，以便发送图片
				mConnManager.setAirplaneMode(false);

				// 开启录制并锁定视频
				mHandler.postDelayed(new Job("GsensorWakeupJob") {
					@Override
					public boolean runJob() {
						if (!native_isAccOn2()) {
							// 再次恢复飞行模式，以便发送图片
							mConnManager.setAirplaneMode(false);
							autoStartCamera(true);
							return true;
						}
						return false;
					}
				}, 300);

				// 拍视频后重新回到休眠状态
				mHandler.postDelayed(mAccOverJob, GSENSOR_WAKEUP_DELAYTIME);
			}
		}
	};

	private class StateObserver extends UEventObserver {

		public StateObserver() {
			super();
		}

		public void startAutoObserving() {
			startObserving("GPIO_NAME=car-acc GPIO_STATE=on");
			startObserving("GPIO_NAME=car-acc GPIO_STATE=over");
			if (REVERSE_ENABLED) {
				startObserving("GPIO_NAME=car-reverse GPIO_STATE=on");
				startObserving("GPIO_NAME=car-reverse GPIO_STATE=over");
			}
			startObserving("GSENSOR_EVENT=WAKEUP");
		}

		@Override
		public void onUEvent(UEvent event) {
			String state = event.toString();
			if (DEBUG) Slog.i(TAG, "Auto state changed: '" + state + "'");

			if (state.contains("GPIO_STATE=on")) {
				if (state.contains("car-acc")) {
					if (DEBUG) Slog.i(TAG, "Auto state changed: Acc ON");

					//mHandler.removeCallbacks(mAccOnJob);
					//mHandler.removeCallbacks(mAccOverJob);
					//mHandler.removeCallbacks(mStandbyJob);
					//mHandler.removeCallbacks(mHeartbeatJob);
					//mHandler.removeCallbacks(mSetStandbyStep1Job);
					mMcuHandler.removeCallbacksAndMessages(null);
					mHandler.removeCallbacksAndMessages(null); // temp
					mHandler.postDelayed(mAccOnJob, 2000);

					// 马上切USB模式，以利于更早录制
					mHandler.post(new Job("SwitchUsbModeJob") {
						@Override
						public boolean runJob() {
							// 恢复Usb模式
							int usbMode = (DEBUG_USB_MODE.equals("device") || DEBUG)
									? UsbSwitcher.MODE_DEVICE
									: UsbSwitcher.MODE_HOST;
							mUsbSwitcher.switchMode(usbMode);
							return true;
						}
					});

				} else if (state.contains("car-reverse")) {
					if (DEBUG) Slog.i(TAG, "Auto state changed: Reversing");

					mHandler.removeCallbacks(mReverseOnJob);
					mHandler.removeCallbacks(mReverseOverJob);
					mHandler.postDelayed(mReverseOnJob, 500);
				}
			} else if (state.contains("GPIO_STATE=over")) {
				if (state.contains("car-acc")) {
					if (DEBUG) Slog.i(TAG, "Auto state changed: Acc Over");

					//mHandler.removeCallbacks(mAccOnJob);
					//mHandler.removeCallbacks(mReverseOnJob);
					//mHandler.removeCallbacks(mHeartbeatJob);
					mMcuHandler.removeCallbacksAndMessages(null);
					mHandler.removeCallbacksAndMessages(null); // temp
					//if (!mHandler.hasCallbacks(mAccOverJob)
					//		&& !mHandler.hasCallbacks(mStandbyJob)) {
						mHandler.postDelayed(mAccOverJob, ACC_OVER_DELAY);
					//}
				} else if (state.contains("car-reverse")) {
					if (DEBUG) Slog.i(TAG, "Auto state changed: Reversing Over");

					mHandler.removeCallbacks(mReverseOnJob);
					mHandler.removeCallbacks(mReverseOverJob);
					mHandler.postDelayed(mReverseOverJob, 2000);
				}
			} else if (state.contains("GSENSOR_EVENT=WAKEUP")) {
				if (DEBUG) Slog.i(TAG, "Auto state changed: Gsensor WakeUp, Acc is "
							+ (native_isAccOn2() ? "On" : "Over")
							+ ", mHasDoneStandbyStep1=" + mHasDoneStandbyStep1);
				mHandler.removeCallbacks(mGsensorWakeupJob);
				mHandler.postDelayed(mGsensorWakeupJob, 200);

				mKeepActiveLock.acquire(/*RTCWAKEUP_DELAYTIME + STANDBY_STEP3_DELAY + 3000*/);
			}

			if (DEBUG) Slog.i(TAG, "onUEvent() isAccOn=" + native_isAccOn2() + ", isReversing=" + native_isReversing2());
		}

	}

	private void resume() {
		if (DEBUG) Slog.i(TAG, "resume() isAccOn=" + native_isAccOn2() + ", isReversing=" + native_isReversing2());

		// 情况待机步骤
		SystemProperties.set("sys.gotosleep_step", "0");

		// 重启uvc reverse detection
		if (getBoolean("persist.sys.use_uvc_reverse", false)) {
			SystemProperties.set("persist.sys.use_uvc_reverse", "1");
		}

		// 取消RTC唤醒
		cancelRtcWakeup();

		// 开启进程监测
		mProcessesMonitor.start();

		// 恢复声音
		mAudioManager.setMasterMute(false, 0);
		mAudioManager.setMasterMute(false, 0);
		int maxMv = mAudioManager.getMasterMaxVolume();
		mAudioManager.setMasterVolume(maxMv, 0);
		// mAudioManager.setMicrophoneMute(false);
		if (DEBUG) Slog.i(TAG, "resume() maxMv=" + maxMv);

		// 恢复FM
		FmTransmitter.get().wakeUp(mContext);
		doExecSafely("tinymix 9 0");

		// 恢复Usb模式
		int usbMode = (DEBUG_USB_MODE.equals("device") || DEBUG)
						? UsbSwitcher.MODE_DEVICE
						: UsbSwitcher.MODE_HOST;
		mUsbSwitcher.switchMode(usbMode);

		// 恢复雷达休眠期状态
		if (!FAN_MODE.equals(FAN_MODE_CPUCTL)) {
			EleRadar.wakeUp();
		}

		// 恢复振灵敏度
		int senLevel = 2;
		String[] senValues = SystemProperties.get("persist.sys.gs_sensitivity_vs", "10,70,130,190,250").split(",");
		Accelerometer.setWakeUpSensitivity(Integer.parseInt(senValues[senLevel]));

		// 恢复飞行模式
		mConnManager.setAirplaneMode(false);
		mHandler.postDelayed(new Job("SetAirplaneModeJob") {
			@Override
			public boolean runJob() {
				if (native_isAccOn2()) {
					mConnManager.setAirplaneMode(false);
				}
				return false;
			}
		}, 500);

		// 打开GPS 和 语言服务
		setLocationEnabled(true);
		mHandler.postDelayed(new Job("SetLocationEnabledJob") {
			@Override
			public boolean runJob() {
				setLocationEnabled(true);
				// startRobotService();
				return false;
			}
		}, 600);

		// 发送心跳广播，一些应用需要监听这个心跳重新启动
		mHandler.removeCallbacks(mHeartbeatJob);
		mHandler.postDelayed(mHeartbeatJob, AUTO_HEARTBEAT_DELAYTIME);
		mMcuHandler.removeCallbacks(mHeartbeatJob);
		mMcuHandler.postDelayed(mMcuHeartbeatJob, 1000);

		// Power不进入深度休眠
		mKeepActiveLock.acquire();
	}

	private void standbyStep1() {
		if (DEBUG) Slog.i(TAG, "standbyStep1() isAccOn=" + native_isAccOn2() + ", isReversing=" + native_isReversing2());

		// 退出进程监测
		mProcessesMonitor.stop();

		// 设置MCU关机时间
		setMcuPowerOffTime();

		// 进入飞行模式
		mConnManager.setAirplaneMode(true);

		// 关闭GPS
		setLocationEnabled(false);

		SystemProperties.set("sys.gotosleep_step", "1");

		// Acc Off时进入唤醒监听
		int senLevel = getInt("persist.sys.wakeupsensitivity", 0);
		String[] senValues = SystemProperties.get("persist.sys.gs_sensitivity_vs", "3,12,24,48,60").split(",");
		Accelerometer.setWakeUpSensitivity(Integer.parseInt(senValues[senLevel]));

		// 关闭Fm发射
		FmTransmitter.get().goToSleep(mContext);
		//doExecSafely("tinymix 9 0");

		// 通知系统和应用进入ACC Off状态
		try {
			// 通知界面和服务退出
			int notifyState = (FLAG_ACC << 16);
			ActivityManagerNative.getDefault().setAutoState(notifyState);
		} catch (Throwable t) {
			Slog.i(TAG, "Acc Over: setAutoState failed", t);
		}
	}

	private void standbyStep2() {
		if (DEBUG) Slog.i(TAG, "standbyStep2() isAccOn=" + native_isAccOn2() + ", isReversing=" + native_isReversing2());

		// 停止搜星程序
		SystemProperties.set("ctl.stop", "faststartgps");

		// 把USB切换到Device模式，停止供电
		mUsbSwitcher.switchMode(UsbSwitcher.MODE_NONE);

		// 进入步骤2休眠状态
		SystemProperties.set("sys.gotosleep_step", "2");
		// SystemProperties.set("ctl.start", "auto-prockiller");
		forceStopAppProcesses();

		// 注册RTC唤醒，以提高GPS生效时间
		startRtcWakeup();
	}

	private void standbyStep3() {
		if (DEBUG) Slog.i(TAG, "standbyStep3() isAccOn=" + native_isAccOn2() + ", isReversing=" + native_isReversing2());

		// 等待1秒再进入休眠
		mKeepActiveLock.acquire(1000);

		// 进入步骤3休眠状态
		SystemProperties.set("sys.gotosleep_step", "3");

		// 进入待机
		mPowerManager.goToSleep(SystemClock.uptimeMillis(),
				GO_TO_SLEEP_REASON_DEVICE_ADMIN,
				GO_TO_SLEEP_FLAG_NO_DOZE);
	}

	private PendingIntent mRtcCallback;
	private PendingIntent obtainRtcCallback() {
		return mRtcCallback != null
				? mRtcCallback
				: (mRtcCallback = PendingIntent.getBroadcastAsUser(mContext, 0, IT_RTCWAKEUP_CALLBACK, 0, UserHandle.ALL));
	}

	private void startRtcWakeup() {
		if (RTCWAKEUP_ENABLED
				&& !native_isAccOn2()
				&& mRtcHandler != null) {
			mRtcHandler.removeCallbacksAndMessages(null);
			mRtcHandler.post(new Job("StartRtcWakeupJob") {
				@Override
				public boolean runJob() {
					Slog.i(TAG, "startRtcWakeup()");
					PendingIntent rtcCallback = obtainRtcCallback();
					AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
					am.cancel(rtcCallback);
					am.set(ELAPSED_REALTIME_WAKEUP, (SystemClock.elapsedRealtime() + RTCWAKEUP_INTERVAL), rtcCallback);
					return true;
				}
			});
		}
	}

	private void cancelRtcWakeup() {
		if (RTCWAKEUP_ENABLED
				/*&& native_isAccOn2()*/
				&& mRtcHandler != null) {
			mRtcHandler.removeCallbacksAndMessages(null);
			mRtcHandler.post(new Job("CancelRtcWakeupJob") {
				@Override
				public boolean runJob() {
					Slog.i(TAG, "cancelRtcWakeup()");
					PendingIntent rtcCallback = obtainRtcCallback();
					AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
					am.cancel(rtcCallback);
					return true;
				}
			});
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (DEBUG) Slog.i(TAG, "onSensorChanged() event=" + event);
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int value) {
		if (DEBUG) Slog.i(TAG, "onAccuracyChanged() sensor=" + sensor + ", value=" + value);
	}

	static final ArrayList<String> AUTO_PROTECTEDPKG_LIST = new ArrayList<>();

	static {
		File pProcListFile = new File("/system/etc/protected-package.list");
		if (pProcListFile.exists()) {
			BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(pProcListFile));
				for (String line = null; (line = reader.readLine()) != null;) {
					AUTO_PROTECTEDPKG_LIST.add(line);
					if (DEBUG) Slog.i(TAG, "Read from protected-package.list: " + line);
				}
			} catch (Throwable t) {
				if (DEBUG) Slog.w(TAG, "Read protected-package.list failed", t);
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable t) {
					}
				}
			}
		}
	}

	private void forceStopAppProcesses() {
		forceStopAppProcesses(false);
	}

	private void forceStopAppProcesses(boolean isUpdateState) {
		IActivityManager am = ActivityManagerNative.getDefault();
		try {
			List<RunningAppProcessInfo> rApps = am.getRunningAppProcesses();
			final int N = rApps != null ? rApps.size() : 0;
			for (int i = 0; i < N; i++) {
				RunningAppProcessInfo app = rApps.get(i);
				if (app.uid >= AID_APP) {
					if (DEBUG) Slog.i(TAG, "forceStopAppProcesses() APP"
								+ " uid=" + app.uid
								+ ", processName=" + app.processName
								+ ", pkgList=" + Arrays.toString(app.pkgList)
								+ ", flags=" + app.flags);

					final int P = app.pkgList != null ? app.pkgList.length : 0;
					J: for (int j = 0; j < P; j++) {
						String packageName = app.pkgList[j];
						if (packageName == null)
							continue;

						// 升级状态下，保护升级进程
						if (isUpdateState) {
							if (packageName.matches("android.rockchip.update.service")) {
								if (DEBUG) Slog.i(TAG, "forceStopAppProcesses() is update state, keep " + packageName);
								continue;
							}
						}

						for (int k = 0; k < AUTO_PROTECTEDPKG_LIST.size(); k++) {
							if (packageName.matches(AUTO_PROTECTEDPKG_LIST.get(k))) {
								if (DEBUG) Slog.i(TAG, "forceStopAppProcesses() " + packageName + " is  protected, continue");
								continue J;
							}
						}

						try {
							if (DEBUG) Slog.i(TAG, "forceStopAppProcesses() stop '" + packageName + "'");
							am.forceStopPackage(packageName, UserHandle.myUserId());
						} catch (Throwable tt) {
							Slog.w(TAG, "forceStopAppProcesses()", tt);
						}
					}

				} else {
					if (DEBUG) Slog.i(TAG, "forceStopAppProcesses() SYS"
							+ " uid=" + app.uid
							+ ", processName=" + app.processName
							+ ", pkgList=" + Arrays.toString(app.pkgList)
							+ ", flags=" + app.flags);
				}
			}
		} catch (Throwable t) {
			Slog.w(TAG, "forceStopAppProcesses()", t);
		}
	}


	private long mLastStartCameraJobId = 0;
	private static final File DEV_VIDEO5 = new File("/dev/video5");
	private void autoStartCamera() { autoStartCamera(false); }
	private void autoStartCamera(final boolean lock) {
		Thread t = new Thread(new Job("AutoStartCameraJob") {
			@Override
			public boolean runJob() {
				int N = 0;
				while (getInt("boot.car.has_release", 0) == 0
						&& !(DEV_VIDEO5.exists() && DEV_VIDEO5.canRead())
						&& N++ < 10) {
					if (Thread.currentThread().getId() != mLastStartCameraJobId)
						return false;

					try {
						Thread.sleep(500);
					} catch (Throwable t) {
					}
				}

				try {
					// 再等500毫秒，以便摄像头准备就绪
					Thread.sleep(500);
				} catch (Throwable t) {
				}

				if (Thread.currentThread().getId() != mLastStartCameraJobId
						|| !(lock || native_isAccOn2()))
					return false;

				// 是否开机启动录制界面
				if (BOOT_START_CAMERA && native_isAccOn2()) {
					startCameraRecorder();
				}

				// 是否开机启动录制界面，背景录制
				//if (getBoolean("persist.sys.carrecord.bootup", false)) {
					if (DEBUG) {
						mHandler.post(new Runnable() {
							@Override
							public void run() {
								Toast.makeText(mContext, "Start camera record during bootup", Toast.LENGTH_LONG).show();
							}
						});
					}

					// 开启录制，但不启动页面
					if (lock || native_isAccOn2()) {
						startCameraRecorderBg(lock);
					}
				//}
				return true;
			}
		});
		mLastStartCameraJobId = t.getId();
		t.start();
	}

	private boolean setLocationEnabled(boolean enabled) {
		if (DEBUG) Slog.i(TAG, "setLocationEnabled("+enabled+"), Acc On is " + native_isAccOn2());
		if (!native_isAccOn2()) enabled = false;

		int currentUserId = ActivityManager.getCurrentUser();
		final ContentResolver cr = mContext.getContentResolver();
		int mode = enabled
				? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
				: Settings.Secure.LOCATION_MODE_OFF;

		mContext.sendBroadcast(new Intent(
				"com.android.settings.location.MODE_CHANGING")
				.putExtra("NEW_MODE", mode),
				android.Manifest.permission.WRITE_SECURE_SETTINGS);

		return Settings.Secure.putIntForUser(cr,
				Settings.Secure.LOCATION_MODE + "_fourtech",
				mode, currentUserId);
	}

	private void launchHomeByKey() {
		long now = SystemClock.uptimeMillis();
		final KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
				KeyEvent.KEYCODE_HOME, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD,
				0, KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
				InputDevice.SOURCE_KEYBOARD);
		InputManager.getInstance().injectInputEvent(down,
				InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

		final KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP,
				KeyEvent.KEYCODE_HOME, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD,
				0, KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
				InputDevice.SOURCE_KEYBOARD);
		InputManager.getInstance().injectInputEvent(up,
				InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
	}

	private void startCameraRecorder() {
		try {
			Intent recorder = new Intent();
			recorder.setClassName(NAME_CAMERA_PACKAGE, NAME_CAMERA_CLASS);
			recorder.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			recorder.putExtra("BOOTUP", true);
			mContext.startActivity(recorder);
		} catch (Throwable t) {
			Slog.w(TAG, "mAccOnJob:run() start camera recorder failed", t);
		}
	}

	private void startCameraRecorderBg(boolean lock) {
		try {
			Intent recorder = new Intent(ACTION_RECORD_BG);
			recorder.setPackage(NAME_CAMERA_PACKAGE);
			if (lock) recorder.putExtra("LOCK", true);
			recorder.putExtra("record_bg", (lock || getBoolean("persist.sys.carrecord.bootup", false)));
			recorder.putExtra("check_reverse", native_isReversing2());
			mContext.startService(recorder);
		} catch (Throwable t) {
			Slog.w(TAG, "startCameraRecorderBg() start camera recorder failed", t);
		}
	}

	private void startRobotService() {
		final String ACTION_ROBOT_SERVICE = "android.intent.action.ROBOT_SERVICE";
		final String ACTION_ROBOT_PACKAGE = "com.fourtech.voice";
		final String EXTRA_ROBOT_START_VALUE = "android.intent.extra.VALUE";
		final String EXTRA_ROBOT_START_FLAG = "android.intent.extra.FLAG";
		final int FLAG_BACKGROUND = 0x02;
		try {
			Intent service = new Intent(ACTION_ROBOT_SERVICE);
			service.setPackage(ACTION_ROBOT_PACKAGE);
			service.putExtra(EXTRA_ROBOT_START_VALUE, "");
			service.putExtra(EXTRA_ROBOT_START_FLAG, FLAG_BACKGROUND);
			mContext.startService(service);
		} catch (Throwable t) {
			Slog.e(TAG, "startRobotService() failed", t);
		}
	}

	private static void doExecSafely(String cmd) {
		try {
			Runtime.getRuntime().exec(cmd);
		} catch (Throwable t) {
		}
	}

	// 设置MCU关机时间
	private void setMcuPowerOffTime() {
		int powerOffTime = SystemProperties.getInt("persist.sys.acc_time", 24);
		writeToMcu(GID_POWEROFF, SID_POWEROFF, new byte[] { (byte) powerOffTime });
	}

	private void sendCloseSystemWindows(String reason) {
		if (ActivityManagerNative.isSystemReady()) {
			try {
				ActivityManagerNative.getDefault().closeSystemDialogs(reason);
			} catch (Throwable t) {
			}
		}
	}

	private static LongSparseArray<String> mJobQueue = new LongSparseArray<>();
	private static abstract class Job implements Runnable {
		String mName;

		public Job(String name) {
			mName = name;
		}

		@Override
		public void run() {
			if (runJob()) { // 加入打印任务队列
				synchronized (mJobQueue) {
					if (mJobQueue.size() > 60) mJobQueue.removeAt(0);
					mJobQueue.append(SystemClock.elapsedRealtime(), mName);
				}
			}
		}

		public void clearSelf() {
			synchronized (mJobQueue) {
				try {
					for (int i = mJobQueue.size() - 1; i >= 0; i--) {
						if (mJobQueue.valueAt(i).equals(mName)) {
							mJobQueue.removeAt(i);
						}
					}
				} catch (Throwable t) {
				}
			}
		}

		// 如果加入打印任务队列返回true
		public abstract boolean runJob();
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		sMe = null;
	}

	@Override
	protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
		final long ident = Binder.clearCallingIdentity();
		try {
			if (args != null && args.length > 0) {
				if ("mcu-reboot".equals(args[0])) {
					mMcuHeartbeatEnabled = false;
				}
			}
			dumpInternal(fout);
		} finally {
			Binder.restoreCallingIdentity(ident);
		}
	}

	private void dumpInternal(PrintWriter pw) {
		pw.println("AutoState (dumpsys " + AUTO_STATE_SERVICE + ")\n");

		synchronized (mLock) {
			pw.println("  Auto State:");
			pw.println("    Acc: " + (native_isAccOn2() ? "On" : "Over"));
			pw.println("    Reverse: " + (native_isReversing2() ? "On" : "Over"));
			pw.println("    Backlight: " + Display.getBacklight());
			if (!FAN_MODE.equals(FAN_MODE_CPUCTL)) {
				if (EleRadar.ENABLED) {
					pw.println("    EleRadar: " + (EleRadar.isAlive() ? "On" : "Off"));
				} else {
					pw.println("    BacklightPatch: " + (EleRadar.isAlive() ? "On" : "Off"));
				}
			} else {
				pw.println("    FanMode: " + (EleRadar.isAlive() ? "On" : "Off"));
			}
			pw.println("    mIsAccOn: " + mIsAccOn);
			pw.println("    mMcuHeartbeatEnabled: " + mMcuHeartbeatEnabled);
			pw.println("    TotalCpu: " + mProcessesMonitor.getTotalCpu() + "%");
			pw.println("    CPU TEMP: " + native_getCpuTemp());
			pw.println("    GPU TEMP: " + native_getGpuTemp());
			pw.println("    CPU FREQ: " + native_getCpuFreq());
			pw.println("");

			int i = 0;
			pw.println("  Job Queue:");
			for (int size = mJobQueue.size(); i < size; i++) {
				long time = mJobQueue.keyAt(i);
				String job = mJobQueue.valueAt(i);

				long d = (time / (1000 * 60 * 60 * 24));
				long h = (time / (1000 * 60 * 60)) % 24;
				long m = (time / (1000 * 60)) % 60;
				long s = (time / 1000) % 60;
				long u = (time % 1000);
				pw.println(String.format("    %2d: [%02d %02d:%02d:%02d.%03d] %s", (i+1), d, h, m, s, u, job));
			}

			long time = SystemClock.elapsedRealtime();
			long d = (time / (1000 * 60 * 60 * 24));
			long h = (time / (1000 * 60 * 60)) % 24;
			long m = (time / (1000 * 60)) % 60;
			long s = (time / 1000) % 60;
			long u = (time % 1000);
			pw.println(String.format("    %2d: [%02d %02d:%02d:%02d.%03d] < Now >", (i+1), d, h, m, s, u));
		}

		pw.println("");
	}

	private boolean mIsAccOn = false;
	private boolean native_isAccOn2() {
		// 防止isAccOn()接口被频繁调用时频繁打开和关闭驱动文件
		return (mIsAccOn = native_isAccOn());
	}

	private boolean mIsReversing = false;
	private boolean native_isReversing2() {
		// 防止isReversing()接口被频繁调用时频繁打开和关闭驱动文件
		return (mIsReversing = native_isReversing());
	}

	private native boolean native_isAccOn();
	private native boolean native_isReversing();
	private native int native_getCpuTemp();
	private native int native_getGpuTemp();
	private native int native_getCpuFreq();
}
