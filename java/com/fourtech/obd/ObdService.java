package com.fourtech.obd;

import java.util.ArrayList;
import java.util.List;

import com.fourtech.mcu.McuConstant;
import com.fourtech.mcu.McuService;

import android.content.Context;
import android.os.RemoteException;
import android.util.Slog;

public class ObdService extends IObdService.Stub implements McuConstant {
	public static final String TAG = "ObdService";
	private static ObdService sMe;

	public static ObdService get(Context context) {
		return sMe != null ? sMe : (sMe = new ObdService(context));
	}

	private List<IObdCallback> mCallbacks;

	public ObdService(Context context) {
		sMe = this;
	}

	@Override
	public void registerCallback(IObdCallback callback) {
		if (mCallbacks == null) {
			mCallbacks = new ArrayList<IObdCallback>();
		}

		if (!mCallbacks.contains(callback)) {
			mCallbacks.add(callback);
		}
	}

	@Override
	public void unregisterCallback(IObdCallback callback)
			throws RemoteException {
		if (mCallbacks != null) {
			mCallbacks.remove(callback);
		}
	}

	public void sendObdData(byte[] data) {
		McuService.getService().write(GID_OBD, SID_UPGRADE, data);
	}

	public void processPacket(byte[] data) {
		int size = (mCallbacks != null) ? mCallbacks.size() : 0;
		for (int i = 0; i < size; i++) {
			try {
				mCallbacks.get(i).onReceived(data);
			} catch (RemoteException e) {
			}
		}
	}

	@Override
	public void sendData(byte[] data) throws RemoteException {
		// TODO Auto-generated method stub
		McuService.getService().write(GID_OBD, SID_OBD, data);

	}

}