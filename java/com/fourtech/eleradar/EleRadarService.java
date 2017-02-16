package com.fourtech.eleradar;

import static android.os.SystemProperties.get;
import static android.os.SystemProperties.getBoolean;
import static android.os.SystemProperties.getInt;
import static com.fourtech.hardware.EleRadar.ENABLED;
import static com.fourtech.hardware.EleRadar.FAN_MODE;
import static com.fourtech.hardware.EleRadar.FAN_MODE_CPUCTL;
import static com.fourtech.hardware.EleRadar.FAN_MODE_MCUCTL;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import com.fourtech.hardware.EleRadar;
import com.fourtech.hardware.SerialAdapter;
import com.fourtech.hardware.SerialAdapter.SerialDataFrame;
import com.fourtech.hardware.SerialAdapter.SerialDataProcessor;

public class EleRadarService extends IEleRadarService.Stub implements
		SerialDataProcessor {
	private static final String TAG = "EleRadarService";
	private static final boolean DEBUG = getBoolean("persist.debug.eleradar", false);

	private static final byte HEAD_AA = (byte) 0xAA;
	private static final byte HEAD_55 = (byte) 0x55;

	private static final byte BAND_X     = (byte) 0x40;
	private static final byte BAND_K     = (byte) 0x50;
	private static final byte BAND_Ka    = (byte) 0x58;
	private static final byte BAND_Ku    = (byte) 0x48;
	private static final byte BAND_LASER = (byte) 0x80;

	// 波特率 默认是 4800 ...
	private static final int SERIAL_SPEED = getInt("ro.config.eleradar.serial_speed", 9600);
	// 串口名 默认是 '/dev/ttyS1'...
	private static final String SERIAL_NAME = get("ro.config.eleradar.serial_name", "/dev/ttyS1");

	private SerialAdapter mAdapter;
	private Map<IBinder, IEleRadarCallback> mCallbacks = new HashMap<IBinder, IEleRadarCallback>();

	public EleRadarService(Context context) {
		super();
		try {
			if (ENABLED && !FAN_MODE.equals(FAN_MODE_CPUCTL)) {
				mAdapter = new SerialAdapter(context, SERIAL_NAME, SERIAL_SPEED, "r", 16, DEBUG, TAG);
				mAdapter.setSerialDataProcessor(this);
			}
		} catch (Throwable t) {
			Slog.w(TAG, "Create EleRadarService error", t);
		}
	}

	@Override
	public void start() throws RemoteException {
		if (ENABLED && !FAN_MODE.equals(FAN_MODE_CPUCTL)) {
			EleRadar.start();
			if (DEBUG) Slog.i(TAG, "start(), isAlive=" + isAlive());
		}
	}

	@Override
	public void stop() throws RemoteException {
		if (ENABLED && !FAN_MODE.equals(FAN_MODE_CPUCTL)) {
			EleRadar.stop();
			if (DEBUG) Slog.i(TAG, "stop(), isAlive=" + isAlive());
		}
	}

	@Override
	public boolean isAlive() throws RemoteException {
		boolean isAlive = EleRadar.isAlive();
		if (DEBUG) Slog.i(TAG, "isAlive() return " + isAlive);
		return isAlive;
	}

	@Override
	public void addCallback(IBinder callback) throws RemoteException {
		if (callback == null)
			return;

		if (!mCallbacks.containsKey(callback)) {
			mCallbacks.put(callback, IEleRadarCallback.Stub.asInterface(callback));
			Slog.i(TAG, "addCallback() callback=" + callback + ", size=" + mCallbacks.size());
		}

	}

	@Override
	public void removeCallback(IBinder callback) throws RemoteException {
		mCallbacks.remove(callback);
		Slog.i(TAG, "removeCallback() callback=" + callback + ", size=" + mCallbacks.size());
	}

	@Override
	public void startAsBacklightPatch() throws RemoteException {
		EleRadar.start();
		FAN_MODE = FAN_MODE_MCUCTL;
		Slog.i(TAG, "startAsBacklightPatch()");
	}

	@Override
	public void stopAsBacklightPatch() throws RemoteException {
		EleRadar.stop();
		Slog.i(TAG, "stopAsBacklightPatch()");
	}

	@Override
	public boolean onByte(SerialDataFrame frame, byte data) {
		if (DEBUG) {
			Slog.i(TAG,
					"onByte() frame.position=" + frame.position
					+ ", frame.cache["+ frame.position + "]=0x" + Integer.toHexString(frame.cache[frame.position] & 0xFF)
					+ ", data=" + Integer.toHexString(data & 0xFF));
		}

		// 帧头我们取0xAA, 0x55为兼容帧忽略
		if (data == HEAD_AA || data == HEAD_55) {
			int position = 0;
			frame.position = position;
			frame.cache[position] = data;
			return false;
		}

		// 如果第一Byte为0xAA, 则第二Byte是信号
		if (frame.position == 0 && frame.cache[0] == HEAD_AA) {
			int position = 1;
			frame.position = position;
			frame.cache[position] = data;
			frame.length = 1;
			return true;
		}

		return false;
	}

	@Override
	public void onFrame(SerialDataFrame frame) {
		if (DEBUG) Slog.i(TAG, "onFrame() buffer[0]=" + Integer.toHexString(frame.buffer[0] & 0xFF));

		byte frequency = (byte)(frame.buffer[0] & 0xFC);
		byte intensity = (byte)(frame.buffer[0] & 0x03);
		if (intensity == 3) intensity = 2;

		Frequency freq;
		switch (frequency) {
		case BAND_X:     freq = Frequency.X_BAND;     break;
		case BAND_K:     freq = Frequency.K_BAND;     break;
		case BAND_Ka:    freq = Frequency.Ka_BAND;    break;
		case BAND_Ku:    freq = Frequency.Ku_BAND;    break;
		case BAND_LASER: freq = Frequency.LASER_BEAM; break;
		default:
			if (DEBUG) Slog.i(TAG, "onFrame() bad data, return");
			return;
		}

		// 通知界面更新
		final Iterator<IBinder> it;
		it = mCallbacks.keySet().iterator();
		while (it.hasNext()) {
			try {
				mCallbacks.get(it.next()).onReceive(freq.ordinal(), intensity);
			} catch (Throwable t) {
			}
		}
	}

}