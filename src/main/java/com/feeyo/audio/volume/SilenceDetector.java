package com.feeyo.audio.volume;

/**
 * TarsosDSP
 * 
 * @see https://0110.be/tags/TarsosDSP
 * @see https://github.com/JorenSix/TarsosDSP
 */
public class SilenceDetector {
	
	//
	public static final double DEFAULT_SILENCE_THRESHOLD = -70.0; //db

	public boolean isSilence(double silenceThreshold, float[] inputBuffer) {
		return soundPressureLevel(inputBuffer) < silenceThreshold;
	}

	/**
	 * 返回 dBSPL
	 */
	public double soundPressureLevel(final float[] buffer) {
		
		//计算音频缓冲器的局部（线性）能量
		double power = 0.0D;
		for (float element : buffer) {
			power += element * element;
		}
		
		double value = Math.pow(power, 0.5);
		value = value / buffer.length;
		
		// 将线性转换为dB值
		return 20.0 * Math.log10(value);
	}
	
	
	/**
	 * 返回 dBSPL
	 */
	public double soundMaxPressureLevel(final float[] buffer) {
		
		//计算音频缓冲器的局部（线性）能量
		double value = 0.0D;
		for (float element : buffer) {
			value = Math.max(value, element);
		}
		
		// 最大的dB值
		return 20.0 * Math.log10(value > 0 ? value / 32767 : value / -32768);
	}
	
}