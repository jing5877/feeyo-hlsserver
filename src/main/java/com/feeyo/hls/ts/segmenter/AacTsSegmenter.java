package com.feeyo.hls.ts.segmenter;

import com.feeyo.mpeg2ts.TsWriter;
import com.feeyo.mpeg2ts.TsWriter.FrameData;
import com.feeyo.mpeg2ts.TsWriter.FrameDataType;

public class AacTsSegmenter extends AbstractTsSegmenter {

	//
    protected int aacBufPtr = 0;        			
    protected byte[][] aacBufArr = null;			 
    
    protected boolean isFirstPes = true;
    
    protected TsWriter tsWriter;
    
	public AacTsSegmenter() {
		
		super();
		
		int sampleRate = (int) this.sampleRate;												// 采样率
		this.tsSegTime = 1.0F * (frameNum << 10) / sampleRate;								// tsSegment的持续时间，以秒为单位
		this.ptsIncPerFrame = (90000 << 10) / sampleRate;

		
		this.tsWriter = new TsWriter();								
		
		this.frameNum = (TS_DURATION * sampleRate) >> 10;									// AAC 帧的数目， faac以1024位为一帧
		this.aacBufArr = new byte[ frameNum ][];

		prepare4nextTs();
	}
	
	@Override
	public void initialize(float sampleRate, int sampleSizeInBits, int channels, int fps) {
		
		super.initialize(sampleRate, sampleSizeInBits, channels, fps);
		this.sampleRate = sampleRate;												// 采样率
		this.frameNum = (TS_DURATION * (int)sampleRate) >> 10;									// AAC 帧的数目， faac以1024位为一帧
		this.tsSegTime = 1.0F * (frameNum << 10) / (int)sampleRate;								// tsSegment的持续时间，以秒为单位
		this.ptsIncPerFrame = (90000 << 10) / (int)sampleRate;
	}

	public void prepare4nextTs() {
		
		tsWriter.reset();

		aacBufPtr = 0;
		for (int i = 0; i < aacBufArr.length; i++) {
			aacBufArr[i] = null;
		}
	}

	@Override
	public void close() {
		aacBufPtr = 0;
	    if (aacBufArr != null) {
	    	aacBufArr = null;
        }
	}

	@Override
	protected byte[] segment(byte rawDataType, byte[] rawData, byte[] reserved) {
		//
		if (rawData != null && rawData.length > 0) {
			
			//
			if ( aacBufPtr < frameNum ) {
				aacBufArr[ aacBufPtr ] = rawData;
				aacBufPtr++;
			}
			
			
			//
			if (  aacBufPtr >= frameNum ) {
				
				// 构造 frame data
				int length = (frameNum / 3) + ( frameNum % 3 > 0 ? 1 : 0);
				FrameData[] frames = new FrameData[ length ];
				int frameIndex = 0;
						
				byte[] aacBuf = null;
				for(int i = 0; i < frameNum; i++) {

					byte[] buf = aacBufArr[i];
					
					if ( aacBuf == null ) {
						aacBuf = buf;
						
					} else {
						byte[] newBuf = new byte[aacBuf.length + buf.length];
						System.arraycopy(aacBuf, 0, newBuf, 0, aacBuf.length);
						System.arraycopy(buf, 0, newBuf, aacBuf.length, buf.length);
						
						aacBuf = newBuf;
					}
					
					
					if ( i != 0 ) {
						
						int remainder = i % 3;
						if ( remainder == 0 || i == frameNum - 1 ) {

							pts += ptsIncPerFrame * remainder == 0 ? 3 : remainder;		// 计算 PTS
							dts = pts;
							
							
							frames[ frameIndex ] = new FrameData();
							frames[ frameIndex ].buf = aacBuf;
							frames[ frameIndex ].pts = pts;
							frames[ frameIndex ].dts = dts;
							frames[ frameIndex ].isAudio = true;
									
							frameIndex++;
							
							aacBuf = null;
							
						} 
						
					}
					
				}
				
				// 构造
				byte[] tsBuf = tsWriter.write(isFirstPes, FrameDataType.AUDIO, frames);
				tsSegTime = (pts - ptsBase) / 90000F;
				ptsBase = pts;
				
				prepare4nextTs();
	                
	            return tsBuf;
				
			}
        }
        return null;
	}
	

	
	//
	public FrameData rawDataToFrameData(byte[] rawData) {
		
		if (rawData != null && rawData.length > 0) {
			pts += ptsIncPerFrame;
			FrameData frameData =  new FrameData();
			frameData.buf = rawData;
			frameData.pts = pts;
			frameData.dts = 0;
			frameData.isAudio = true;
			return frameData;
        }	
		
		return null;
	}
	
}
