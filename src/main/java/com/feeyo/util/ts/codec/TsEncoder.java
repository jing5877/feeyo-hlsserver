package com.feeyo.util.ts.codec;

/**
 * --------------------------------- PES/TS 概述 -----------------------------------------
 * 
 *  PES  (  一个 PES 被切成N段，每段组成一个TS包的负载 )
 * 		TS 1
 * 	  	TS 2
 * 	  	TS ...
 * 	  	TS N
 * 			第一个TS包 = TS头 + TS自适应字段（8bit） + PES头 + PES可选头+ NALU AUD + SEI + SPS + PPS + TS负载(IDR Frame)
 * 			第二个TS包到本帧倒数第二个TS包:
 * 					固定的格式:  TsPacket = TsHeader(4bytes) + TsPayload(184bytes)
 * 					唯一变化的就是TsHeader中的字段ContinuityCounter(包递增计数器), 从0-15循环变化。
 * 			最后一个TS包 = TS头 + TS自适应字段 + 填充字段 + TS Payload
 * 
 * 
 * PSI ( 节目特定信息, 含 PAT+PMT ) 
 * 		PAT ( 节目相关表, 1个PAT表中包含N个PMT表的索引信息 )
 * 			PMT index 1
 * 			PMT index 2
 * 			PMT index ...
 * 			PMT index N
 * 
 * 		PMT ( 节目映射表，而1个PMT表中又包含视频PES 音频PES等索引信息 )
 * 			PID 1
 * 			PID 2
 * 			PID ...
 * 			PID N
 * 
 * 
 * @see https://github.com/aliyun/aliyun-media-c-sdk/blob/master/src/oss_media_hls.c
 * 
 * @see https://en.m.wikipedia.org/wiki/MPEG_transport_stream
 * @see https://en.m.wikipedia.org/wiki/MPEG_program_stream
 * @see https://en.m.wikipedia.org/wiki/Program-specific_information
 * 
 * 
 * @author yangtao
 * @author wangyaming
 * @author xuwenfeng
 * @author zhuam
 * 
 */

public class TsEncoder {
	
	public static final int MAX_PES_PACKET_SIZE = 1280 * 720 * 3;
	
	// Transport Stream packets are 188 bytes in length
	public static final int TS_PACKET_SIZE = 188;	
	public static final int TS_PAYLOAD_SIZE = 184;
	
	// Table 2-29 – Stream type assignments. page 66
	public static final byte STREAM_TYPE_AUDIO_AAC = 0x0f;
	public static final byte STREAM_TYPE_AUDIO_MP3 = 0x03;
	public static final byte STREAM_TYPE_AUDIO_AC3 = (byte)0x81;
	public static final byte STREAM_TYPE_AUDIO_DTS = (byte)0x8a;
	
	public static final byte STREAM_TYPE_VIDEO_H264 = 0x1b;
	public static final byte STREAM_TYPE_VIDEO_MPEG4 = 0x10;

	public static final int TS_AUDIO_PID = 0x0072;
	public static final int TS_VIDEO_PID = 0x0071;
	public static final int TS_PAT_PID = 0x0000;
	public static final int TS_PMT_PID = 0x0FFF;
	
	// Transport Stream Description Table
	public static final int TS_PAT_TABLE_ID = 0x00;
	public static final int TS_PMT_TABLE_ID = 0x02;
	
	
	public static final int TS_PTS_BASE = 63000;
	public static final int TS_SYNC_BYTE = 0x47;	// First byte of each TS packet. 

	private byte[] fFrameBuffer = null;
	private int fFrameSize = 0;
	
	private int fAudioContinuityCounter = 0;
	private int fVideoContinuityCounter = 0;
	private int fPATContinuityCounter = 0;
	private int fPMTContinuityCounter = 0;
	
	private byte[] tsBuffer;


	/**
	 * 转换成 ts，  pts 一秒相当于90000
	 */
	public byte[] encode(boolean isAudio, byte[] buffer, int bufferLength, long pts, long dts, boolean isEnd, boolean isFirstPes) {
		writeESPacket(isAudio, buffer, bufferLength, pts, dts, isEnd, isFirstPes);
		return tsBuffer;
	}


	/** 
	 * 写入指定的 ES 包
	 * @param isFirstPes 
	 */
	private void writeESPacket(boolean isAudio, byte[] data, int length, long pts, long dts, boolean isEnd, boolean isFirstPes) {
		
		// 无效的帧
		if (fFrameSize + length > MAX_PES_PACKET_SIZE) {
			fFrameSize = 0;
			return;
		}
		
		if (fFrameBuffer == null) {
			fFrameBuffer = new byte[MAX_PES_PACKET_SIZE];
			fFrameSize = 0;
		} 

		if (fFrameSize == 0) {
			
			writePATPacket();
			writePMTPacket(isAudio);
			
			int pesHeaderLength = writePESHeader(isAudio, fFrameBuffer, length, pts, dts);
			fFrameSize += pesHeaderLength;
			if( !isAudio ) {
				byte[] naluAud = {0x00, 0x00, 0x00, 0x01, 0x09, (byte) 0xf0};
				System.arraycopy(naluAud, 0, fFrameBuffer, fFrameSize, naluAud.length);
				fFrameSize += naluAud.length;
			}
			
			System.arraycopy(data, 0, fFrameBuffer, fFrameSize, length);
			fFrameSize += length;

		} else {
			System.arraycopy(data, 0, fFrameBuffer, fFrameSize, length);
			fFrameSize += length;
		}

		if (isEnd) {
			writePESPacket(isAudio, fFrameBuffer, fFrameSize, pts, isFirstPes);
			fFrameSize = 0;
		}
	}
	

	/** 
	 * 多个TS包打包成一个PES包,那么第一个TS包,应该包含PES Header. 
	 * 
	 * PTS 显示时间戳 
	 * DTS 解码时间戳
	 * DSM 数据存储媒体
	 * ESCE 基本流时钟基准
	 */
	private int writePESHeader(boolean isAudio, byte[] buffer, int length, long pts, long dts) {
		
		//对于音频而言，只需要有pts即可
		//对于视频而言，需要pts和dts。当只有I帧和P帧时，dts和pts顺序相同，可以设置为相等，包含B帧时，应小于下一P帧的pts大于上一P帧的pts
		length = length + 8 + 5;
		byte[] pes = buffer;
		int i = 0;

	    // 3B, 包起始码前缀
		pes[i++] = 0x00;
		pes[i++] = 0x00;
		pes[i++] = 0x01;

		// 1B, 数据流识别码
		pes[i++] = isAudio ? (byte) 0xc0 : (byte) 0xe0;  //8bits
		
		// 2B, PES 包长度
		if(isAudio) {
			pes[i++] = (byte) ((length >> 8) & 0xFF);
			pes[i++] = (byte) (length & 0xFF);
		} else {
			pes[i++] = 0x00; // 为0表示不受限制
			pes[i++] = 0x00; // 16:
		}

		// 2B, PES 包头识别标志, Flags
		pes[i++] = (byte) 0x80; // (1000 0100) data_alignment 10000001
		pes[i++] = (byte) 0xc0; // (1100 0000) PTS DTS

		// 1B，PES 包头长度
		pes[i++] = 0x0a; 

		
		// 5B， PTS
		//
		pes[i++] = (byte) (((pts >> 30) & 0xFE) | 0x31); 	// 4: '0010' or '0011',
															// 3: PTS, 1: marker
		pes[i++] = (byte) ((pts >> 22) & 0xff); 			// 15: PTS
		pes[i++] = (byte) (((pts >> 14) & 0xFE) | 0x01); 	// 1: marker
		pes[i++] = (byte) ((pts >> 7) & 0xff); 				// 15: PTS
		pes[i++] = (byte) ((pts << 1) & 0xFE | 0x01); 		// 1: marker
		
		// 5B， DTS
		pes[i++] = (byte) (((dts >> 29) & 0xFE) | 0x11); 	// 4: '0010' or '0011',
															// 3: PTS, 1: marker
		pes[i++] = (byte) ((dts >> 22) & 0xff); 			// 15: PTS
		pes[i++] = (byte) (((dts >> 14) & 0xFE) | 0x01); 	// 1: marker
		pes[i++] = (byte) ((dts >> 7) & 0xff); 				// 15: PTS
		pes[i++] = (byte) ((dts << 1) & 0xFE | 0x01); 		// 1: marker

		return 9 + 5 + 5;
	}

	/** 
	 * 写入指定的 PES 包
	 * @param isFirstPes 
	 */
	private void writePESPacket(boolean isAudio, byte[] data, int length, long pts, boolean isFirstPes) {
		
		byte[] buffer = new byte[256];
		boolean isFirstTs = true;
		long pcr = pts;				
		int leftover = length;
		
		int pid = isAudio ? TS_AUDIO_PID : TS_VIDEO_PID;

		int dataOffset = 0;
		while ( leftover > 0 ) {
			
			boolean isAdaptationField = (isFirstTs || ( leftover < TS_PAYLOAD_SIZE )) ? true : false;

			// TS Packet Header
			buffer[0] = TS_SYNC_BYTE;
			buffer[1] = (byte) ((isFirstTs ? 0x40 : 0x00) | ((pid >> 8) & 0x1f));				//
			buffer[2] = (byte) (pid & 0xff);													// 包的ID号
			buffer[3] = (byte) ((isAdaptationField ? 0x30 : 0x10) | (isAudio ? fAudioContinuityCounter : fVideoContinuityCounter));		
			// Continuity counter 用于判断packet有无丢失，若无丢失相同pid的CC为连续的
			
			if(isAudio)
				fAudioContinuityCounter = (fAudioContinuityCounter + 1) & 0x0F;
			else
				fVideoContinuityCounter = (fVideoContinuityCounter + 1) & 0x0F;

			long size = 0;
			if ( leftover < TS_PAYLOAD_SIZE ) {
				
				//最后一个ts包需要填充0xff 达到188bit
				size = leftover;
				long stuffing = TS_PAYLOAD_SIZE - size;		// 填充 size

				if (stuffing > 0) {
					buffer[4] = (byte) (stuffing - 1); // 长度
				}

				if (stuffing > 1) {
					buffer[5] = 0x00; // 总是为 0x00
				}

				if (stuffing > 2) {
					for (int i = 0; i < stuffing - 2; i++) {
						buffer[i + 6] = (byte) 0xff;
					}
				}
				
				System.arraycopy(data, dataOffset, buffer, (int) (4 + stuffing), (int) size);

			} else {
				
				size = TS_PAYLOAD_SIZE;
				
				// 第一个ts数据包 增加自适应数据（8bit）
				if (isFirstTs) {
					
					size = size - 8;

					buffer[4] = 0x07;		  					  // initial adaptation field length
					buffer[5] |= isFirstPes ? 0x50 : 0x10; 		  // flag bits 0001 0000 -> got PCR
					
					writePCR(buffer, 6, pcr); 					  // 6 Bytes
					System.arraycopy(data, dataOffset, buffer, 12, (int) size);
					
				} else {
					System.arraycopy(data, dataOffset, buffer, 4, (int) size);
				}
			}

			append(buffer, 0, TS_PACKET_SIZE);

			isFirstTs = false;
			dataOffset += size;
			leftover -= size;
		}

	}
	
	// 写入指定的 PCR 节目时钟基准
	private void writePCR(byte[] buffer, int offset, long pcr) {
		byte[] p = buffer;
		if (p == null) {
			return;
		}

		int i = offset;

		// (33bit) program clock reference base
		p[i++] = (byte) ((pcr >> 25) & 0xff); //
		p[i++] = (byte) ((pcr >> 17) & 0xff); //
		p[i++] = (byte) ((pcr >> 9) & 0xff); //
		p[i++] = (byte) ((pcr >> 1) & 0xff); //

		// p[i++] = ((pcr & 0x01) << 7) | 0x7E; //(6bit) reserved
		p[i++] = 0x00;

		// (9bit) Program clock reference extension
		p[i++] = 0x00; //

	}
	
	
	// 生成 PAT 包 
	private void writePATPacket() {
		
		byte[] buffer = new byte[TS_PACKET_SIZE];
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = (byte) 0xff;
		}

		int PAT_TABLE_OFFSET = 5;
		byte[] p = buffer;
		int tableLength = 13; // 13
		int i = 0;

		// TS Packet Header (4 Bytes)
		p[i++] = TS_SYNC_BYTE; // 8: 同步字节, 为 0x47
		p[i++] = 0x40; // (0100 0000) 1: 传输误码指示符, 1: 起始指示符, 1: 优先传输
		p[i++] = 0x00; // 13: PID
		p[i++] = (byte) (0x10 | fPATContinuityCounter); // 2: 传输加扰, 2: 自适应控制 4:
		
		fPATContinuityCounter = (fPATContinuityCounter + 1) & 0x0F; //包递增计数器(0-15)

		p[i++] = 0x00; 	//起始指示符为1，故调整一个字节

		// PAT Table
		p[i++] = TS_PAT_TABLE_ID; // 8: 固定为0x00, 标志是该表是PAT
		p[i++] = (byte) (0xB0 | ((tableLength >> 8) & 0x0F)); // 1bit: 段语法标志位，固定为1; 1bit: 0; 2bit: 保留
		p[i++] = (byte) (tableLength & 0xff); // 12: 13, 表示这个字节后面有用的字节数，包括CRC32

		p[i++] = 0x00; //
		p[i++] = 0x01; // 16: 该传输流的ID，区别于一个网络中其它多路复用的流
		p[i++] = (byte) (0xC1); // 2: 保留; 5: 范围0-31，表示PAT的版本号; 1:发送的PAT是当前有效还是下一个PAT有效 (1100 0001)
		p[i++] = 0x00; // 8: 分段的号码。PAT可能分为多段传输，第一段为00，以后每个分段加1，最多可能有256个分段
		p[i++] = 0x00; // 8: 最后一个分段的号码

		// Programs 节目列表
		p[i++] = 0x00; //
		p[i++] = 0x01; // 16: 节目号为0x0000表示为network_PID ,节目号为0x0001 表示为PMT ，PMT_PID 为0x0fff--(13bit)
		p[i++] = (byte) (0xE0 | ((TS_PMT_PID >> 8) & 0x1F)); // 3: 保留位 (1110 1111)
		p[i++] = (byte) (TS_PMT_PID & 0xff);
		

		// CRC 32
		long crc = TsUtil.mpegts_crc32(buffer, PAT_TABLE_OFFSET, (tableLength + 3) - 4);
		p[i++] = (byte) ((crc >> 24) & 0xff);
		p[i++] = (byte) ((crc >> 16) & 0xff);
		p[i++] = (byte) ((crc >> 8) & 0xff);
		p[i++] = (byte) ((crc) & 0xff);

		append(buffer, 0, TS_PACKET_SIZE);
	}
	
	/**
	 * 生成 PMT 包, 这个包用来描述指定的节目的编码格式等信息
	 */
	private void writePMTPacket(boolean isAudio) {
		
		int PMT_TABLE_OFFSET = 5;

		byte[] buffer = new byte[TS_PACKET_SIZE];
		for (int i = 0; i < buffer.length; i++) {
			buffer[i] = (byte) 0xff;
		}

		byte[] p = buffer;
		//mixture tableLength为0x17,pure 为0x12
		int tableLength = 18; // PMT 表数据内容长度, 不包括 PMT 表前 3 个字节
		int i = 0;

		// TS Packet Header (4 Bytes)
		p[i++] = TS_SYNC_BYTE; // 8 bit: 同步字符, 总是为 0x47
		p[i++] = (byte) (0x40 | ((TS_PMT_PID >> 8) & 0x1F)); // 1 bit: 传输误码指示符; 1bit: 起始指示符; 1 bit: 优先传输
		p[i++] = (byte) (TS_PMT_PID & 0xff); // 13 bit: PID
		p[i++] = (byte) (0x10 | fPMTContinuityCounter); // 2 bit: 传输加扰, 2 bit:自适应控制, 4
		fPMTContinuityCounter = (fPMTContinuityCounter + 1) & 0x0F;

		//
		p[i++] = 0x00; // 起始指示符为1，故调整一个字节

		// PMT Table
		p[i++] = TS_PMT_TABLE_ID; // 8 bit: 固定为0x02, 标志是该表是PMT
		p[i++] = (byte) (0xB0 | ((tableLength >> 8) & 0x0F)); // 1 bit:
																// 段语法标志位，固定为1;
																// 1
		// bit: 0; 2 bit: 保留
		// (1011 0000)
		p[i++] = (byte) (tableLength & 0xff); // 12 bit: 表示这个字节后面有用的字节数，包括CRC32
		
		
		p[i++] = 0x00; //
		p[i++] = 0x01; // 16 bit: 指出该节目对应于可应用的 Program map PID
		
		// 2 bit: 保留; 5 bit: 指出TS流中Program map section的版本号; 
		//1 bit: 当该位置1时，当前传送的 Program map section 可用 (11000001)
		p[i++] = (byte) (0xC1); 
		
		
		p[i++] = 0x00; // 8 bit: 固定为0x00
		p[i++] = 0x00; // 8 bit: 固定为0x00

		int tsPcrPid = isAudio ? TS_AUDIO_PID : TS_VIDEO_PID;
		p[i++] = (byte) (0xE0 | ((tsPcrPid >> 8) & 0x1F)); // 3 bit: 保留
		p[i++] = (byte) (tsPcrPid & 0xff); // 13 bit: 节目号 指明 TS 包的PID值
		p[i++] = (byte) (0xF0); // 4 bit: 保留位
		p[i++] = 0x00; // 12 bit: 前两位bit为00。该域指出跟随其后对节目信息的描述的 byte 数

		// 视频流的描述
		p[i++] = isAudio ? STREAM_TYPE_AUDIO_AAC : STREAM_TYPE_VIDEO_H264; //stram_type: 音频0x0F 视频0x1b
		
		int tsPid = isAudio ? TS_AUDIO_PID: TS_VIDEO_PID;
		p[i++] = (byte) (0xE0 | ((tsPid >> 8) & 0x1F)); //
		p[i++] = (byte) (tsPid & 0xff); //
		p[i++] = (byte) (0xF0); //
		p[i++] = 0x00; //

		// 32 位 CRC 校验码
		long crc = TsUtil.mpegts_crc32(buffer, PMT_TABLE_OFFSET, (tableLength + 3) - 4);
		p[i++] = (byte) ((crc >> 24) & 0xff);
		p[i++] = (byte) ((crc >> 16) & 0xff);
		p[i++] = (byte) ((crc >> 8) & 0xff);
		p[i++] = (byte) ((crc) & 0xff);

		append(buffer, 0, TS_PACKET_SIZE);
	}
	
	private void append(byte[] newBuffer, int index, int length) {
		
		if (newBuffer == null) {
			return;
		}
		
		if (newBuffer.length != length) {
			byte[] buff = new byte[length];
			System.arraycopy(newBuffer, index, buff, 0, length);
			newBuffer = buff;
		}
		
		if (tsBuffer == null) {
			tsBuffer = newBuffer;
			return;
		}
		tsBuffer = TsUtil.margeByteArray(tsBuffer, newBuffer);
	}
	
	public void close() {
		tsBuffer = null;
	}
	
	public void clear() {
		tsBuffer = null;
		fAudioContinuityCounter = 0;
		fVideoContinuityCounter = 0;
		fPATContinuityCounter = 0;
		fPMTContinuityCounter = 0;
	}
}
