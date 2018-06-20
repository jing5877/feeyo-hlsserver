package com.feeyo.util.ts.codec;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.feeyo.hls.ts.segmenter.AacH264MixedTsSegmenter;
import com.feeyo.hls.ts.segmenter.AbstractTsSegmenter;
import com.feeyo.net.udp.packet.ByteUtil;
import com.feeyo.net.udp.packet.V5PacketType;
import com.feeyo.test.TestDataUtil;

public class AacH264MixedTsEncoderTest {

	public static void main(String[] args) {

		byte[] videoData = TestDataUtil.getVideoData();

		ArrayDeque<AvFrame> videoDeque = new ArrayDeque<AvFrame>();
		// 丢弃最后不足2048的语音部分
		long avcCounter = 0;
		for (int i = 0; i <= videoData.length - 2048; i = i + 2048) {
			byte[] dest = new byte[2048];
			System.arraycopy(videoData, i, dest, 0, 2048);
			videoDeque.offer(new AvFrame(dest, 0, avcCounter++));
		}

		byte[] audioData = TestDataUtil.getAudioData();
		long aacCounter = 0;
		ArrayDeque<AvFrame> audioDeque = new ArrayDeque<AvFrame>();
		// 丢弃最后不足2048的语音部分
		for (int k = 0; k <= audioData.length - 2048; k = k + 2048) {
			byte[] dest = new byte[2048];
			System.arraycopy(audioData, k, dest, 0, 2048);
			audioDeque.offer(new AvFrame(dest, 1, aacCounter++));
		}

		List<AvFrame> avList = new ArrayList<AvFrame>();

		// Random
		Random rand = new Random();
		while (!audioDeque.isEmpty() || !videoDeque.isEmpty()) {
			int randNum = rand.nextInt(100) + 1;
			if (randNum > 50) {
				if (!audioDeque.isEmpty()) {
					avList.add(audioDeque.pop());
				} else {
					while (!videoDeque.isEmpty())
						avList.add(videoDeque.pop());
				}
			} else {
				if (!videoDeque.isEmpty()) {
					avList.add(videoDeque.pop());
				} else {
					while (!audioDeque.isEmpty())
						avList.add(audioDeque.pop());
				}
			}
		}

		AbstractTsSegmenter tsSegmenter = new AacH264MixedTsSegmenter();
		tsSegmenter.initialize(8000, 16, 1, 25);

		for (AvFrame avFrame : avList) {

			if (avFrame.type == 0) {
				tsSegmenter.getTsBuf(V5PacketType.H264_STREAM, avFrame.payload, ByteUtil.longToBytes(avFrame.index));
			} else if (avFrame.type == 1) {
				tsSegmenter.getTsBuf(V5PacketType.AAC_STREAM, avFrame.payload, ByteUtil.longToBytes(avFrame.index));
			} else {
				System.out.println("## ERROR!");
			}
		}
	}

	static class AvFrame {
		public byte[] payload;
		public int type; // 0 video 1 audio
		public long index;

		public AvFrame(byte[] payload, int type, long index) {
			super();
			this.payload = payload;
			this.type = type;
			this.index = index;
		}
	}

}
