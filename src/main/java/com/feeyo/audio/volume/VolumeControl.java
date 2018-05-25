package com.feeyo.audio.volume;

import com.feeyo.audio.noise.NoiseGenerator;
import com.feeyo.audio.noise.NoiseSuppress;

public class VolumeControl {
	
	public static final double silenceThresold  = -75D;
	
	// 最大的梯度分贝
	private double maxGradientDb = -999;
	private double newMaxGradientDb = maxGradientDb;
	
	private long ctime = 0;
	
	private NoiseSuppress noiseSupress;
	
	public VolumeControl(int sampleRate, int frameSize) {
		
		this.ctime = System.currentTimeMillis();
		this.noiseSupress = new NoiseSuppress(sampleRate, frameSize);
	}
	
	public byte[] noise(byte[] data) {
		// 降噪
		data = noiseSupress.noiseReductionProcess(data);
		return data;
	}
	
	public byte[] gain(byte[] data) {
		
		short[] pcm = VolumeUtil.byteArray2shortArray(data);
		double db = VolumeUtil.calMaxVolumeDbByAbs( pcm );
		long now = System.currentTimeMillis();
		
		// lookup max gradient db
		if ( now - ctime > (5 * 60 * 1000) ) {
			
			if (newMaxGradientDb != -999) {
				maxGradientDb = newMaxGradientDb;
				
				//reset
				newMaxGradientDb = -999;
			}
			ctime = now;
			maxGradientDb = Math.max( maxGradientDb, db );

		} else {
			newMaxGradientDb = Math.max( newMaxGradientDb, db );
		}
		
		// 增益
		if (maxGradientDb != -999 && maxGradientDb < -8) {
			
			// 音量自动增益
			double inc = ( getGradientDb() - maxGradientDb );
			double multiplier = Math.pow(10, inc / 20);
			
			for (int i = 0; i < pcm.length; i++) {
				short pcmval = (short) (multiplier * pcm[i]);
				if (pcmval < 32767 && pcmval > -32768) {
					pcm[i] = pcmval;
				} else if (pcmval > 32767) {
					pcm[i] = 32767;
				} else if (pcmval < -32768) {
					pcm[i] = -32768;
				}
			}
			data = VolumeUtil.shortArray2byteArray(pcm);
		}
		

		return data;
	}
	
	private double getGradientDb() {
		if (maxGradientDb < -15f) {
			return -2;
			
		} else if (maxGradientDb < -14) {
			return -3;
			
		} else if (maxGradientDb < -12) {
			return -4;
			
		} else if (maxGradientDb < -9) {
			return -5;
			
		} else {
			return -6;
		}
	}

	public boolean isSilence(double silenceThreshold, byte[] rawData) {
		short[] pcmData = VolumeUtil.byteArray2shortArray(rawData);
		return VolumeUtil.calAvgVolumeDbBySqure(pcmData) < silenceThreshold; 
	}
	
	public byte[] generateWhiteNoise(int length) {
		byte[] pcm = new byte[length];
		System.arraycopy(NoiseGenerator.getFixedNoise(), 0, pcm, 0, length);
		return pcm;
	}
	
}
