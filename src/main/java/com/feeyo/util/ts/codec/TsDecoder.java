package com.feeyo.util.ts.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Bytes;

/**
 * 
 * @author xuwenfeng@variflight.com
 *
 */
public class TsDecoder {
	
	private final Logger LOGGER = LoggerFactory.getLogger(TsDecoder.class);
	
	public static final int MAX_PES_PACKET_SIZE = 1280 * 720 * 5;
	
	// Transport Stream Description Table
	private static final int TS_PAT_TABLE_ID = 0x00;
	private static final int TS_PMT_TABLE_ID = 0x02;
	
	private static final byte sync_byte = 0x47;
	private static final byte[] nalu_aud = {0x00, 0x00, 0x00, 0x01, 0x09, (byte) 0xF0};
	
	private static final byte[] video_flag = {0x00, 0x00, 0x01, (byte) 0xE0};
	private static final byte[] audio_flag = {0x00, 0x00, 0x01, (byte) 0xC0};
	
	private byte[] videoFrameBuffer;
	private byte[] audioFrameBuffer;
	
	private int videoBufPtr = 0; 
	private int audioBufPtr = 0;
	
	private int currentStreamType = -1; // -1 : undefined; 0 : video ; 1 ： audio 
	
	int count = 1;
	
	public TsDecoder() {
		videoFrameBuffer = new byte[MAX_PES_PACKET_SIZE];
		audioFrameBuffer = new byte[MAX_PES_PACKET_SIZE];
	}
	
	public byte[][] decode(byte[] data) {
		
		byte[][] rawData = new byte[2][];
		for(int i=0; i <= data.length - 188; i= i+188 ) {
			byte[] dest = new byte[188]; 
			System.arraycopy(data, i, dest, 0, 188);
			System.out.println(count++);
			decodeTsPacket(dest);
		}
		
		rawData[0] = videoFrameBuffer;
		rawData[1] = audioFrameBuffer;
		return rawData;
	}
	
	public void decodeTsPacket(byte[] data) {
		
		int index = 0; // from 0 to 187
		
		//
		if(data.length != 188) {
			LOGGER.warn("Not A Complete Packetized Elementary Stream.");
			return;
		}
		
		// 4 byte TS Header
		/*
		 * 8B : sync_byte 同步字节
		 * 1B ：transport_error_indicator 错误提示信息
		 * 1B : payload_unit_start_indicator 负载单元开始标志（packet不满188字节时需填充）
		 * 1B : transport_priority 传输优先级标志（1：优先级高）
		 * 13B: PID Packet ID号码，唯一的号码对应不同的包
		 * 2B : transport_scrambling_control 加密标志（00：未加密；其他表示已加密）
		 * 2B : adaptation_field_control 附加区域控制
		 * 4B : continuity_counter 包递增计数器
		 */
		if(data[0] != sync_byte) {
			LOGGER.error("NOT A　Legal Ts Packet");
			return;
		}
		
		
		int startIndicator = (int) (data[1] >> 6);
		
		index += 4;
		index += startIndicator;
		
		//skip pat & pmt table
		if(data[4]==0x00 && (data[5] == TS_PAT_TABLE_ID || data[5] == TS_PMT_TABLE_ID) && startIndicator == 1)
			return;
		
		//判断是否有adaptationField
		int adaptationFieldControl = ((data[3] & 0x30) >> 4) &  0xFF;  
		boolean containAdaptationField = false;
		switch(adaptationFieldControl) {
		case 0:		//reserved
			return;
		case 1:		//无 adaptation_field，仅有效载荷
			break;
		case 2:		//仅有 Adaptation_field，无有效载荷
			return;
		case 3:		//Adaptation_field 后随有效载荷
			containAdaptationField = true;
			break;
		}
		
		if(containAdaptationField) {
			int adaptationFieldLength = data[4] & 0xFF;
			index += (1 + adaptationFieldLength);		// adaptation_field_length 占8b
		}
		
		//PES的第1个 ts packet
		int flagPos = Bytes.indexOf(data, video_flag);
		if(flagPos != -1 ) {
			System.out.println("---video");
			currentStreamType = 0;
			int pesHeaderDataLength = data[flagPos + 5 + 3] & 0xFF;
			//6 bytes pes header, 3 bytes pes optional header flag, pesHeaderDataLength is pts & dts length
			int payLoadPos = flagPos + 6 + 3 + pesHeaderDataLength;	
			
			int audPos = Bytes.indexOf(data, nalu_aud);
			if(audPos != -1) 
				payLoadPos = audPos + nalu_aud.length;
			System.arraycopy(data, payLoadPos, videoFrameBuffer, videoBufPtr, data.length - payLoadPos);
			videoBufPtr +=  data.length - payLoadPos;
			return;
			
		}else {
		
			flagPos = Bytes.indexOf(data, audio_flag);
			if(flagPos != -1) {
				System.out.println("---audio");
				currentStreamType = 1;
				if(flagPos + 5 + 3 > data.length -1) 
					return;
				int pesHeaderDataLength = data[flagPos + 5 + 3] & 0xFF;
				//6 bytes pes header, 3 bytes pes optional header flag, pesHeaderDataLength is pts & dts length
				int payLoadPos = flagPos + 6 + 3 + pesHeaderDataLength;	
				
				System.arraycopy(data, payLoadPos, audioFrameBuffer, audioBufPtr, data.length - payLoadPos);
				audioBufPtr +=  data.length - payLoadPos;
				return;
			}
		}
		
		if(currentStreamType == 0) {
			int audPos = Bytes.indexOf(data, nalu_aud);
			
			if(audPos != -1) {
				System.arraycopy(data, index, videoFrameBuffer, videoBufPtr, audPos - index);
				videoBufPtr +=  audPos - index;
				
				System.arraycopy(data, audPos + nalu_aud.length, videoFrameBuffer, videoBufPtr, data.length - audPos - nalu_aud.length);
				videoBufPtr +=   data.length - audPos - nalu_aud.length;
			}else {
				
				System.arraycopy(data, index, videoFrameBuffer, videoBufPtr, data.length - index);
				videoBufPtr +=  data.length - index;
			}
		}else if(currentStreamType == 1) {
			System.arraycopy(data, index, audioFrameBuffer, audioBufPtr, data.length - index);
			audioBufPtr +=  data.length - index;
		}
		
	}
	
	public void close() {
		videoFrameBuffer = null;
		audioFrameBuffer = null;
		videoBufPtr = 0;
		audioBufPtr = 0;
	}
	
}
