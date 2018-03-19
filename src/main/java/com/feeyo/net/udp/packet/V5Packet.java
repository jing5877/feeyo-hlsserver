package com.feeyo.net.udp.packet;


/**
 * 新版本协议
 *  
 *  ---------------------------------------------------------------------------
 *  89 89 (2 byte), 
 *  packetSender(4 byte), packetType(1 byte), packetReserved(8 byte), packetId(4 byte), 
 *  packetLength(4 byte), packetOffset(4 byte), packet(n byte), CRC(8byte)
 *  
 *  
 * @author zhuam
 *
 */
public class V5Packet {

	public static final int HEAD_LENGTH = 27;
	public static final int TAIL_LENGTH = 8;

	//------------------------包头--------------------------
								// 89 89
	int  packetSender;			// 数据发送方 (4 byte)
	byte packetType;			// 数据类型 (1 byte)
	byte[] packetReserved;		// 预留 (8 byte)
	int  packetId;   			// 数据包的唯一标识符 (4 byte)
	int  packetLength;			// 数据包长度 (4 byte)
	int  packetOffset;			// 数据包的偏移 (4 byte)
	
	
	byte[] packetData;	// 包体
	long crc;			// 校验 （8 bytes）

	public V5Packet(int packetSender, byte packetType, byte[] packetReserved, 
			int packetId,  int packetLength, int packetOffset, byte[] packetData, long crc) {
		
		this.packetSender = packetSender;
		this.packetType = packetType;
		this.packetReserved = packetReserved;
		this.packetId = packetId;
		this.packetLength = packetLength;
		this.packetOffset = packetOffset;
		this.packetData = packetData;
		this.crc = crc;
	}

	public int getPacketSender() {
		return packetSender;
	}
	
	public byte getPacketType() {
		return packetType;
	}

	public byte[] getPacketReserved() {
		return packetReserved;
	}

	public int getPacketId() {
		return packetId;
	}

	public int getPacketLength() {
		return packetLength;
	}

	public int getPacketOffset() {
		return packetOffset;
	}

	//获取数据
	public byte[] getPacketData() {
		return packetData;
	}



	public long getCrc() {
		return crc;
	}

	public boolean isPartPacket() {
		return packetLength != packetData.length;
	}

	public boolean isFirstPartPacket() {
		return packetOffset == 0;
	}

	public boolean isLastPartPacket() {
		return (packetOffset + packetData.length ) == packetLength;
	}

	//获取的数据为字符串
	public String getStringData() {
		return new String( packetData );
	}

	//获取数据的十六进制字符串
	public String getHexData() {
		return ByteUtil.asHex( packetData );
	}

	public String toString() {
		String str = "PKT type=" + packetType + ", id=" + packetId + ", packetLength=" + packetLength;
		str += ", CRC=" + crc + ", DATA=" + getStringData();
		return str;
	}

}