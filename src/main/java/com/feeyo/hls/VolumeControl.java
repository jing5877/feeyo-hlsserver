package com.feeyo.hls;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class VolumeControl {
	
	private static final long cyclicPeriod = 3 * 60 * 60 * 1000;
	private static final long initPeriod = 10 * 60 * 1000;
	
	private double maxDbInCyclicPeriod = -1;
	private double maxDbInInitPeriod = -1;
	private long initTime = 0;
	
	public VolumeControl() {
		this.initTime = System.currentTimeMillis();
	}
	
	public byte[] autoControlVolume(byte[] data) {
		short[] shorts = byteArray2shortArray(data);
		double db = calVolumeDbByAbs(shorts,shorts.length);
		long current = System.currentTimeMillis();
		if(current - initTime > initPeriod) {
			
			if(maxDbInInitPeriod != -1) {
				maxDbInCyclicPeriod = maxDbInInitPeriod;
				maxDbInInitPeriod = -1;
			}
			maxDbInCyclicPeriod = maxDbInCyclicPeriod > db ? maxDbInCyclicPeriod : db;
			
			
		}else {
			maxDbInInitPeriod = maxDbInInitPeriod > db ? maxDbInInitPeriod : db;
		}
		
		if(maxDbInCyclicPeriod != -1)
			data = autoControlVolume(data, 85 - maxDbInCyclicPeriod);
		
		if(current - initTime > cyclicPeriod) 
			initTime = current;
		
		return data;
	}
	
	private byte[] autoControlVolume(byte[] data, double inc) {
		
		short[] pcm = byteArray2shortArray(data);
		double multiplier =  Math.pow(10, inc / 20 ) ;
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

	//使用绝对值计算分贝值
	private double calVolumeDbByAbs(short[] pcmData, int size) {
		double sum = 0;
	    for (short rawSample : pcmData) {
	    	sum += Math.abs(rawSample);
	    }
	    return Math.abs(20 * Math.log10((sum / size)));
	}
	
	private byte[] shortArray2byteArray(short[] shorts) {
		byte[] bytes = new byte[shorts.length * 2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(shorts);
		return bytes;
	}
	
	private short[] byteArray2shortArray(byte[] bytes) {
		short[] shorts = new short[bytes.length / 2];
		ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
		return shorts;
	}
}
