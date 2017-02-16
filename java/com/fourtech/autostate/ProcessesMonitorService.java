package com.fourtech.autostate;

import static android.os.SystemProperties.getBoolean;
import static android.os.SystemProperties.getInt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

public class ProcessesMonitorService implements Runnable {
	private static final String TAG = "ProcessesMonitorService";
	private static final boolean DEBUG = getBoolean("persist.debug.top_monitor", true);
	private static final boolean DEBUGV = DEBUG && getBoolean("persist.debug.top_monitor", false);

	// private static final String TOP_PATTERN = "\\s*(\\d{1,5})\\s+(\\d{1,2})\\s+(\\d{1,5})‱\\s+([A-Z])\\s+(\\d{1,5})\\s+(\\d{1,6})K\\s+(\\d{1,6})K\\s+([bf]g)\\s+([a-z0-9_]+)\\s+(.+)";
	private static final String TOP_PATTERN = "\\s*(\\d+)\\s+(\\d+)\\s+(\\d+)‱\\s+([A-Z])\\s+(\\d+)\\s+(\\d+)K\\s+(\\d+)K\\s+([bf]g)\\s+([a-z0-9_]+)\\s+(.+)";
	private static final Matcher TOP_MATCHER = Pattern.compile(TOP_PATTERN).matcher("");
	private static final int CPU_TOTAL2MONITOR = getInt("persist.sys.cpu_total2monitor", 20); // how many cpu to kill or clear
	private static final int NUM_PROC2MONITOR = getInt("persist.sys.num_proc2monitor", 0); // 0 for All
	private static final String CMD = "/system/bin/top -M -m " + NUM_PROC2MONITOR;

	private static final List<A> AS = new ArrayList<>();

	private int mTotalCpu;
	private Context mContext;
	private Thread mMonitorService;
	private boolean mIsRunning = false;;

	public ProcessesMonitorService(Context context) {
		mContext = context;
		if (DEBUG) Slog.i(TAG, "ProcessesMonitorService()");
	}

	@SuppressLint("SdCardPath")
	public void start() {
		if (DEBUG) Slog.i(TAG, "start()");
		mIsRunning = true;
		if (mMonitorService == null) {
			// init as
			File procListFile = new File("/system/etc/top-monitor-proc.list");
			if (procListFile.exists()) {
				BufferedReader reader = null;
				try {
					reader = new BufferedReader(new FileReader(procListFile));
					for (String line = null; (line = reader.readLine()) != null;) {
						A a = new A(line);
						AS.add(a);

						if (DEBUG) Slog.i(TAG, "start() add " + a + " to AS.");
					}
				} catch (Throwable t) {
					Log.w(TAG, "Read top-monitor-proc.list failed", t);
				} finally {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable t) {
						}
					}
				}
			}

			// start monitor service
			mMonitorService = new Thread(this, "top-monitor-service");
			mMonitorService.setDaemon(true);
			mMonitorService.start();
		}
	}

	public void stop() {
		if (DEBUG) Slog.i(TAG, "stop()");
		mIsRunning = false;
	}

	@Override
	public void run() {
		BufferedReader inReader = null;
		try {
			Process topProc = Runtime.getRuntime().exec(CMD);

			InputStream in = topProc.getInputStream();
			inReader = new BufferedReader(new InputStreamReader(in));

			String line = "";
			ArrayList<ProcessInfo> ps = new ArrayList<>();
			long lastHandleTime = SystemClock.uptimeMillis();
			while ((line = inReader.readLine()) != null) {
				if (!mIsRunning) {
					ps.clear();
					continue;
				}

				// parse
				ProcessInfo pi = ProcessInfo.parseInfo(line);
				if (pi != null) {
					ps.add(pi);
				} else if (ps.size() > 0) {
					long now = SystemClock.uptimeMillis();
					if (now - lastHandleTime >= 3000) {
						handldProcessInfoList(ps);
						lastHandleTime = now;
					} else {
						// 不可能发生，如果发生了应该是出异常了
						Slog.w(TAG, "run() receive full info too fast ( in " + (now-lastHandleTime) + " millis ).");
					}
					ps.clear();
				}
			}
		} catch (Throwable t) {
			Slog.w(TAG, "run() failed.", t);
		} finally {
			if (inReader != null) {
				try {
					inReader.close();
				} catch (Throwable tt) {
					Slog.w(TAG, "run() close reader failed.", tt);
				}
			}
		}
	}

	public int getTotalCpu() {
		return mTotalCpu;
	}

	private Integer getActionTimes(A a) {
		Integer times = mActionTimes.get(a.hashCode());
		return (times != null) ? times : Integer.valueOf(0);
	}

	private void updateActionTimes(A a, int times) {
		mActionTimes.put(a.hashCode(), Integer.valueOf(times));
	}

	private List<A> mToCleanList = new ArrayList<>();
	private SparseArray<Integer> mActionTimes = new SparseArray<>();
	private void handldProcessInfoList(List<ProcessInfo> ps) {
		mToCleanList.clear();
		final int size = ps != null ? ps.size() : 0;

		if (DEBUGV) {
			for (int i = 0; i < size; i++) {
				Slog.i(TAG, "handldProcessInfoList() ps[" + i + "]=" + ps.get(i));
			}
		}

		int totalCpu = 0;
		for (int i = 0; i < size; i++) {
			ProcessInfo pi = ps.get(i);
			totalCpu += pi.CPU;
			for (int j = 0; j < AS.size(); j++) {
				if (pi.Name.matches(AS.get(j).name)) {
					if (pi.CPU >= (AS.get(j).cpu * 100)) {
						mToCleanList.add(AS.get(j).newForProcessInfo(pi));
					} else {
						// 归零
						updateActionTimes(AS.get(j), 0);
						if (DEBUGV) Slog.i(TAG, "handldProcessInfoList() " + pi.Name + " cpu=" + pi.CPU + ", recount times");
					}
				}
			}
		}
		totalCpu += 50;
		mTotalCpu = (totalCpu/100);

		if (DEBUGV) {
			Slog.i(TAG, "handldProcessInfoList() totalCpu=" + totalCpu + "("
					+ mTotalCpu + "%), CPU_TOTAL2MONITOR=" + CPU_TOTAL2MONITOR);
		}

		if (mTotalCpu > CPU_TOTAL2MONITOR) {
			if (mToCleanList.size() > 0) {
				ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
				@SuppressWarnings("deprecation")
				List<RunningTaskInfo> taskInfo = am.getRunningTasks(2);
				List<RunningAppProcessInfo> rps = am.getRunningAppProcesses();
				O: for (int i = 0, ts = mToCleanList.size(); i < ts; i++) {
					A a = mToCleanList.get(i);
					ProcessInfo pi = a.proc;
					for (int j = 0, rpss = rps.size(); j < rpss; j++) {
						RunningAppProcessInfo rp = rps.get(j);
						if (pi.Name.equals(rp.processName)) {
							if (!taskInfo.isEmpty()) {
								for (int k = 0; k < taskInfo.size(); k++) {
									RunningTaskInfo rti = taskInfo.get(k);
									if (rp.pkgList != null) {
										for (int l = 0; l < rp.pkgList.length; l++) {
											String curRunningPkg = rti.baseActivity.getPackageName();
											if (DEBUGV) Slog.i(TAG, "handldProcessInfoList() rp.pkgList["+l+"]= " + rp.pkgList[l] + ", curRunningPkg=" + curRunningPkg);
											if ((""+curRunningPkg).matches(a.name)) {
												updateActionTimes(a, 0);
												if (DEBUG) Slog.i(TAG, "handldProcessInfoList() " + rp.pkgList[l] + " is running, continue and recount times");
												continue O;
											}
										}
									}
								}
							}

							int times = getActionTimes(a);
							if (times <= 1) {
								updateActionTimes(a, times + 1);
								if (DEBUG) Slog.i(TAG, "handldProcessInfoList() " + rp.processName + " times=" + times);
								continue O;
							}
							updateActionTimes(a, 0);

							// clear and kill
							if (rp.pkgList != null) {
								for (int l = 0; l < rp.pkgList.length; l++) {
									if (DEBUGV) Slog.i(TAG, "handldProcessInfoList() " + rp.pkgList[l] + " action=" + a.action);
									if (a.action.contains("clear")) {
										am.clearApplicationUserData(rp.pkgList[l], null);
										if (DEBUG) Slog.i(TAG, "handldProcessInfoList() clear " + rp.pkgList[l]);
									}
									if (a.action.contains("kill")) {
										am.forceStopPackage(rp.pkgList[l]);
										if (DEBUG) Slog.i(TAG, "handldProcessInfoList() kill " + rp.pkgList[l]);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private static class A {
		int cpu;
		String name;
		String action;
		ProcessInfo proc;

		A() {
		}

		A(String line) {
			Matcher m = Pattern.compile("(\\d+),([^,]+),(.+)").matcher(line);
			if (m.matches()) {
				cpu = Integer.parseInt(m.group(1));
				name = m.group(2);
				action = m.group(3);
			}
		}

		A newForProcessInfo(ProcessInfo proc) {
			A a = new A();
			a.cpu = cpu;
			a.name = name;
			a.action = action;
			a.proc = proc;
			return a;
		}

		@Override
		public int hashCode() {
			return name != null ? (name.hashCode()^123) : 0;
		}

		@Override
		public String toString() {
			return new StringBuffer().append("A{")
					.append(" cpu:").append(cpu)
					.append(", name:").append(name)
					.append(", action:").append(action)
					.append(", proc:").append(proc)
					.append(" }").toString();
		}

	}

	private static class ProcessInfo {
		int PID;
		int PR;
		int CPU;
		char S;
		int THR;
		int VSS;
		int RSS;
		String PCY;
		String UID;
		String Name;

		private ProcessInfo() {
		}

		public static ProcessInfo parseInfo(String line) {
			if (DEBUGV) Slog.i(TAG, "parseInfo() line='" + line + "'");
			if (TextUtils.isEmpty(line))
				return null;

			ProcessInfo pi = null;
			TOP_MATCHER.reset(line);
			if (TOP_MATCHER.matches()) {
				pi = new ProcessInfo();
				pi.PID = Integer.parseInt(TOP_MATCHER.group(1));
				pi.PR = Integer.parseInt(TOP_MATCHER.group(2));
				pi.CPU = Integer.parseInt(TOP_MATCHER.group(3));
				pi.S = TOP_MATCHER.group(4).charAt(0);
				pi.THR = Integer.parseInt(TOP_MATCHER.group(5));
				pi.VSS = Integer.parseInt(TOP_MATCHER.group(6));
				pi.RSS = Integer.parseInt(TOP_MATCHER.group(7));
				pi.PCY = TOP_MATCHER.group(8);
				pi.UID = TOP_MATCHER.group(9);
				pi.Name = TOP_MATCHER.group(10);
			}

			return pi;
		}

		@Override
		public String toString() {
			return new StringBuffer().append("ProcessInfo{")
					.append(" PID:").append(PID)
					.append(", PR:").append(PR)
					.append(", CPU:").append(CPU)
					.append(", S:").append(S)
					.append(", THR:").append(THR)
					.append(", VSS:").append(VSS)
					.append(", RSS:").append(RSS)
					.append(", PCY:").append(PCY)
					.append(", UID:").append(UID)
					.append(", Name:").append(Name)
					.append(" }").toString();
		}

	}

	// 初始化
	// private native void native_init();
	// 获取进程信息
	// private native void takeProcessInfoList();
}