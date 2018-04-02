package com.feeyo.hls.ts.segmenter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.ts.segmenter.H264TsSegmenter.AvcResult;
import com.feeyo.mpeg2ts.TsWriter;
import com.feeyo.mpeg2ts.TsWriter.FrameData;
import com.feeyo.mpeg2ts.TsWriter.FrameDataType;
import com.feeyo.net.udp.packet.ByteUtil;
import com.feeyo.net.udp.packet.V5PacketType;

/**
 * 音视频混合流
 * @author xuwenfeng@variflight.com
 */
public class AacH264MixedTsSegmenter extends AbstractTsSegmenter {

	private static Logger LOGGER = LoggerFactory.getLogger(AacH264MixedTsSegmenter.class);

	private static final long wTime = 2000; 									// 等待时间ms
	private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);

	private ArrayDeque<AvcResult> avcResultDeque = new ArrayDeque<AvcResult>();
	private ArrayDeque<FrameData> avcFrameCache = new ArrayDeque<FrameData>(); 	// 视频队列
	private ArrayDeque<FrameData> aacFrameCache = new ArrayDeque<FrameData>(); 	// 音频队列

	private List<RawItem> avcRawCache = new ArrayList<RawItem>();
	private List<RawItem> aacRawCache = new ArrayList<RawItem>();

	private long preAvcCTime = System.currentTimeMillis();
	private long preAacCTime = preAvcCTime;
	private long preAvcIndex = -1; 												// 已处理的视频序号
	private long preAacIndex = -1; 												// 已处理的音频序号

	private boolean skipAvc = false; 											// 视频是否跳帧
	private boolean skipAac = false; 											// 音频是否跳帧
	private boolean waitAac = false; 											// 是否等待音频
	private boolean isTailAvc = false; 											// 是否为最后的视频帧组
	private boolean isFirstAacPes = true; 										// 是否为音频首个PES包
	private boolean isFirstAvcPes = true; 										// 是否为视频首个PES包
	private boolean syncPtsBase = false; 										// 音视频起始时间的对齐标志
	private boolean isLowTenSec = false;										// 视频时长是否低于10s
	private byte headFrameType = 0x00; 											// 辅助音视频对齐起始时间
	private long ctime = 0; 													// 同上

	private long mixPts; 														// mixTs中最新的pts

	private long maxAacPts = 0;
	private long maxAvcPts = 0;

	private TsWriter tsWriter;
	private H264TsSegmenter h264TsSegmenter;
	private AacTsSegmenter aacTsSegmenter;

	private byte[][] tsSecs;
	private int tsSecsPtr = 0;
	private int tsSegmentLen = 0;

	private AtomicBoolean aacLocking = new AtomicBoolean(false);
	private AtomicBoolean avcLocking = new AtomicBoolean(false);
	
	public AacH264MixedTsSegmenter() {

		tsSecs = new byte[3000][];
		tsWriter = new TsWriter();
		h264TsSegmenter = new H264TsSegmenter();
		aacTsSegmenter = new AacTsSegmenter();

		scheduledExecutor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {

				long currentTime = System.currentTimeMillis();

				if (currentTime - preAacCTime > wTime) {
					waitAac = false;
					if (skipAac) {
						preAacCTime = currentTime;
						if (!aacRawCache.isEmpty()) {
							try {
								while (!aacLocking.compareAndSet(false, true)) {
								}
								RawItem raw = aacRawCache.remove(0);
								FrameData aacFrame = aacTsSegmenter.process(raw.rawData);
								if (aacFrame != null)
									aacFrameCache.offer(aacFrame);
								if (preAacIndex != raw.index)
									preAacIndex = raw.index;
							} finally {
								aacLocking.set(false);
							}
						}
					}
				}

				if (skipAvc && currentTime - preAvcCTime > wTime) {
					preAvcCTime = currentTime;
					if (!avcRawCache.isEmpty()) {
						try {
							while (!avcLocking.compareAndSet(false, true)) {
							}
							RawItem raw = avcRawCache.remove(0);
							AvcResult avcResult = h264TsSegmenter.process(raw.rawData);
							if (avcResult != null)
								avcResultDeque.offer(avcResult);
							if (preAvcIndex != raw.index)
								preAvcIndex = raw.index;
						} finally {
							avcLocking.set(false);
						}
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

	/**
	 * @param V5PacketType.AAC_STREAM -> rawData is a full-frame accData  
	 * 		  V5PacketType.H264_STREAM -> rawData is h264 byte dataStream
	 */
	@Override
	protected byte[] segment(byte rawDataType, byte[] rawData, byte[] reserved) {

		if (reserved.length != 8) {
			LOGGER.error("## Reserved area doesn't contain index!");
			return null;
		}

		if (ctime == 0) {
			headFrameType = rawDataType;
			ctime = System.currentTimeMillis();
		}

		// from 0
		long index = ByteUtil.bytesToLong(reserved[0], reserved[1], reserved[2], reserved[3], reserved[4], reserved[5],
				reserved[6], reserved[7]);

		switch (rawDataType) {

		case V5PacketType.AAC_STREAM:

			if (headFrameType != V5PacketType.AAC_STREAM && !syncPtsBase) {
				aacTsSegmenter.setPts((System.currentTimeMillis() - ctime) * 90);
				syncPtsBase = true;
			}

			if (index == preAacIndex + 1) {
				preAacIndex++;
				skipAac = false;
				FrameData aacFrame = aacTsSegmenter.process(rawData);
				if (aacFrame != null)
					aacFrameCache.offer(aacFrame);
				preAacCTime = System.currentTimeMillis();
			} else if (index > preAacIndex + 1) {
				try {
					while (!aacLocking.compareAndSet(false, true)) {
					}
					aacRawCache.add(new RawItem(rawData, index));
					Collections.sort(aacRawCache, new RawDescComparator());
					skipAac = true;
				} finally {
					aacLocking.set(false);
				}
			}

			while (!aacRawCache.isEmpty() && aacRawCache.get(0).index == preAacIndex + 1) {
				preAacIndex++;
				skipAac = false;
				FrameData aacFrame = aacTsSegmenter.process(aacRawCache.remove(0).rawData);
				if (aacFrame != null)
					aacFrameCache.offer(aacFrame);
				preAacCTime = System.currentTimeMillis();
			}

			if (waitAac)
				writeFrame();

			break;

		case V5PacketType.H264_STREAM:

			try {
				while (!avcLocking.compareAndSet(false, true)) {
				}
				if (headFrameType != V5PacketType.H264_STREAM && !syncPtsBase) {
					h264TsSegmenter.setPts((System.currentTimeMillis() - ctime) * 90);
					syncPtsBase = true;
				}

				if (index == preAvcIndex + 1) {
					skipAvc = false;
					preAvcIndex = index;

					AvcResult avcResult = h264TsSegmenter.process(rawData);
					if (avcResult != null)
						avcResultDeque.offer(avcResult);
					preAvcCTime = System.currentTimeMillis();

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
					preAvcCTime = System.currentTimeMillis();

				}

				if (isTailAvc && !waitAac) {
					if(tsSecs[0] == null)
						return null;
					tsSegTime = (mixPts - ptsBase) / 90000F;
					isTailAvc = false;
					
					// Consider video in 10 seconds.
					if(!isLowTenSec && tsSecs[0] != null && tsSegTime < 10F && System.currentTimeMillis() - ctime > 60 * 1000) {
						isLowTenSec = true;
						return write2Ts();
					}
					
					return tsSecs[0] == null || tsSegTime < 10F ? null : write2Ts();
				}
				while (!avcResultDeque.isEmpty()) {
					writeFrame();
					if (isTailAvc && !waitAac) {
						while (!avcFrameCache.isEmpty()) {
							if (avcFrameCache.isEmpty())
								break;
							while (!waitAac) {
								if (avcFrameCache.isEmpty())
									break;
								FrameData frameData = avcFrameCache.pop();

								byte[] aacTsSegment = tsWriter.write(isFirstAvcPes, FrameDataType.MIXED, frameData);
								if (aacTsSegment != null) {
									tsSegmentLen += aacTsSegment.length;
									tsSecs[tsSecsPtr++] = aacTsSegment;
								}
								isFirstAvcPes = false;
								mixPts = mixPts > frameData.pts ? mixPts : frameData.pts;
								waitAac = (aacFrameCache.isEmpty()
										&& maxAacPts + aacTsSegmenter.getPtsIncPerFrame() < maxAvcPts
												+ h264TsSegmenter.getPtsIncPerFrame());
							}
						}
						tsSegTime = (mixPts - ptsBase) / 90000F;
						isTailAvc = false;
						
						// Consider video in 10 seconds.
						if(!isLowTenSec && tsSecs[0] != null && tsSegTime < 10F && System.currentTimeMillis() - ctime > 60 * 1000) {
							isLowTenSec = true;
							return write2Ts();
						}
						
						return tsSegTime < 10F ? null : write2Ts();
					}
				}
			} finally {
				avcLocking.set(false);
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

			if (result.isTailAvc) {
				isTailAvc = true;
				break;
			}
		}

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
				maxAacPts = maxAacPts > frameData.pts ? maxAacPts : frameData.pts;
				mixPts = mixPts > maxAacPts ? mixPts : maxAacPts;
			} else {

				FrameData frameData = avcFrameCache.pop();
				byte[] avcTsSegment = tsWriter.write(isFirstAvcPes, FrameDataType.MIXED, frameData);
				if (avcTsSegment != null) {
					tsSegmentLen += avcTsSegment.length;
					tsSecs[tsSecsPtr++] = avcTsSegment;
				}
				isFirstAvcPes = false;
				maxAvcPts = maxAvcPts > frameData.pts ? maxAvcPts : frameData.pts;
				mixPts = mixPts > maxAvcPts ? mixPts : maxAvcPts;
			}
		}
		waitAac = (aacFrameCache.isEmpty() && maxAacPts + aacTsSegmenter.getPtsIncPerFrame() < maxAvcPts + h264TsSegmenter.getPtsIncPerFrame());
	}

	public void prepare4nextTs() {
		isFirstAvcPes = true;
		isFirstAacPes = true;
		tsSegmentLen = 0;
		tsSecsPtr = 0;
		tsWriter.reset();

		for (int i = 0; i < tsSecs.length; i++) {
			tsSecs[i] = null;
		}
	}

	private byte[] write2Ts() {

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
