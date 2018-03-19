package com.feeyo.util;

import java.util.ArrayList;
import java.util.List;

public class StringUtil {
	
	public static final String EMPTY_STR = "";
	public static final String[] EMPTY_STR_ARRAY = new String[0];

	/**
	 * 给定的是不是全部为字母(a~zA~Z)
	 * 
	 * @param input
	 * @return true表示全部为字母(a~zA~Z)
	 */
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

	/**
	 * 首字母大写
	 * 
	 * @param input
	 * @return
	 */
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

	/**
	 * 如果 是 null或者空字符串 就返回true
	 * 
	 * @param input
	 * @return
	 */
	public static boolean isEmpty(CharSequence input) {
		return input == null || input.length() == 0;
	}

	/**
	 * 分割字符串
	 * 
	 * @param input
	 * @param separator
	 * @param hasEmpty
	 *            返回是否包含空白字符
	 * @return
	 */
	public static String[] split(String input, char separator, boolean hasEmpty) {
		if (input == null) {
			return null;
		}

		int length = input.length();
		if (length == 0) {
			return EMPTY_STR_ARRAY;
		}
		List<String> tempStrs = new ArrayList<String>();
		/*
		 * 逐个测试，记录开始索引，截取字符串并保存起来，最后输出
		 */
		int start = 0;
		int end = 0;
		boolean lastMatched = false;// 上次匹配到了
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

	/**
	 * 分割字符串,返回值包括空白字符
	 * 
	 * @param input
	 * @param separator
	 *            分割字符
	 * @return
	 */
	public static String[] split(String input, char separator) {
		return split(input, separator, true);
	}
}