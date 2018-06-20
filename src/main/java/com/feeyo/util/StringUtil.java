package com.feeyo.util;

import java.util.ArrayList;
import java.util.List;

public class StringUtil {
	
	public static final String EMPTY_STR = "";
	public static final String[] EMPTY_STR_ARRAY = new String[0];

	public static boolean isAlpha(CharSequence input) {
		if (input == null || input.length() == 0) {
			return false;
		}
		char ch;
		for (int i = input.length() - 1; i >= 0; i--) {
			ch = input.charAt(i);
			if (ch < 'A' || 'z' < ch || ('Z' < ch && ch < 'a')) {
				return false;
			}
		}
		return true;
	}

	public static String capitalize(String input) {
		int length;
		if (input == null || (length = input.length()) == 0) {
			return input;
		}
		char ch = input.charAt(0);
		char upCh = Character.toUpperCase(ch);
		if (ch == upCh) {
			return input;
		}
		char[] newChars = new char[length];
		newChars[0] = upCh;
		input.getChars(1, length, newChars, 1);
		return String.valueOf(newChars);
	}

	public static boolean isEmpty(CharSequence input) {
		return input == null || input.length() == 0;
	}

	public static String[] split(String input, char separator, boolean hasEmpty) {
		if (input == null) {
			return null;
		}

		int length = input.length();
		if (length == 0) {
			return EMPTY_STR_ARRAY;
		}
		List<String> tempStrs = new ArrayList<String>();
	
		int start = 0;
		int end = 0;
		boolean lastMatched = false;
		boolean match = false;
		while (end < length) {
			if (input.charAt(end) == separator) {
				if (match || hasEmpty) {
					tempStrs.add(input.substring(start, end));
					lastMatched = true;
					match = false;
				}
				start = ++end;
				continue;
			}
			lastMatched = false;
			match = true;
			end++;
		}
		if (match || hasEmpty && lastMatched) {
			tempStrs.add(input.substring(start, end));
		}
		return tempStrs.toArray(new String[tempStrs.size()]);
	}

	public static String[] split(String input, char separator) {
		return split(input, separator, true);
	}
}