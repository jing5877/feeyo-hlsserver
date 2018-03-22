package com.feeyo.util.ts.codec;

import java.util.ArrayDeque;
import java.util.Random;

import com.feeyo.hls.ts.segmenter.AacH264MixedTsSegmenter;
import com.feeyo.hls.ts.segmenter.AbstractTsSegmenter;
import com.feeyo.net.udp.packet.V5PacketType;
import com.feeyo.net.udp.test.TestDataUtil;

public class AacH264MixedTsEncoderTest {
	
	
	
	public static void main(String[] args) {
	
		byte[] videoData = TestDataUtil.getVideoData();
	
		ArrayDeque<AvFrame> videoDeque = new ArrayDeque<AvFrame>();
		//丢弃最后不足2048的语音部分
		for(int i=0; i <= videoData.length - 2048; i= i+2048 ) {
			byte[] dest = new byte[2048]; 
			System.arraycopy(videoData, i, dest, 0, 2048);
			videoDeque.offer(new AvFrame(dest, 0));
		}
		
		
		byte[] audioData = TestDataUtil.getAudioData();
	
		ArrayDeque<AvFrame> audioDeque = new ArrayDeque<AvFrame>();
		//丢弃最后不足2048的语音部分
		for(int k=0; k <= audioData.length - 2048; k= k + 2048) {
			byte[] dest = new byte[2048]; 
			System.arraycopy(audioData, k, dest, 0, 2048);
			audioDeque.offer(new AvFrame(dest, 1));
		}
		
		ArrayDeque<AvFrame> avDeque = new ArrayDeque<AvFrame>();
		
		//Random
		Random rand = new Random();
		while(!audioDeque.isEmpty() || !videoDeque.isEmpty()) {
			int randNum = rand.nextInt(100) + 1;
			if(randNum > 50) {
				if(!audioDeque.isEmpty()) {
					avDeque.offer(audioDeque.pop());
				}else {
					while(!videoDeque.isEmpty())
						avDeque.offer(videoDeque.pop());
				}
			}else {
				if(!videoDeque.isEmpty()) {
					avDeque.offer(videoDeque.pop());
				}else {
					while(!audioDeque.isEmpty())
						avDeque.offer(audioDeque.pop());
				}
			}
		}
		
		AbstractTsSegmenter tsSegmenter = new AacH264MixedTsSegmenter();
		
		
		while(true) {
			for(AvFrame avFrame : avDeque) {
				
				if(avFrame.type == 0) {
					tsSegmenter.getTsBuf(V5PacketType.H264_STREAM, avFrame.payload);
				}else if(avFrame.type == 1){
					tsSegmenter.getTsBuf(V5PacketType.AAC_STREAM, avFrame.payload);
				}else {
					System.out.println("## ERROR!");
				}
			}
		}
	}
	
	static class AvFrame {
		public byte[] payload;
		public int type;	//0 video 1 audio
		public AvFrame(byte[] payload, int type) {
			super();
			this.payload = payload;
			this.type = type;
		}
	}

}
