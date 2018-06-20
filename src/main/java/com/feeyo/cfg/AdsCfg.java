package com.feeyo.cfg;

public class AdsCfg {
	
	private String name;
	private String type;
	
	private float sampleRate = 8000F;
	private int sampleSizeInBits = 16;
	private int channels = 1;
	private int fps = 25;
	
	public AdsCfg(String name, String type) {
		this.name = name;
		this.type = type;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public float getSampleRate() {
		return sampleRate;
	}

	public int getSampleSizeInBits() {
		return sampleSizeInBits;
	}

	public int getChannels() {
		return channels;
	}

	public int getFps() {
		return fps;
	}

	public void setSampleRate(float sampleRate) {
		this.sampleRate = sampleRate;
	}

	public void setSampleSizeInBits(int sampleSizeInBits) {
		this.sampleSizeInBits = sampleSizeInBits;
	}

	public void setChannels(int channels) {
		this.channels = channels;
	}

	public void setFps(int fps) {
		this.fps = fps;
	}
	
}
