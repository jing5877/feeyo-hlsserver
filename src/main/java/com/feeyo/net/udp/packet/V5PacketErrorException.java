package com.feeyo.net.udp.packet;

public class V5PacketErrorException extends Exception {

	private static final long serialVersionUID = -143989805382831875L;

	private byte[] data;

	public V5PacketErrorException(String errorCode, byte[] data) {
		super(errorCode);
		this.data = data;
	}

	public byte[] getData() {
		return data;
	}

}
