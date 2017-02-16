package com.fourtech.utilities;

public class Utilities {

	private Utilities() {
	}

	public static int parseInt(String str) {
		if (str == null || str.length() == 0)
			return 0;

		str = str.trim();
		int radix = 10;
		if (str.startsWith("0x") || str.startsWith("0X")) {
			radix = 16;
			str = str.substring(2);
		}
		return Integer.parseInt(str, radix);
	}

	public static String getLineInfo() {
		StackTraceElement ste = new Throwable().getStackTrace()[1];
		return " File:" + ste.getFileName() + ", Line:" + ste.getLineNumber();
	}

}