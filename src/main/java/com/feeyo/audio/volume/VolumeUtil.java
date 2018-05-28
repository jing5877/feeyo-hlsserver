package com.feeyo.audio.volume;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VolumeUtil {
	
	// max dBSPL
	public static double getMaxSoundPressureLevel(short[] pcmData) {
		
		double maxData = 0;
		for(short pcm : pcmData)
			maxData = Math.abs(maxData) > Math.abs(pcm)? maxData : pcm;
		return 20 * Math.log10(maxData > 0 ? maxData / 32767 : maxData / -32768);
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
	
	
	public static byte[] toByteArray(short[] shorts) {
		byte[] bytes = new byte[shorts.length * 2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);
		return bytes;
	}

	public static short[] toShortArray(byte[] bytes) {
		short[] shorts = new short[bytes.length / 2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		return shorts;
	}
	
	// PCM 16 bit, signed, little-endian
	//
	public static float[] toFloatArray(byte[] in_buff) {
		float[] out_buff = new float[ in_buff.length / 2 ];
		int ix = 0;
		for (int ox = 0; ox < out_buff.length; ox++) {
			out_buff[ox] = ((short) ((in_buff[ix++] & 0xFF) | (in_buff[ix++] << 8))) * (1.0f / 32767.0f);
		}
		return out_buff;
	}
	
	public static byte[] toByteArray(float[] in_buff) {
		byte[] out_buff = new byte[in_buff.length * 2];
		int ox = 0;
		for (int ix = 0; ix < in_buff.length; ix++) {
			int x = (int) (in_buff[ix] * 32767.0);
			out_buff[ox++] = (byte) x;
			out_buff[ox++] = (byte) (x >>> 8);
		}
		return out_buff;
	}
	
}
