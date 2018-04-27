package com.feeyo.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VolumeUtil {

	public static byte[] autoControlVolume(byte[] data, double inc) {

		short[] pcm = byteArray2shortArray(data);
		double multiplier = Math.pow(10, inc / 20);
		for (int ctr = 0; ctr < pcm.length; ctr++) {
			short pcmval = (short) (pcm[ctr] * multiplier);
			if (pcmval < 32767 && pcmval > -32768) {
				pcm[ctr] = pcmval;
			} else if (pcmval > 32767) {
				pcm[ctr] = 32767;
			} else if (pcmval < -32768) {
				pcm[ctr] = -32768;
			}
		}
		return shortArray2byteArray(pcm);
	}

	public static double calMaxVolumeDbByAbs(short[] pcmData) {
		
		double maxData = 0;
		for(short pcm : pcmData)
			maxData = maxData > Math.abs(pcm)? maxData : Math.abs(pcm);
		return 20 * Math.log10(maxData > 0 ? maxData / 32767 : maxData / -32768);
	}
	
	public static byte[] shortArray2byteArray(short[] shorts) {
		byte[] bytes = new byte[shorts.length * 2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);
		return bytes;
	}

	public static short[] byteArray2shortArray(byte[] bytes) {
		short[] shorts = new short[bytes.length / 2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		return shorts;
	}
	
	/*
	 * Hamming 2*pi*k w(k) = 0.54 - 0.46*cos(------), where 0 <= k < N N-1
	 *
	 * n window length w buffer for the window parameters
	 */
	public static void hamming(int n, float[] w) {
		int i;
		float k = (float) (2 * Math.PI / (n - 1)); /* 2*pi/(N-1) */

		for (i = 0; i < n; i++)
			w[i] = (float) (0.54 - 0.46 * Math.cos(k * i));
	}
	
}
