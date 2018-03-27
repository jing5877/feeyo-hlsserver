package com.feeyo.hls.ts.segmenter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.ts.segmenter.H264TsSegmenter.AvcResult;
import com.feeyo.mpeg2ts.TsWriter;
import com.feeyo.mpeg2ts.TsWriter.FrameData;
import com.feeyo.mpeg2ts.TsWriter.FrameDataType;
import com.feeyo.net.udp.packet.ByteUtil;
import com.feeyo.net.udp.packet.V5PacketType;

// 混合流
public class AacH264MixedTsSegmenter extends AbstractTsSegmenter {

	private static Logger LOGGER = LoggerFactory.getLogger(AacH264MixedTsSegmenter.class);

	private static final long wTime = 500; // 等待时间ms
	private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

	private ArrayDeque<AvcResult> avcResultDeque = new ArrayDeque<AvcResult>();		
	private ArrayDeque<FrameData> avcFrameCache = new ArrayDeque<FrameData>();		//视频队列
	private ArrayDeque<FrameData> aacFrameCache = new ArrayDeque<FrameData>();		//音频队列

	private List<RawItem> avcRawCache = new ArrayList<RawItem>();
	private List<RawItem> aacRawCache = new ArrayList<RawItem>();

	private long preAvcCTime = System.currentTimeMillis();
	private long preAacCTime = preAvcCTime;
	private long preAvcIndex = -1;			//已处理的视频序号
	private long preAacIndex = -1;			//已处理的音频序号

	private boolean skipAvc = false;		//视频是否跳帧
	private boolean skipAac = false;		//音频是否跳帧
	private boolean waitAac = false;		//是否需要等到音频
	private boolean isTailAvc = false;		//是否为最后的视频帧组
	private boolean isFirstAacPes = true;	//是否为音频首个PES包
	private boolean isFirstAvcPes = true;	//是否为视频首个PES包
	private boolean syncPtsBase = false;	//音视频起始时间的对齐标志
	private byte headFrameType = 0x00;		//辅助音视频对齐起始时间
	private long ctime = 0;					//同上
	
	private long mixPts;					//mixTs中最新的pts

	private TsWriter tsWriter;
	private H264TsSegmenter h264TsSegmenter;
	private AacTsSegmenter aacTsSegmenter;

	private byte[][] tsSecs;
	private int tsSecsPtr = 0;
	private int tsSegmentLen = 0;

	public AacH264MixedTsSegmenter() {

		tsSecs = new byte[3000][];
		tsWriter = new TsWriter();
		h264TsSegmenter = new H264TsSegmenter();
		aacTsSegmenter = new AacTsSegmenter();

		scheduledExecutor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				long currentTime = System.currentTimeMillis();
				
				if(waitAac && currentTime - preAacCTime > wTime) {
					waitAac = false;
				}
				
				if (skipAac && currentTime - preAacCTime > wTime) {
					preAacCTime = currentTime;
					if (!aacRawCache.isEmpty()) {
						RawItem raw = aacRawCache.remove(0);
						FrameData aacFrame = aacTsSegmenter.process(raw.rawData);
						if (aacFrame != null)
							aacFrameCache.offer(aacFrame);
						preAacIndex = raw.index;
					}
				}

				if (skipAvc && currentTime - preAvcCTime > wTime) {
					preAvcCTime = currentTime;
					if (!avcRawCache.isEmpty()) {
						RawItem raw = avcRawCache.remove(0);
						AvcResult avcResult = h264TsSegmenter.process(raw.rawData);
						if (avcResult != null)
							avcResultDeque.offer(avcResult);
						preAvcIndex = raw.index;
					}
				}
				
			}

		}, 0, 30, TimeUnit.MILLISECONDS);
	}

	@Override
	public void initialize(float sampleRate, int sampleSizeInBits, int channels, int fps) {
		h264TsSegmenter.initialize(sampleRate, sampleSizeInBits, channels, fps);
		aacTsSegmenter.initialize(sampleRate, sampleSizeInBits, channels, fps);
	}

	@Override
	protected byte[] transcoding(byte rawDataType, byte[] rawData) {
		return rawData;
	}

	@Override
	protected byte[] segment(byte rawDataType, byte[] rawData, byte[] reserved) {

		if (reserved.length != 8) {
			LOGGER.error("## Reserved area doesn't contain index!");
			return null;
		}
		
		if(ctime == 0) {
			headFrameType = rawDataType;
			ctime = System.currentTimeMillis();
		}
		
		//from 0
		long index = ByteUtil.bytesToLong(reserved[0], reserved[1], reserved[2], reserved[3], reserved[4], reserved[5],
				reserved[6], reserved[7]);
		
		switch (rawDataType) {

		case V5PacketType.AAC_STREAM:
			
			if( !syncPtsBase && headFrameType != V5PacketType.AAC_STREAM ) {
				aacTsSegmenter.setPts((System.currentTimeMillis() - ctime) * 90);
				syncPtsBase = true;
			}
			
			if (index == preAacIndex + 1) {
				preAacIndex++;
				skipAac = false;
				FrameData aacFrame = aacTsSegmenter.process(rawData);
				if (aacFrame != null)
					aacFrameCache.offer(aacFrame);

			} else if (index > preAacIndex + 1) {
				aacRawCache.add(new RawItem(rawData, index));
				Collections.sort(aacRawCache, new RawDescComparator());
				skipAac = true;
			}

			while (!aacRawCache.isEmpty() && aacRawCache.get(0).index == preAacIndex + 1) {
				preAacIndex++;
				skipAac = false;
				FrameData aacFrame = aacTsSegmenter.process(aacRawCache.remove(0).rawData);
				if (aacFrame != null)
					aacFrameCache.offer(aacFrame);
			}

			if (waitAac)
				writeFrame();

			break;

		case V5PacketType.H264_STREAM:
			
			if(!syncPtsBase && headFrameType != V5PacketType.H264_STREAM ) {
				h264TsSegmenter.setPts((System.currentTimeMillis() - ctime) * 90);
				syncPtsBase = true;
			}
			
			if (index == preAvcIndex + 1) {
				skipAvc = false;
				preAvcIndex = index;

				AvcResult avcResult = h264TsSegmenter.process(rawData);
				if (avcResult != null)
					avcResultDeque.offer(avcResult);
			} else if (index > preAvcIndex + 1) {
				avcRawCache.add(new RawItem(rawData, index));
				Collections.sort(avcRawCache, new RawDescComparator());
				skipAvc = true;
			}

			while (!avcRawCache.isEmpty() && avcRawCache.get(0).index == preAvcIndex + 1) {
				preAvcIndex++;
				skipAvc = false;
				AvcResult avcResult = h264TsSegmenter.process(avcRawCache.remove(0).rawData);
				if (avcResult != null) 
					avcResultDeque.offer(avcResult);
			}

			if (isTailAvc && !waitAac)
				return tsSecs[0] == null ? null : write2Ts();
			while (!avcResultDeque.isEmpty()) {
				writeFrame();
				if (isTailAvc && !waitAac) {
					long lastAvcPts = 0;
					while (!avcFrameCache.isEmpty()) {
						
						FrameData frameData = avcFrameCache.pop();
						byte[] aacTsSegment = tsWriter.write(isFirstAvcPes, FrameDataType.MIXED, frameData);
						if (aacTsSegment != null) {
							tsSegmentLen += aacTsSegment.length;
							tsSecs[tsSecsPtr++] = aacTsSegment;
						}
						isFirstAvcPes = false;
						lastAvcPts = frameData.pts;
					}
					mixPts = mixPts > lastAvcPts ? mixPts : lastAvcPts;
					return write2Ts();
				}
			}

			break;
		}
		
		return null;
	}

	private void writeFrame() {

		if (isTailAvc)
			return;

		while (!avcResultDeque.isEmpty()) {
			AvcResult result = avcResultDeque.pop();
			for (FrameData avcFrame : result.avcFrames)
				avcFrameCache.offer(avcFrame);
			if (result.isLastAvcResult) {
				isTailAvc = true;
				break;
			}
		}

		long lastAacPts = 0;
		long lastAvcPts = 0;
		while (!aacFrameCache.isEmpty() && !avcFrameCache.isEmpty()) {
			
			FrameData avcFrame = avcFrameCache.peek();
			if (aacFrameCache.peek().pts < avcFrame.pts) {
				FrameData frameData = aacFrameCache.pop();
				byte[] aacTsSegment = tsWriter.write(isFirstAacPes, FrameDataType.MIXED, frameData);

				if (aacTsSegment != null) {
					tsSegmentLen += aacTsSegment.length;
					tsSecs[tsSecsPtr++] = aacTsSegment;
				}
				isFirstAacPes = false;
				lastAacPts = frameData.pts;
			} else {

				FrameData frameData = avcFrameCache.pop();
				byte[] avcTsSegment = tsWriter.write(isFirstAvcPes, FrameDataType.MIXED, frameData);
				if (avcTsSegment != null) {
					tsSegmentLen += avcTsSegment.length;
					tsSecs[tsSecsPtr++] = avcTsSegment;
				}
				isFirstAvcPes = false;
				lastAvcPts = frameData.pts;
			}
		}
		long maxPts = lastAvcPts > lastAacPts ? lastAvcPts : lastAacPts;
		mixPts = mixPts > maxPts ? mixPts : maxPts;
		waitAac = (aacFrameCache.isEmpty() && lastAacPts + aacTsSegmenter.getPtsIncPerFrame() < lastAvcPts + h264TsSegmenter.getPtsIncPerFrame());
	}

	public void prepare4nextTs() {
		isFirstAvcPes = true;
		isFirstAacPes = true;
		isTailAvc = false;

		tsSegmentLen = 0;
		tsSecsPtr = 0;
		tsWriter.reset();

		for (int i = 0; i < tsSecs.length; i++) {
			tsSecs[i] = null;
		}

	}

	private byte[] write2Ts() {
		
		tsSegTime = (mixPts - ptsBase) / 90000F;
		ptsBase = mixPts;
		byte[] tsSegment = new byte[tsSegmentLen];
		int tsSegmentPtr = 0;
		for (int i = 0; i < tsSecs.length; i++) {
			if (tsSecs[i] != null) {
				System.arraycopy(tsSecs[i], 0, tsSegment, tsSegmentPtr, tsSecs[i].length);
				tsSegmentPtr += tsSecs[i].length;
			}
		}
		prepare4nextTs();
		return tsSegment;
	}

	@Override
	public void close() {

		isFirstAvcPes = true;
		isFirstAacPes = true;
		isTailAvc = false;
		preAvcIndex = -1;
		preAacIndex = -1;
		skipAvc = false;
		skipAac = false;
		avcRawCache.clear();
		aacRawCache.clear();
		
		syncPtsBase = false;
		headFrameType = 0x00;
		ctime = 0;

		tsSegmentLen = 0;
		tsSecsPtr = 0;

		for (int i = 0; i < tsSecs.length; i++) {
			tsSecs[i] = null;
		}

		h264TsSegmenter.close();
		aacTsSegmenter.close();
	}

	class RawDescComparator implements Comparator<RawItem> {
		@Override
		public int compare(RawItem o1, RawItem o2) {
			if (o1 == null || o2 == null) {
				return -1;
			}
			if (o1.index > o2.index)
				return 1; // 大于
			else if (o1.index == o2.index)
				return 0; // 等于
			else
				return -1; // 小于
		}
	}

	class RawItem {

		public byte[] rawData;
		public long index;

		public RawItem(byte[] rawData, long index) {
			this.rawData = rawData;
			this.index = index;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;

			if (obj == null || !(obj instanceof RawItem))
				return false;

			RawItem item = (RawItem) obj;
			return this.index > item.index;
		}
	}

}
