package com.fourtech.mcu;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.R.string;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;


public class McuUpgradeService extends IMcuUpgradeService.Stub implements McuConstant {
	private static final String TAG = "McuUpgradeService-mcu";
	public static final boolean DEBUG =true;
	private File mFile =new File("");
	private String mFilename = "FOURTECH_HSJ";
	private Context mContext;
	private List<IUpgradeCallback> mCallbacks;
   Boolean mcuUpFlag= false;
	private static McuUpgradeService sMe;

	public static McuUpgradeService get() {
		return sMe;
	}

	public McuUpgradeService(Context context) {
		super();
		sMe = this;
		mContext = context;
		mCallbacks = new ArrayList<IUpgradeCallback>();
		 IntentFilter filter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
		 mContext.registerReceiver(mReceve, filter);
        
	}
	BroadcastReceiver mReceve =new BroadcastReceiver(){
		public void onReceive(Context arg0, Intent arg1) {
			if(mcuUpFlag){
				mcuUpgrade();
			}
			mContext.unregisterReceiver(mReceve);
		};
	};

	public void setMcuUpgradeFlag(Boolean flag){
		mcuUpFlag = flag;
	}
	
	public void mcuUpgrade() {
		Log.i(TAG, "mcuUpgrade======");
		if (getUpgradeFlag()) {
			try {
					Intent intent = new Intent(Intent.ACTION_MAIN);
						intent.addCategory(Intent.CATEGORY_LAUNCHER);
						ComponentName cn = new ComponentName("com.example.McuUpgrde", "com.example.McuUpgrde.McuUpgrade"); 
						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.putExtra("mcuupgrade", true);
						intent.setComponent(cn);
						mContext.startActivity(intent);
			} catch (Throwable t) {
				Slog.w(TAG, "start mcuUpgrade  failed", t);
			}
		}
	}
	

	private int getOldVersionData(){
	  	  String filepath = "";
	      filepath = SystemProperties.get("hw.sys.mcu.version", "");
	      getStingData(filepath);
		  return getStingData(filepath);
	}
	
	private int getStingData(String name) {
		String filenumber = "";
		int number;
		String regEx = "[^0-9]";
		Pattern p = Pattern.compile(regEx);
		Matcher m = p.matcher(name);
		filenumber = m.replaceAll("").trim();
		number = Integer.parseInt(filenumber);
		return number;
	}

	private Boolean getUpgradeFlag() {
		String filepath = "";
		int newVersionData;
		int oldVersionData;
		File f = new File("/system/");
		File[] files = f.listFiles();
		for (int i = 0; i < files.length; i++) {
			filepath = files[i].getName();
			if (filepath.contains(mFilename)) {
				newVersionData = getStingData(filepath);
				oldVersionData = getOldVersionData();
				Log.i(TAG, "newVersionData======" + newVersionData);
				Log.i(TAG, "oldVersionData======" + oldVersionData);
				if (newVersionData > oldVersionData) {
					return true;
				}
			}
		}
		return false;
	}
	
	@Override
	public void registerCallback(IUpgradeCallback callback) {
		if (!mCallbacks.contains(callback)) {
			mCallbacks.add(callback);
		}
	}

	@Override
	public void unregisterCallback(IUpgradeCallback callback) {
		mCallbacks.remove(callback);
	}

	@Override
	public void requestUpgrade() {
		McuService.getService().write(GID_UPGRADE, SID_UPGRADE, new byte[] { (byte) 0x88 });
	}

	@Override
	public void sendUpgradeData(byte[] data) {
		McuService.getService().write(GID_UPGRADE, SID_UPGRADE, data);
	}
	

	public void processUpgradeCommand(final byte[] data) {
		if (data.length == 0)
			return;
/*		if (data.length > 0 && (data[0] & 0xFF) == 0x87) {
			Intent error = new Intent(McuUpgradeManager.ACTION_UPGRADE_ERROR);
			
			error.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			mContext.startActivity(error);
			return;
		}*/

		for (int i = 0, size = mCallbacks.size(); i < size; i++) {
			try {
				mCallbacks.get(i).onEvent(data);
			} catch (Exception e) {
			}
		}
	}

}