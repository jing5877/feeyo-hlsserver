package com.feeyo.net.udp.test;

import java.util.List;

import com.feeyo.net.udp.packet.V5Packet;
import com.feeyo.net.udp.packet.V5PacketDecoder;
import com.feeyo.net.udp.packet.V5PacketEncoder;
import com.feeyo.net.udp.packet.V5PacketErrorException;
import com.feeyo.net.udp.packet.V5PacketIdGenerator;

public class V5PacketTest {
	
	private static V5PacketEncoder encoder = new V5PacketEncoder();
	private static V5PacketDecoder decoder = new V5PacketDecoder();
	
	public static void main(String[] args) {
		
		// 18
		V5PacketIdGenerator idGenerator = new V5PacketIdGenerator();
		String text = "Hello world ccccccccccccccccccccccc3333333344444ccccccccccc! zzzzzzz@";
		int MTU = 50;
		
		// 原始包
		int packetSender = 12;
		byte packetType = 1;
		byte[] packetReserved = new byte[]{ 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
		int packetId = idGenerator.getId();
		List<V5Packet> packets = encoder.encode(MTU, packetSender, packetType, packetReserved, packetId, text.getBytes());
		
		for (int i = 0; i < packets.size(); i++) {
			V5Packet p = packets.get(i);
			System.out.println( p );
			
			byte[] pBytes = encoder.encode(p);
			
			try {
				
				V5Packet packet = decoder.decode( pBytes );
				if ( packet != null ) {
					System.out.println( packet );
				}
		
			} catch (V5PacketErrorException e) {
				e.printStackTrace();
			}
		}
		
	}

}
