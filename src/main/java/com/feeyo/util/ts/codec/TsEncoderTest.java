package com.feeyo.util.ts.codec;

import java.util.ArrayList;
import java.util.List;

import com.feeyo.hls.ts.segmenter.H264TsSegmenter;
import com.feeyo.net.udp.packet.V5PacketType;
import com.feeyo.test.TestDataUtil;

public class TsEncoderTest {
	
	public static void main(String[] args) {
//		AacTsSegmenter tsSegmenter = new AacTsSegmenter();
//		byte[] data = TestDataUtil.getAudioData();
		
		H264TsSegmenter tsSegmenter = new H264TsSegmenter();
		byte[] data = TestDataUtil.getVideoData();

		List<byte[]> dataList = new ArrayList<byte[]>();
		//丢弃最后不足2048的语音部分
		for(int i=0; i <= data.length - 2048; i= i+2048 ) {
			byte[] dest = new byte[2048]; 
			System.arraycopy(data, i, dest, 0, 2048);
			dataList.add(dest);
		}
		
		while(true) {
			for(byte[] media : dataList) {
				tsSegmenter.getTsBuf(V5PacketType.H264_STREAM, media, null);
			}
		}
		
	}

}
