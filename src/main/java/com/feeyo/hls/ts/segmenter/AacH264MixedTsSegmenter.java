package com.feeyo.hls.ts.segmenter;

// 混合流
public class AacH264MixedTsSegmenter extends AbstractTsSegmenter {

	@Override
	protected byte[] transcoding(byte rawDataType, byte[] rawData) {
		return rawData;
	}

	@Override
	protected byte[] segment(byte rawDataType, byte[] rawData) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() {
	}

}
