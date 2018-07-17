package com.cclearning.imgclass.utils;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.cclearning.imgclass.Config;

public class Utils {
	public static final String SEPERATOR = "_*_*_*_";
	public static final String ID_URL_SEPERATOR = "_***_***_";

	public static String createMessage(String key, String value, String separator) {
		return key + separator + value;
	}

	public static String[] splitMessage(String msg, String separator) {
		return msg.split(Pattern.quote(separator));
	}

	public static String createRequestMessage(String id, String url) {
		return createMessage(id, url, ID_URL_SEPERATOR);
	}

	public static String[] splitRequestMessage(String msg) {
		return splitMessage(msg, ID_URL_SEPERATOR);
	}

	public static String createResultMessage(String key, String value) {
		return key + SEPERATOR + value;
	}

	public static String[] splitResultMessage(String msg) {
		return msg.split(Pattern.quote(SEPERATOR));
	}

	public static void log(String msg) {
		System.out.println(msg);
	}

	public static void sleep(int seconds) {
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}
