package com.fourtech.hardware;

import java.io.IOException;
import java.nio.ByteBuffer;

import android.content.Context;
import android.hardware.SerialManager;
import android.hardware.SerialPort;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Slog;

public class SerialAdapter {
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
	private SerialDataProcessor mProcessor;

	public static class SerialDataFrame {
		public int length;
		public int position;
		public byte[] cache;
		public byte[] buffer;
	}

	public static interface SerialDataProcessor {
		boolean onByte(SerialDataFrame frame, byte data);
		void onFrame(SerialDataFrame frame);
	}

	public SerialAdapter(Context context, String name, int speed, String mode)
			throws IOException {
		this(context, name, speed, mode, 1024);
	}

	public SerialAdapter(Context context, String name, int speed, String mode,
			int bufferCapacity) throws IOException {
		this(context, name, speed, mode, bufferCapacity, false, null);
	}

	public SerialAdapter(Context context, String name, int speed, String mode,
			int bufferCapacity, boolean debug, String tag) throws IOException {
		mContext = context;
		SERIAL_NAME = name;
		SERIAL_SPEED = speed;
		BUFFER_CAPACITY = bufferCapacity;
		MODE = mode;
		DEBUG = debug;
		TAG = tag != null ? tag : "SerialAdapter(" + name + ")";
		reInitIfNeed();
	}

	public boolean reInitIfNeed() throws IOException {
		boolean isOk = false;
		if (mReadThread != null) {
			try {
				ByteBuffer buffer = ByteBuffer.allocateDirect(2);
				buffer.put(new byte[] { '\r', '\n' });
				mSerialPort.write(buffer, 2);
				isOk = true;
			} catch (Throwable t) {
			}
		}

		if (!isOk) {
			SerialManager sm = (SerialManager) mContext.getSystemService(Context.SERIAL_SERVICE);
			if (DEBUG) Slog.i(TAG, "openSerialPort(" + SERIAL_NAME + ", " + SERIAL_SPEED + ")");
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

	public void write(byte[] data) {
		if (mWriteHandler != null)
			mWriteHandler.obtainMessage(MSG_WRITE, data).sendToTarget();
	}

	public void setSerialDataProcessor(SerialDataProcessor p) {
		mProcessor = p;
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

		public WriteH(Looper looper, SerialAdapter adapter) {
			super(looper);
			mBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_WRITE: {
				if (msg.obj == null)
					break;

				byte[] data = (byte[]) msg.obj;

				try {
					ByteBuffer buffer = (ByteBuffer) mBuffer.clear();
					buffer.put(data);
					mSerialPort.write(buffer, data.length);
					if (DEBUG) Slog.i(TAG, "write [" + new String(data) + "] to serialport success!");
				} catch (Throwable t) {
					Slog.e(TAG, "Write data:'" + new String(data) + "' failed.", t);
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
	}

	private class ReadT implements Runnable {

		private Thread mT = null;
		private SerialPort mSerialPort;
		private ByteBuffer mBuffer;
		private int mCount = 0;
		private byte[] mCache;
		private SerialDataFrame mFrame;

		public ReadT(SerialPort serialPort) {
			mSerialPort = serialPort;
			mBuffer = ByteBuffer.allocateDirect(BUFFER_CAPACITY);
			mCache = new byte[BUFFER_CAPACITY];
			mFrame = new SerialDataFrame();
			mFrame.cache = mCache;
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
			if (DEBUG) Slog.i(TAG, "ReadT isRunning,,,");
			while (isRunnable()) {
				try {
					// Read data
					ByteBuffer buffer = (ByteBuffer) mBuffer.clear();
					int n = mSerialPort.read(buffer);
					if (DEBUG) Slog.i(TAG, "Read from serialport n=" + n);
					if (n < 0) throw new IOException("n==-1");

					buffer.position(0);
					for (int i = 0; i < n; i++) {
						onByte(buffer.get());
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

		private void onByte(byte data) {
			if (mProcessor != null) {
				if (mProcessor.onByte(mFrame, data)) {
					if (mFrame.length > 0) {
						mFrame.buffer = new byte[mFrame.length];
						System.arraycopy(mFrame.cache, mFrame.position, mFrame.buffer, 0, mFrame.length);
						try {
							mProcessor.onFrame(mFrame);
						} catch (Exception e) {
							Slog.e(TAG, "Call onFrame() failed", e);
						}

						// New Frame
						mFrame = new SerialDataFrame();
						mFrame.cache = mCache;
					}
				}
				return;
			}

			if ('\n' == data)
				return;

			if (mCount >= BUFFER_CAPACITY) {
				mCount = 0;
			}

			if ('\r' == data) {
				if (mCount > 0) {
					mFrame.buffer = new byte[mFrame.length];
					System.arraycopy(mCache, 0, mFrame.buffer, 0, mCount);
					try {
						mProcessor.onFrame(mFrame);
					} catch (Exception e) {
						Slog.e(TAG, "Call onFrame() failed", e);
					}

					// New Frame
					mCount = 0;
					mFrame = new SerialDataFrame();
					mFrame.cache = mCache;
				}
				return;
			}

			mCache[mCount++] = data;
		}

	}

}