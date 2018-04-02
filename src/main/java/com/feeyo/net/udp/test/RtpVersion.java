package com.feeyo.net.udp.test;

public enum RtpVersion {

    V2((byte) 0x80),
    V1((byte) 0x40),
    V0((byte) 0x00);

   
    private final byte b;
 
    private RtpVersion(byte b) {
        this.b = b;
    }

    public static RtpVersion fromByte(byte b) throws IllegalArgumentException {
        byte tmp = (byte) (b & 0xc0);
        // Starts from version 2, which is the most common.
        for (RtpVersion version : values()) {
            if (version.getByte() == tmp) {
                return version;
            }
        }
        throw new IllegalArgumentException("Unknown version for byte: " + b);
    }
 
    public byte getByte() {
        return b;
    }
}
