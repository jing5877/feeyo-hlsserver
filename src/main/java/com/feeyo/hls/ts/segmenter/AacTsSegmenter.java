package com.feeyo.hls.ts.segmenter;

import com.feeyo.mpeg2ts.TsWriter;
import com.feeyo.mpeg2ts.TsWriter.FrameData;

public class AacTsSegmenter extends AbstractTsSegmenter {
	
    protected byte[] aacBuf;                  // aacBuf 用于缓存 pesAuNum 帧数据
    protected int aacBufPtr = 0;              // aacBuf 写指针

    protected int aacFrameCounter = 0;        // 编码计数器，等于aacFrameNum时重置

    protected byte[][] tsSecs;                // 一个 tsSegment 包含几个 tsSecs: {tsSegment} = {[tsSec] [tsSec] ... [tsSec]}
    protected int tsSecsPtr = 0;				// tsSecs 指针
    protected int tsSegmentLen = 0;			// 一个 tsSegment 的字节数
    
    protected boolean isFirstPes = true;
    
    protected TsWriter tsWriter;
    
	public AacTsSegmenter() {
		
		super();
		
		this.tsWriter = new TsWriter();														//
		
		int sampleRate = (int) this.sampleRate;												// 采样率
		this.frameNum = (TS_DURATION * sampleRate) >> 10;									// AAC 帧的数目， faac以1024位为一帧
		
		int maxAacBufLen = TS_PES_AU_NUM * 2048;											// AAC缓存的最大长度
		this.aacBuf = new byte[maxAacBufLen];												// AAC缓存
		
		this.tsSegTime = 1.0F * (frameNum << 10) / sampleRate;								// tsSegment的持续时间，以秒为单位
		this.ptsIncPerFrame = (90000 << 10) / sampleRate;
		this.tsSecs = new byte[(int) Math.ceil(1.0 * frameNum / TS_PES_AU_NUM)][];	//

		prepare4nextTs();
	}
	
	@Override
	public void initialize(float sampleRate, int sampleSizeInBits, int channels, int fps) {
		
		super.initialize(sampleRate, sampleSizeInBits, channels, fps);
		this.sampleRate = sampleRate;												// 采样率
		this.frameNum = (TS_DURATION * (int)sampleRate) >> 10;									// AAC 帧的数目， faac以1024位为一帧
		this.tsSegTime = 1.0F * (frameNum << 10) / (int)sampleRate;								// tsSegment的持续时间，以秒为单位
		this.ptsIncPerFrame = (90000 << 10) / (int)sampleRate;
		this.tsSecs = new byte[(int) Math.ceil(1.0 * frameNum / TS_PES_AU_NUM)][];
	}

	public void prepare4nextTs() {
		aacBufPtr = 0;
		tsSecsPtr = 0;
		aacFrameCounter = 0;
		tsSegmentLen = 0;
		tsWriter.reset();

		for (int i = 0; i < tsSecs.length; i++) {
			tsSecs[i] = null;
		}
	}

	@Override
	public void close() {
	    if (tsSecs != null) {
            tsSecs = null;
        }
        aacBuf = null;
	}

	@Override
	protected byte[] segment(byte rawDataType, byte[] rawData, byte[] reserved) {
		//
		if (rawData != null && rawData.length > 0) {
			
            System.arraycopy(rawData, 0, aacBuf, aacBufPtr, rawData.length);
            aacBufPtr += rawData.length;

			if (++aacFrameCounter % TS_PES_AU_NUM == 0) {
				//try{
					pts += ptsIncPerFrame * TS_PES_AU_NUM;		// 计算 PTS
					
                	//byte[] tsBuf = tsEncoder.encode(true, aacBuf, aacBufPtr, pts, pts, true, isFirstPes);
					byte[] tsBuf = tsWriter.writeAAC(isFirstPes, aacBuf, aacBufPtr, pts, dts);
                	isFirstPes = false;
	                tsSegmentLen += tsBuf.length;
	                tsSecs[tsSecsPtr++] = tsBuf;
	                aacBufPtr = 0;
               // } finally {
                	//tsWriter.reset();
                //}
            }

			if (aacFrameCounter == frameNum) {
				if (aacBufPtr > 0) {
					//try {
						pts += ptsIncPerFrame * (frameNum % TS_PES_AU_NUM);	// 计算 PTS
	                    //byte[] tsBuf = tsEncoder.encode(true, aacBuf, aacBufPtr, pts, pts, true, false);
						byte[] tsBuf = tsWriter.writeAAC(false, aacBuf, aacBufPtr, pts, dts);
	                    tsSegmentLen += tsBuf.length;
	                    tsSecs[tsSecsPtr++] = tsBuf;
                    //} finally {
                    	//tsWriter.reset();
                   // }
                }

				tsSegTime = (pts - ptsBase) / 90000F;
				ptsBase = pts;
				
                byte[] tsSegment = new byte[tsSegmentLen];
                int tsSegmentPtr = 0;
				for (int i = 0; i < tsSecs.length; i++) {
					if(tsSecs[i] != null) {
	                    System.arraycopy(tsSecs[i], 0, tsSegment, tsSegmentPtr, tsSecs[i].length);
	                    tsSegmentPtr += tsSecs[i].length;
					}
                }
				isFirstPes = true;
                prepare4nextTs();
                
                return tsSegment;
            }
        }
        return null;
	}
	
	public FrameData process(byte[] rawData) {
		
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
