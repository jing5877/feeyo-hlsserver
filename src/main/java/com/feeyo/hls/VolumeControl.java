package com.feeyo.hls;

import java.util.concurrent.atomic.AtomicBoolean;

import com.feeyo.audio.noise.NoiseSuppress;
import com.feeyo.util.VolumeUtil;

public class VolumeControl {
	
	private static final long cyclicPeriod = 30 * 60 * 1000;
	private static final long initPeriod = 5 * 60 * 1000;
	
	private double maxDbInCyclicPeriod = -999;
	private double maxDbInInitPeriod = -999;
	private long initTime = 0;
	private NoiseSuppress noiseSupress;
	
	private AtomicBoolean _lock = new AtomicBoolean(false);
	
	public VolumeControl(float sampleRate, int frameSize) {
		this.initTime = System.currentTimeMillis();
		noiseSupress = new NoiseSuppress((int)sampleRate, frameSize);
	}
	
	public byte[] autoControlVolume(byte[] data) {
		
		short[] shorts = VolumeUtil.byteArray2shortArray(data);
		double db = VolumeUtil.calMaxVolumeDbByAbs(shorts);
		long current = System.currentTimeMillis();
		
		if(current - initTime > initPeriod) {
			if(maxDbInInitPeriod != -999) {
				maxDbInCyclicPeriod = maxDbInInitPeriod;
				maxDbInInitPeriod = -999;
			}
			maxDbInCyclicPeriod = maxDbInCyclicPeriod > db ? maxDbInCyclicPeriod : db;
		}else {
			maxDbInInitPeriod = maxDbInInitPeriod > db ? maxDbInInitPeriod : db;
		}
		
		if(maxDbInCyclicPeriod != -999 && maxDbInCyclicPeriod < -8) {
			data = autoControlVolume(shorts, getGradientDb() - maxDbInCyclicPeriod );
		}
		
		try {
			while(_lock.compareAndSet(false, true)) 
				data = noiseSupress.noiseReductionProcess(data);
		}finally {
			_lock.set(false);
		}
		
		if(current - initTime > cyclicPeriod) 
			initTime = current;
		return data;
	}
	
	private double getGradientDb() {
		
		if(maxDbInCyclicPeriod < -15f) {
			return -2;
		}else if(maxDbInCyclicPeriod< -14) {
			return -3;
		}else if(maxDbInCyclicPeriod< -12) {
			return -4;
		}else if(maxDbInCyclicPeriod < -9) {
			return -5;
		}else {
			return -6;
		}
	}
	
	private byte[] autoControlVolume(short[] pcm, double inc) {
		
		double multiplier = Math.pow(10, inc/20);
		for (int ctr = 0; ctr < pcm.length; ctr++) {
		    short pcmval = (short) (multiplier * pcm[ctr]);
		    if (pcmval < 32767 && pcmval > -32768) {
		        pcm[ctr] = pcmval;
		    } else if (pcmval > 32767) {
		        pcm[ctr] = 32767;
		    } else if (pcmval < -32768) {
		        pcm[ctr] = -32768;
		    }
		}
		return VolumeUtil.shortArray2byteArray(pcm);
	}
	
}
