package com.fourtech.gps;

import static android.os.SystemProperties.getBoolean;
import static com.fourtech.mcu.McuConstant.GID_GPS;
import static com.fourtech.mcu.McuConstant.SID_GPS;
import static com.fourtech.mcu.McuService.writeToMcu;

import java.util.ArrayList;
import java.util.List;

import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcel;
import android.util.Slog;

public class GpsService extends Binder {
	private static final String TAG = "GpsService-4t";
	private static final boolean DEBUG = getBoolean("persist.debug.cgps", false);

	public static final String GPS_SERVICE = "auto-gps";
	private static final int CMD_SET_CALLBACK    = 0x01;
	private static final int CMD_REMOVE_CALLBACK = 0x02;
	private static final int CMD_RESET           = 0x03;
	private static final int CMD2CLIENT_NMEA     = 0x86;

	private int mIndex;
	private int mCheck;
	private byte[] mCache;
	private Handler mHandler;

	private static class Client {
		public int errCount;
		public IBinder remote;

		public Client(IBinder binder) {
			errCount = 0;
			remote = binder;
		}
	}

	private List<Client> mClients = new ArrayList<>();
	private List<Client> mClientDels = new ArrayList<>();

	private GpsService() {
		super();
		mCache = new byte[1024];
		HandlerThread t;
		(t = new HandlerThread("auto-gps-service")).start();
		mHandler = new Handler(t.getLooper()/* , null, true */) {
			@Override
			public void handleMessage(Message msg) {
				if (msg != null && msg.obj instanceof byte[]) {
					byte[] nmea = (byte[]) msg.obj;
					for (int i = 0; i < nmea.length; i++) {
						if (nmea[i] == '\r' || nmea[i] == '\n')
							continue;

						if (nmea[i] == '$') {
							mIndex = 0;
							mCheck = Integer.MAX_VALUE;
						} else if (nmea[i] == '*') {
							mCheck = mIndex;
						}

						if (mIndex >= 0 && mIndex < mCache.length) {
							mCache[mIndex] = nmea[i];
						}

						if (mIndex == (mCheck + 2)
								&& mCache[0] == '$'
								&& mIndex < mCache.length-1) {
							int length = (mIndex + 1);
							byte[] nmeaToClient = new byte[length];
							System.arraycopy(mCache, 0, nmeaToClient, 0, length);
							sendToClient(nmeaToClient);
							mIndex = 0;
							mCache[0] = 0;
							mCheck = Integer.MAX_VALUE;
						} else {
							// 累加
							mIndex++;
						}
					}
				}
			}
		};
	}

	private static GpsService sMe;

	public static GpsService get() {
		return sMe != null ? sMe : (sMe = new GpsService());
	}

	public void onNmeaData(byte[] nmea) {
		// mHandler.removeCallbacksAndMessages(null);
		mHandler.obtainMessage(1, nmea).sendToTarget();
	}

	@Override
	protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
		try {
			return onTransact2(code, data, reply, flags);
		} catch (Throwable t) {
			Slog.w(TAG, "onTransact()", t);
		}
		return false;
	}

	protected boolean onTransact2(int code, Parcel data, Parcel reply, int flags) {

		switch (code) {
		case CMD_SET_CALLBACK: {
			IBinder client = data.readStrongBinder();
			mClients.add(new Client(client));
			if (reply != null) {
				reply.writeInt(client != null ? 88 : 66);
			}
			break;
		}
		case CMD_REMOVE_CALLBACK: {
			IBinder client = data.readStrongBinder();
			if (mClients.remove(client)) {
				reply.writeInt(88);
			} else {
				reply.writeInt(66);
			}
			break;
		}
		case CMD_RESET: {
			writeToMcu(GID_GPS, SID_GPS, new byte[] {
					(byte) 0xB5, 0x62, 0x06, 0x04, 0x04, 0x00, (byte) 0xFF,
					(byte) 0x87, 0x01, 0x00, (byte) 0x95, (byte) 0xF7
					});
			if (reply != null)
				reply.writeInt(88);
			break;
		}
		default:
			break;
		}

		return true;
	}

	private void sendToClient(byte[] nmea) {
		if (DEBUG) Slog.i(TAG, "sendToClient() nmea=" + new String(nmea));
		for (int i = 0, s = mClients.size(); i < s; i++) {
			Client c = mClients.get(i);
			if (c != null && c.remote != null) {
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				data.writeByteArray(nmea);
				try {
					c.remote.transact(CMD2CLIENT_NMEA, data, reply, 0);
					int result = reply.readInt();
					if (DEBUG) Slog.i(TAG, "sendToClient() result" + result);
					data.recycle();
					reply.recycle();
					c.errCount = 0;
				} catch (Throwable t) {
					c.errCount++;
					if (c.errCount > 10) {
						mClientDels.add(c);
					}
					Slog.w(TAG, "onNmeaData()", t);
				}
			}
		}

		// delete error client
		int delSize = mClientDels.size();
		if (delSize > 0) {
			for (int i = 0; i < delSize; i++) {
				mClients.remove(mClientDels.get(i));
			}
			mClientDels.clear();
		}
	}
}