package com.feeyo.net.udp.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 
 * @author zhuam
 *
 */
public class V5PacketEncoder {

	public List<V5Packet> encode(int mtu, int packetSender, byte packetType, byte[] packetReserved, int packetId, byte[] data) {
		
		//
		if ( mtu <= V5Packet.HEAD_LENGTH + V5Packet.TAIL_LENGTH ) {
			throw new java.lang.IllegalArgumentException(" mtu must be greater than packet header size , " 
						+ " headSize=" + V5Packet.HEAD_LENGTH + ", tailSize=" + V5Packet.TAIL_LENGTH );
		}
		
		//1、根据原始的包长和MTU值 来分成几个碎片包
		int maxLen = mtu - V5Packet.HEAD_LENGTH - V5Packet.TAIL_LENGTH;
		int n = data.length / maxLen;
		if ( (data.length % maxLen) != 0 )
			n++;
		
		//2、构造碎片包
		List<V5Packet> packs = new ArrayList<V5Packet>(n);
		for (int i = 0; i < n; i++) {

			int dataOffset =  i * maxLen;
			int dataLength = (i < (n - 1)) ? maxLen : data.length - i * maxLen;
			
			//
			byte[] packetData = new byte[ dataLength ];
			
			//
			int idx = 0;
			int start = dataOffset;
			int end = dataOffset + dataLength;
			for (int j = start; j < end; j++) {
				packetData[idx++] = data[j];
			}
			
			// 包尾，crc
			CRC32 crc32 = new CRC32();
			crc32.update( packetData, 0, packetData.length);
			long crc = crc32.getValue();
	
			int packetLength = data.length;
			int packetOffset =  i * maxLen;
			
			packs.add( new V5Packet(packetSender, packetType, packetReserved, packetId, packetLength, packetOffset, packetData, crc) );
		}
		
		return packs;
		
	}
	
	public byte[] encode(V5Packet packet) {
		
		int packetSender = packet.getPacketSender();
		byte packetType = packet.getPacketType();
		byte[] packetReserved = packet.getPacketReserved();
		int packetId = packet.getPacketId();
		int packetLength = packet.getPacketLength();
		int packetOffset = packet.getPacketOffset();
		byte[] packetData = packet.getPacketData();
		long crc = packet.getCrc();
		
		
		byte[] pdu = new byte[V5Packet.HEAD_LENGTH + packetData.length + V5Packet.TAIL_LENGTH];
		int idx = 0;
		
		
		pdu[idx++] = 89;
		pdu[idx++] = 89;
		
		// 发送方
		pdu[idx++] = ByteUtil.getByte3(packetSender);
		pdu[idx++] = ByteUtil.getByte2(packetSender);
		pdu[idx++] = ByteUtil.getByte1(packetSender);
		pdu[idx++] = ByteUtil.getByte0(packetSender);
		
		// 包类型
		pdu[idx++] = packetType;
		
		// 预留
		System.arraycopy(packetReserved, 0, pdu, idx, packetReserved.length);
		idx += 8;
		
		// 包唯一编号
		pdu[idx++] = ByteUtil.getByte3(packetId);
		pdu[idx++] = ByteUtil.getByte2(packetId);
		pdu[idx++] = ByteUtil.getByte1(packetId);
		pdu[idx++] = ByteUtil.getByte0(packetId);
		
		// 包长
		pdu[idx++] = ByteUtil.getByte3(packetLength);
		pdu[idx++] = ByteUtil.getByte2(packetLength);
		pdu[idx++] = ByteUtil.getByte1(packetLength);
		pdu[idx++] = ByteUtil.getByte0(packetLength);
		
		// 包偏移值
		pdu[idx++] = ByteUtil.getByte3(packetOffset);
		pdu[idx++] = ByteUtil.getByte2(packetOffset);
		pdu[idx++] = ByteUtil.getByte1(packetOffset);
		pdu[idx++] = ByteUtil.getByte0(packetOffset);
		
		
		idx = V5Packet.HEAD_LENGTH;
		
		for (int j = 0; j < packetData.length; j++) {
			pdu[idx++] = packetData[j];
		}
		
		// 包尾，crc		
		pdu[idx++] = ByteUtil.getByte7( crc );
		pdu[idx++] = ByteUtil.getByte6( crc );
		pdu[idx++] = ByteUtil.getByte5( crc );
		pdu[idx++] = ByteUtil.getByte4( crc );
		pdu[idx++] = ByteUtil.getByte3( crc );
		pdu[idx++] = ByteUtil.getByte2( crc );
		pdu[idx++] = ByteUtil.getByte1( crc );
		pdu[idx++] = ByteUtil.getByte0( crc );

		return pdu;
	}

}
