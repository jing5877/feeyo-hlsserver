package com.feeyo.audio.volume;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VolumeUtil {
	
	// max dBSPL
	public static double getMaxSoundPressureLevel(short[] shortBuf) {
		
		double max = 0;
		for(short s : shortBuf)
			max = Math.abs(max) > Math.abs(s)? max : s;
		return 20 * Math.log10(max > 0 ? max / 32767 : max / -32768);
	}
	
	// dBSPL
	public static double getSoundPressureLevel(short[] shortBuf) {
		// 计算音频缓冲器的局部（线性）能量
		double power = 0.0D;
		for (short element : shortBuf) {
			power += element * element;
		}
		
		// http://en.wikipedia.org/wiki/Root_mean_square
		double value = Math.sqrt(power);
		value = value / shortBuf.length;
		
		// 将线性转换为dB值
		return 20.0 * Math.log10(value / 32767);
	}
	
	
	// dBSPL
	public static double getSoundPressureLevel(float[] floatBuf) {
		
		// 计算音频缓冲器的局部（线性）能量
		double power = 0.0D;
		for (float element : floatBuf) {
			power += element * element;
		}
		
		double value = Math.pow(power, 0.5);
		value = value / floatBuf.length;
		
		// 将线性转换为dB值
		return 20.0 * Math.log10(value);
	}
	
	
	public static byte[] toByteArray(short[] shortArr) {
		byte[] byteArr = new byte[shortArr.length * 2];
		ByteBuffer.wrap( byteArr ).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shortArr);
		return byteArr;
	}

	public static short[] toShortArray(byte[]  byteArr) {
		short[] shortArr = new short[byteArr.length / 2];
		ByteBuffer.wrap(byteArr).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortArr);
		return shortArr;
	}
	
	// PCM 16 bit, signed, little-endian
	//
	public static float[] toFloatArray(byte[] byteArr) {
		float[] floatArr = new float[ byteArr.length / 2 ];
		int idx = 0;
		for (int i = 0; i < floatArr.length; i++) {
			floatArr[i] = ((short) ((byteArr[idx++] & 0xFF) | (byteArr[idx++] << 8))) * (1.0f / 32767.0f);
		}
		return floatArr;
	}
	
	public static byte[] toByteArray(float[] floatArr) {
		byte[] byteArr = new byte[floatArr.length * 2];
		int idx= 0;
		for (int i = 0; i < floatArr.length; i++) {
			int v = (int) (floatArr[i] * 32767.0);
			byteArr[idx++] = (byte) v;
			byteArr[idx++] = (byte) (v >>> 8);
		}
		return byteArr;
	}
	
}
