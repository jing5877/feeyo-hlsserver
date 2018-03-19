package com.feeyo.net.udp.packet;

public class V5PacketErrorException extends Exception {

	private static final long serialVersionUID = -143989805382831875L;


	public V5PacketErrorException(String errorCode) {
		super(errorCode);
	}

	public V5PacketErrorException(String errorCode, Throwable cause) {
		super(errorCode, cause);
	}

	public V5PacketErrorException(String errorCode, String errorDesc) {
		super(errorCode + ":" + errorDesc);
	}

	public V5PacketErrorException(String errorCode, String errorDesc, Throwable cause) {
		super(errorCode + ":" + errorDesc, cause);
	}

	public V5PacketErrorException(Throwable cause) {
		super(cause);
	}
	
}
