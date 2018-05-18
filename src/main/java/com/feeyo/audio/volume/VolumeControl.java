package com.feeyo.audio.volume;

import com.feeyo.audio.noise.NoiseSuppress;

public class VolumeControl {
	
	private double maxGradientDb = -999;
	private double initGradientDb = -999;
	
	private long ctime = 0;
	
	private volatile boolean isNoiseReduction = false;
	private NoiseSuppress noiseSupress;
	
	public VolumeControl(float sampleRate, int frameSize, boolean isNoiseReduction) {
		
		this.ctime = System.currentTimeMillis();
		this.isNoiseReduction = isNoiseReduction;
		this.noiseSupress = new NoiseSuppress((int)sampleRate, frameSize);
	}
	
	public byte[] autoControlVolume(byte[] data) {
		
		short[] shorts = VolumeUtil.byteArray2shortArray(data);
		double db = VolumeUtil.calMaxVolumeDbByAbs(shorts);
		long now = System.currentTimeMillis();
		
		if ( now - ctime > (5 * 60 * 1000) ) {
			
			if (initGradientDb != -999) {
				maxGradientDb = initGradientDb;
				initGradientDb = -999;
			}
			maxGradientDb = maxGradientDb > db ? maxGradientDb : db;
			
		} else {
			initGradientDb = initGradientDb > db ? initGradientDb : db;
		}
		
		// 增益
		if (maxGradientDb != -999 && maxGradientDb < -8) {
			data = autoGain(shorts, getGradientDb() - maxGradientDb);
		}
		
		// 降噪
		if ( isNoiseReduction == true )
			data = noiseSupress.noiseReductionProcess(data);
		
		if(now - ctime > (30 * 60 * 1000)) 
			ctime = now;
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

	// 音量自动增益
	private byte[] autoGain(short[] pcm, double inc) {
		
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
		return VolumeUtil.shortArray2byteArray(pcm);
	}
	
}
