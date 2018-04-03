package com.feeyo.net.udp.test.packet;

import org.jboss.netty.buffer.ChannelBuffer;

public abstract class ControlPacket extends Packet {

    protected RtpVersion version;
    protected Type type;

    protected ControlPacket(Type type) {
        this.version = RtpVersion.V2;
        this.type = type;
    }

    public static ControlPacket decode(ChannelBuffer buffer) {
    	
    	//System.out.println( ByteUtil.convertToHex( buffer.array() ) );
    	
        if ((buffer.readableBytes() % 4) > 0) {
            throw new IllegalArgumentException("Invalid RTCP packet length: expecting multiple of 4 and got " + 
            									buffer.readableBytes());
        }
        byte b = buffer.readByte();
        RtpVersion version = RtpVersion.fromByte(b);
        if (!version.equals(RtpVersion.V2)) {
            return null;
        }
        
        boolean hasPadding = (b & 0x20) > 0; // mask 0010 0000
        byte innerBlocks = (byte) (b & 0x1f); // mask 0001 1111

        ControlPacket.Type type = ControlPacket.Type.fromByte(buffer.readByte());

        // This length is in 32bit (4byte) words. These first 4 bytes already read don't count.
        int length = buffer.readShort();
        if (length == 0) {
            return null;
        }

        // No need to pass version downwards, only V2 is supported so subclasses can safely assume V2.
        // I know it's ugly when the superclass knows about the subclasses but since this method is static (and NEEDS
        // to be) the alternative was having this method in a external class. Pointless. 
        switch (type) {
            case RECEIVER_REPORT:
                return ReceiverReportPacket.decode(buffer, hasPadding, innerBlocks, length);
            case APP_DATA:
                return null;
            default:
                throw new IllegalArgumentException("Unknown RTCP packet type: " + type);
        }
    }

    public abstract ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize);

    public abstract ChannelBuffer encode();

    public RtpVersion getVersion() {
        return version;
    }

    public void setVersion(RtpVersion version) {
        if (version != RtpVersion.V2) {
            throw new IllegalArgumentException("Only V2 is supported");
        }
        this.version = version;
    }

    public Type getType() {
        return type;
    }

    public static enum Type {
        SENDER_REPORT((byte) 0xc8),
        RECEIVER_REPORT((byte) 0xc9),
        SOURCE_DESCRIPTION((byte) 0xca),
        BYE((byte) 0xcb),
        APP_DATA((byte) 0xcc);

        private byte b;

        Type(byte b) {
            this.b = b;
        }
        
        public static Type fromByte(byte b) {
            switch (b) {
                case (byte) 0xc8:
                    return SENDER_REPORT;
                case (byte) 0xc9:
                    return RECEIVER_REPORT;
                case (byte) 0xca:
                    return SOURCE_DESCRIPTION;
                case (byte) 0xcb:
                    return BYE;
                case (byte) 0xcc:
                    return APP_DATA;
                default:
                    throw new IllegalArgumentException("Unknown RTCP packet type: " + b);
            }
        }

        public byte getByte() {
            return this.b;
        }
    }
}
