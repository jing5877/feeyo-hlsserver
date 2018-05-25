package com.feeyo.audio.volume;

import com.feeyo.audio.noise.NoiseSample;
import com.feeyo.audio.noise.NoiseSuppress;

public class VolumeControl {
	
	public static final double DEFAULT_SILENCE_THRESHOLD  = -70D;
	
	// 最大的梯度分贝
	private double max_dB = -999;
	private double new_max_dB = max_dB;
	
	private long ctime = 0;
	
	private NoiseSuppress noiseSupress;
	
	public VolumeControl(int sampleRate, int frameSize) {
		
		this.ctime = System.currentTimeMillis();
		this.noiseSupress = new NoiseSuppress(sampleRate, frameSize);
	}
	
	public byte[] noise(byte[] data) {
		// 降噪
		
		short[] pcm = VolumeUtil.toShortArray(data);
		pcm = noiseSupress.noiseReductionProcess(pcm);
		data = VolumeUtil.toByteArray(pcm);
		
		return data;
	}
	
	public byte[] gain(byte[] data) {
		
		short[] pcmData = VolumeUtil.toShortArray(data);
		
		double db = VolumeUtil.getMaxSoundPressureLevel( pcmData );

		// lookup max gradient db
		long now = System.currentTimeMillis();
		if ( now - ctime > (5 * 60 * 1000) ) {
			
			if (new_max_dB != -999) {
				max_dB = new_max_dB;
				
				//reset
				new_max_dB = -999;
			}
			ctime = now;
			max_dB = Math.max( max_dB, db );

		} else {
			new_max_dB = Math.max( new_max_dB, db );
		}
		
		// 增益
		if (max_dB != -999 && max_dB < -8) {
			
			// 音量自动增益
			double inc = ( getGradientDb() - max_dB );
			double multiplier = Math.pow(10, inc / 20);
			
			for (int i = 0; i < pcmData.length; i++) {
				short pcmval = (short) (multiplier * pcmData[i]);
				if (pcmval < 32767 && pcmval > -32768) {
					pcmData[i] = pcmval;
				} else if (pcmval > 32767) {
					pcmData[i] = 32767;
				} else if (pcmval < -32768) {
					pcmData[i] = -32768;
				}
			}
			
			data = VolumeUtil.toByteArray( pcmData );
		}
		return data;
	}
	
	private double getGradientDb() {
		if (max_dB < -15f) {
			return -2;
			
		} else if (max_dB < -14) {
			return -3;
			
		} else if (max_dB < -12) {
			return -4;
			
		} else if (max_dB < -9) {
			return -5;
			
		} else {
			return -6;
		}
	}
	
	public byte[] silenceDetection( byte[] rawData ) {
		
		short[] pcmData = VolumeUtil.toShortArray(rawData);
		
		// 
		boolean isSilence = VolumeUtil.getSoundPressureLevel(pcmData) < DEFAULT_SILENCE_THRESHOLD;
		if ( isSilence ) {
			byte[] pcm = new byte[rawData.length];
			System.arraycopy(NoiseSample.WHITE_NOISE, 0, pcm, 0, rawData.length);
			return pcm;
		} 
		
		return rawData;
	}
	
}
