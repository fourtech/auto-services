package com.fourtech.mcu;

import static android.os.SystemProperties.get;
import static android.os.SystemProperties.getBoolean;
import static android.os.SystemProperties.getInt;
import static com.fourtech.autostate.AutoState.ACTION_REVERSE_OVER;

import java.io.DataOutputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.fourtech.autostate.AutoState;
import com.fourtech.autostate.AutoStateService;
import com.fourtech.gps.GpsService;
import com.fourtech.mcu.McuConstant;
import com.fourtech.mcu.McuSerialAdapter.McuSerialDataReceiver;
import com.fourtech.mcu.McuUpgradeService;
import com.fourtech.obd.ObdService;
import com.fourtech.mcu.Keyevent;

import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;

public class McuService extends IMcuService.Stub   implements  McuSerialDataReceiver,
McuConstant  {
	private static final String TAG = "McuService";
	private static final boolean DEBUG = getBoolean("persist.debug.mcu", false);
	private String mMcuFilename = "FOURTECH-HSJ";
	private static final String LOWPOWER = "Super low power restar systom";
	private static final String MPUACKTIME= " System response timeout ";
	private static final String COLDBOOT= " System cold boot ";
	private static final String SYSTEMPOWERON= " System power on  with";
    private String PowerReasonfileName = "PowerReasonLogFile.txt";
	// 波特率 默认是 115200 ...
	private static final int SERIAL_SPEED = getInt("ro.config.mcu.serial_speed", 115200);
	// 串口名 默认是 '/dev/ttyS0'...
	private static final String SERIAL_NAME = get("ro.config.mcu.serial_name", "/dev/ttyS0");
	private McuSerialAdapter mAdapter;
	private static McuService sMe;
   private Context mContext;
   private Boolean upgradeFlag = false;
   private Boolean mIsMcuUpgradeMode = false;
   private  Keyevent mKeyProcess;
   private byte[] mGpsBuff;
   private int GpsIndex;
   boolean   GpsGetIndex;
  private static int GpsDataL =600;
	 public McuService(Context context) {
		super();
		mContext =  context;
		sMe =this;
		try {
			mAdapter = new McuSerialAdapter(context, SERIAL_NAME, SERIAL_SPEED, "rw", 240, DEBUG, TAG);
			mAdapter.setSerialDataReceiver(this);
		    mKeyProcess = new Keyevent(mContext, McuService.this);
		    mGpsBuff = new byte[GpsDataL];
		    GpsIndex =0;
		    GpsGetIndex=false;
		} catch (Throwable t) {
			Slog.w(TAG, "Create RadarService error", t);
		}
		byte[] data= {1}; 
		write(GID_VERSION,SID_VERSION,data);
	}

	public static McuService getService() {
		return sMe;
	}


   public void writeToSDFile(final String msg) {
	   SystemProperties.set("ctl.start", "auto-preloader:" + msg);
   }  
   
	// 设置MCU关机电压（120-90电压为12v到9v）和心跳中断延时时间（单位为秒最小为10）
	private void setMcuPowerOffVoltage() {
		int powerOffVoltage = SystemProperties.getInt("persist.sys.poweroffvoltage", 100);
		int powerHeartBeatDelay = SystemProperties.getInt("persist.sys.powerHeartBeatDelay", 25);
		write(GID_POWEROFF_VOLTAGE, SID_POWEROFF_VOLTAGE, new byte[] { (byte) powerOffVoltage, (byte) powerHeartBeatDelay });
	}
	/**
	 * Write data to MCU
	 * 
	 * @param gid Group Id
	 * @param sid Sub Id
	 * @param data Data
	 */
	public void write(byte gid, byte sid, byte[] data) {
		if (mAdapter != null) {
			if (mIsMcuUpgradeMode) {
				if (gid == GID_UPGRADE)
					mAdapter.write(gid, sid, data);
			} else {
				 mAdapter.write(gid, sid, data);
			}
		}
	}

	private Boolean checksum(byte[] data) {
		int checksum = 0;
		int i;
		for (i = 2; i < (data.length - 1); i++) {
			checksum = (byte) checksum ^ data[i];
		}
		if ((checksum == data[i])) {
			return true;
		} else {
			Slog.i("McuService1", "checksum==========================faild" );
			return false;
		}
	}

	private void onGetMcuStatus(byte[] buffer) {
		StringBuffer buf = new StringBuffer("[ ");
		for (int i = 0, max = buffer.length - 1; i <= max; i++) {
			buf.append("");
			if (i < max) buf.append(String.format("%02x, ", buffer[i]));
			else buf.append(String.format("%02x ]", buffer[i]));
		}
		Slog.i(TAG, "onGetMcuStatus() data=" + buf.toString());
	}

	private void onGetGpsInfo(byte[] buffer) {
		GpsService.get().onNmeaData(buffer);
		if (DEBUG && false) {
			String dataStr = "";
			for (int i = 0; i < buffer.length; i++) {
				dataStr += "0x" + Integer.toHexString(buffer[i] & 0xFF) + " ";
			}
			// Slog.i("McuService1", "onReceive  mcu  data=" + dataStr);
			Slog.i(TAG, "buffer=" + new String(buffer));
		}
	}

	private void onGetPowerReason(byte[] buffer) {
		SimpleDateFormat sDateFormat = new SimpleDateFormat(
				"yyyy-MM-dd hh:mm:ss");
		String date = sDateFormat.format(new java.util.Date());
		if (buffer[2] == 1) {
			String power = "null";
			if (buffer[4] == 1) {
				power = "Vol Hight";
			} else if (buffer[4] == 2) {
				power = "Vol  Low";
			} else if (buffer[4] == 3) {
				power = "Vol Middle Low";
			} else if (buffer[4] == 4) {
				power = "Vol Super Low";
			}
			writeToSDFile(date + "    " + LOWPOWER + "    " + power);
		} else if (buffer[2] == 2) {
			writeToSDFile(date + "    " + MPUACKTIME);
		} else if (buffer[2] == 3) {
			writeToSDFile(date + "    " + COLDBOOT);
		} else if (buffer[2] == 4) {
			writeToSDFile(date + "    " + SYSTEMPOWERON + buffer[3]);
		}
	}
	
	private void onGetMcuVersion(byte[] buffer ) {
		byte[] buf = new byte[buffer.length-6];
		String str = "";
	  	 String filepath = "";
	  	 setMcuPowerOffVoltage() ;
		for (int i = 2; i < buffer.length-6; i++) {
			byte c = (byte) (0xff & buffer[i]);
			buf[i] = c > 0 ? c : 0x20;
		}
		try {
			str = new String(buf, "GBK").trim();
		} catch (UnsupportedEncodingException uee) {
			Log.e(TAG, "onGetMcuVersion error: ");
		}
		SystemProperties.set("hw.sys.mcu.version", str);
		filepath = SystemProperties.get("hw.sys.mcu.version", "");
		Slog.i(TAG, "hw.sys.mcu.version=" + filepath);

		if ((!upgradeFlag)&&(filepath.contains(mMcuFilename))) {
			upgradeFlag = true;
			McuUpgradeService.get().setMcuUpgradeFlag(upgradeFlag);
		} 
	}
	
/*data格式	head55  ,heada0 , index  ,length, gid , sid,  datan.......... , sum*/
	@Override
	public void onReceive(byte[] data) {
		if (DEBUG) {
			String dataStr = "";
			for (int i = 0; i < data.length; i++) {
				dataStr += "0x" + Integer.toHexString(data[i] & 0xFF) + " ";
			}
			Slog.i(TAG, "onReceive  mcu  data=" + dataStr);
		}
		if (checksum(data)) {
			byte[] buffer ;
			int dateLength;
			dateLength = data[3]&0xff;
			switch (data[4]) {
			case GID_MCU_STATE:
				mIsMcuUpgradeMode=false;
				buffer= new byte[data.length];
				 System.arraycopy(data, 4, buffer, 0, dateLength);
				onGetMcuStatus(buffer);
				break;
			case GID_KEY:
				buffer= new byte[data.length];
			    System.arraycopy(data, 4, buffer, 0, dateLength);
			   mKeyProcess.onReceivedKey(buffer);
			   break;
			case 	GID_VERSION:
				buffer= new byte[data.length];
				 System.arraycopy(data, 4, buffer, 0, dateLength);
				onGetMcuVersion(buffer);
				break;
			case GID_POWER_REASON:
				buffer= new byte[data.length];
				System.arraycopy(data, 4, buffer, 0, dateLength);
				onGetPowerReason(buffer);
				break;
			case GID_OBD:
				buffer= new byte[data.length-7];
				 System.arraycopy(data, 6, buffer, 0, dateLength-3);
				 ObdService.get(mContext).processPacket(buffer);
			     break;
			case GID_GPS:
				buffer= new byte[data.length-7];
				 System.arraycopy(data, 6, buffer, 0, dateLength-3);
				 onGetGpsInfo(buffer);
				break;
			case GID_UPGRADE:
				mIsMcuUpgradeMode = true;
				buffer= new byte[data.length];
				System.arraycopy(data, 6, buffer, 0, 1);
				if (DEBUG)
					Slog.i(TAG, "buffer[0]=" + buffer[0]);
				McuUpgradeService.get().processUpgradeCommand(buffer);
				break;
			default:
				break;
			}
		}

	}

	public static void writeToMcu(byte gid, byte sid, byte[] data) {
		if (getService() != null) {
			getService().write(gid, sid, data);
		}
	}

}