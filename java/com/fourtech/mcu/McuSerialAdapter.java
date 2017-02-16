package com.fourtech.mcu;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.fourtech.mcu.McuConstant;

import android.content.Context;
import android.hardware.SerialManager;
import android.hardware.SerialPort;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Slog;

public class McuSerialAdapter implements McuConstant{
	private static final int MSG_WRITE = 1;

	private final String TAG;
	private final boolean DEBUG;
	private final String MODE;
	private final String SERIAL_NAME;
	private final int SERIAL_SPEED;
	private final int BUFFER_CAPACITY;

	private Looper mWriteLooper;
	private WriteH mWriteHandler;
	private ReadT mReadThread;

	private Context mContext;
	private SerialPort mSerialPort;
	private int  getDateStep;
	private int  getDateIndex;
    private int  head_add;
    private int  head_lenth;
	private McuSerialDataReceiver mReceiver;

	public static interface  McuSerialDataReceiver {
		void onReceive(byte[] data);
	}

	public McuSerialAdapter(Context context, String name, int speed, String mode)
			throws IOException {
		this(context, name, speed, mode, 1024);
	}

	public McuSerialAdapter(Context context, String name, int speed, String mode,
			int bufferCapacity) throws IOException {
		this(context, name, speed, mode, bufferCapacity, false, null);
	}

	public McuSerialAdapter(Context context, String name, int speed, String mode,
			int bufferCapacity, boolean debug, String tag) throws IOException {
		mContext = context;
		SERIAL_NAME = name;
		SERIAL_SPEED = speed;
		BUFFER_CAPACITY = bufferCapacity;
		MODE = mode;
		DEBUG = debug;
	    getDateIndex=0;
		getDateStep=0;
		TAG = tag != null ? tag : "McuSerialAdapter(" + name + ")";
		reInitIfNeed();
	}

	public boolean reInitIfNeed() throws IOException {
		boolean isOk = false;
		Slog.i(TAG, "reInitIfNeed ");
		if (mReadThread != null) {
			try {
				isOk = true;
			} catch (Throwable t) {
			}
		} 

		if (!isOk) {
			SerialManager sm = (SerialManager) mContext.getSystemService(Context.SERIAL_SERVICE);
			if (DEBUG) Slog.i(TAG, "openMcuSerialPort(" + SERIAL_NAME + ", " + SERIAL_SPEED + ")");
			mSerialPort = sm.openSerialPort(SERIAL_NAME, SERIAL_SPEED);
			if (MODE == null || MODE.contains("w")) {
				if (mWriteHandler == null) {
					HandlerThread t = new HandlerThread("SerialAdapter-Thread");
					t.start();
					mWriteLooper = t.getLooper();
					mWriteHandler = new WriteH(mWriteLooper, this);
				}
			}

			if (MODE == null || MODE.contains("r")) {
				if (mReadThread == null) {
					mReadThread = new ReadT(mSerialPort);
					mReadThread.start();
				} else {
					mReadThread.stop();
					mReadThread = new ReadT(mSerialPort);
					mReadThread.start();
				}
			}
		}
		return isOk;
	}

	public void write(byte gid, byte sid,byte[] data) {
		if (mWriteHandler != null)
			mWriteHandler.obtainMessage(1, gid, sid, data).sendToTarget();
	}

	public void setSerialDataReceiver(McuSerialDataReceiver r) {
		mReceiver = r;
	}

	public void close() {
		if (mWriteHandler != null) {
			mWriteHandler.removeCallbacksAndMessages(null);
			mWriteHandler = null;
		}
		if (mWriteLooper != null) {
			mWriteLooper.quit();
			mWriteLooper = null;
		}
		if (mReadThread != null) {
			mReadThread.stop();
			mReadThread = null;
		}
	}

	private class WriteH extends Handler {
		ByteBuffer mBuffer;
		private byte mSeq;

		public WriteH(Looper looper, McuSerialAdapter adapter) {
			super(looper);
			mBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_WRITE: {
				if (msg.obj == null)
					break;
				byte seq = mSeq++;
				byte gid = (byte) msg.arg1;
				byte sid = (byte) msg.arg2;
				byte[] data = (byte[]) msg.obj;
				byte len = (byte) (3 + data.length); // GID, SID, DATA, CHECKSUM
				byte[] buffer = new byte[7 + data.length]; // HEAD0, HEAD1, SEQ, LEN, GID, SID, DATA, CHECKSUM

				buffer[0] = HEADA0;
				buffer[1] = HEAD55;
				buffer[2] = seq;
				buffer[3] = len;
				buffer[4] = gid;
				buffer[5] = sid;
				System.arraycopy(data, 0, buffer, 6, data.length);
				buffer[buffer.length - 1] = checksum(buffer);
				try {
					ByteBuffer mbuffer = (ByteBuffer) mBuffer.clear();
					mbuffer.put(buffer);
					mSerialPort.write(mbuffer, buffer.length);
					if (DEBUG) {
						String dataStr = "";
						for (int i = 0; i < buffer.length; i++) {
							dataStr += "0x" + Integer.toHexString(buffer[i] & 0xFF) + " ";
						}
						Slog.i(TAG, "write to   mcu  data=" + dataStr);
					}
				} catch (Throwable t) {
					Slog.e(TAG, "Write data:'" + new String(buffer) + "' failed.", t);
					try {
						reInitIfNeed();
					} catch (Throwable t2) {
					}
				}
				break;
			}
			default:
				break;
			}
		}
		// SEQ^LEN^GID^SID^DATA
		private byte checksum(byte[] buffer) {
			byte sum = 0;
			for (int i = 2; i < buffer.length - 1; i++) {
				sum = (byte) (sum ^ buffer[i]);
			}
			return sum;
		}
	}

	private class ReadT implements Runnable {

		private Thread mT = null;
		private SerialPort mSerialPort;
		private ByteBuffer mBuffer;
		private byte[] mCache;
       private int IndexF;
       private int IndexB;
		public ReadT(SerialPort serialPort) {
			mSerialPort = serialPort;
			mBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
			mCache = new byte[1024];
		}

		public void start() {
			if (mT == null) {
				mT = new Thread(this, "SerialAdapter-Thread");
				mT.start();
			}
		}

		public void stop() {
			if (mT != null) {
				mT.interrupt();
				mT = null;
			}
		}

		public boolean isRunnable() {
			return (mT != null);
		}

		public void run() {
			int errorCount = 0;
			if (DEBUG)
				Slog.i(TAG, "ReadT isRunning,,,");
			while (isRunnable()) {
				try {
					// Read data
					ByteBuffer buffer = (ByteBuffer) mBuffer.clear();
					int n = mSerialPort.read(buffer);
					// if (DEBUG) Slog.i(TAG, "Read from serialport n=" + n);
					if (n < 0)throw new IOException("n==-1");
					buffer.position(0);
					for (int i = 0; i < n; i++) {
						onGetData(buffer.get());
					}
					errorCount = 0;
				} catch (Throwable e) {
					Slog.i(TAG, "ReadT error.", e);
					errorCount++;
					if (errorCount > 64) {
						stop();
					}
					return;
				}
			}
		}


		private void onGetData(byte b) {

			switch (getDateStep) {
			case 0:
				if (b == HEAD55) {
					getDateStep = 1;
					getDateIndex = 1;
				}
				break;
			case 1:
				if (b == HEADA0) {
					getDateStep = 2;
					getDateIndex = 2;
				} else {
					getDateStep = 0;
					getDateIndex = 0;
				}
				break;
			case 2:
				head_add = b;
				getDateStep = 3;
				getDateIndex = 3;
				break;
			case 3:
				head_lenth = (b & 0xff);
				if (head_lenth > 3) {
					if (DEBUG)
						Slog.i(TAG, "onGetData=" + b + "head_lenth"
								+ head_lenth);
					getDateStep = 4;
					getDateIndex = 4;
					mCache[0] = HEAD55;
					mCache[1] = HEADA0;
					mCache[2] = (byte) head_add;
					mCache[3] = (byte) head_lenth;
				} else {
					if (DEBUG)
						Slog.i(TAG, "onGetData=" + b + "head_lenth"
								+ head_lenth);
					getDateStep = 0;
				}
				break;
			case 4:
				getDateIndex = getDateIndex & 0xff;
				if (getDateIndex == head_lenth + 3) {
					mCache[getDateIndex] = b;
					byte[] buffer = new byte[getDateIndex + 1];
					// if (DEBUG) Slog.i(TAG, "IndexF==" + IndexF + "IndexB==" +
					// IndexB +"datalenth==" + getDateIndex);
					System.arraycopy(mCache, 0, buffer, 0, getDateIndex + 1);
					try {
						mReceiver.onReceive(buffer);
						if (DEBUG)
							Slog.i(TAG, "onGetData=" + b
									+ "getDateStep===2=====" + getDateIndex);
						getDateStep = 0;
						getDateIndex = 0;
					} catch (Exception e) {
						getDateStep = 0;
						getDateIndex = 0;
						Slog.e(TAG, "Call onSerialCommand() failed", e);
					}
				} else {
					mCache[getDateIndex++] = b;
				}
				break;
			default:
				if (DEBUG)
					Slog.i(TAG, "onGetData=" + b + "getDateStep===1====="
							+ getDateStep);
				getDateStep = 0;
				getDateIndex = 0;
				break;
			}

		}
	}

}