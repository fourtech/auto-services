package com.fourtech.fm;

import static android.os.SystemProperties.getInt;
import static android.os.SystemProperties.set;
import android.os.Binder;
import android.os.RemoteException;

import com.fourtech.autostate.AutoStateService;
import com.fourtech.hardware.FmTransmitter;

public class FmTransmitterService extends IFmTransmitterService.Stub {

	private static final String KEY_LAST_MODE = "persist.sys.fm_last_mode";

	public FmTransmitterService() {
		super();
		FmTransmitter.get();
		FmTransmitter.sIsLastPowerOn = (getInt(KEY_LAST_MODE, 0) == 1);
	}

	@Override
	public boolean setPowerOn() throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			if (FmTransmitter.get().setPowerOn()
					&& AutoStateService.get().isAccOn()) {
				FmTransmitter.sIsLastPowerOn = true;
				set(KEY_LAST_MODE, "1");
				return true;
			}
			return false;
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public boolean setPowerOff() throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			if (FmTransmitter.get().setPowerOff()
					&& AutoStateService.get().isAccOn()) {
				FmTransmitter.sIsLastPowerOn = false;
				set(KEY_LAST_MODE, "0");
			}
			return true;
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public boolean setFrequency(float freq) throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			return FmTransmitter.get().setFrequency(freq);
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public boolean setVol(int vol) throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			return FmTransmitter.get().setVol(vol);
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public boolean setSignaln(int sig) throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			return FmTransmitter.get().setSignaln(sig);
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public boolean setDivergence(int div) throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			return FmTransmitter.get().setDivergence(div);
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public int getNowMode() throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			return FmTransmitter.get().getNowMode();
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

	@Override
	public int getChipId() throws RemoteException {
		long callingId = Binder.clearCallingIdentity();
		try {
			return FmTransmitter.get().getChipId();
		} finally {
			Binder.restoreCallingIdentity(callingId);
		}
	}

}
