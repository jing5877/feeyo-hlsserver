package com.feeyo.net.udp.packet;

/**
 * 
 * @author zhuam
 *
 */
public class V5PacketIdGenerator {

	private static final int MAX_VALUE = Integer.MAX_VALUE;

	private int packetId = 0;
	private final Object lock = new Object();

	public int getId() {
		synchronized (lock) {
			if (packetId >= MAX_VALUE) {
				packetId = 0;
			}
			return ++packetId;
		}
	}
	
}
