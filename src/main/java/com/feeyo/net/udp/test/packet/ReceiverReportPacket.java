package com.feeyo.net.udp.test.packet;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;

public class ReceiverReportPacket extends AbstractReportPacket {

    public ReceiverReportPacket() {
        super(Type.RECEIVER_REPORT);
    }

    public static ReceiverReportPacket decode(ChannelBuffer buffer, boolean hasPadding, byte innerBlocks, int length) {
        ReceiverReportPacket packet = new ReceiverReportPacket();

        packet.setSenderSsrc(buffer.readUnsignedInt());

        int read = 4;
        for (int i = 0; i < innerBlocks; i++) {
            packet.addReceptionReportBlock(ReceptionReport.decode(buffer));
            read += 24; // Each SR/RR block has 24 bytes (6 32bit words)
        }

        // Length is written in 32bit words, not octet count.
        int lengthInOctets = (length * 4);
        // (hasPadding == true) check is not done here. RFC respecting implementations will set the padding bit to 1
        // if length of packet is bigger than the necessary to convey the data; therefore it's a redundant check.
        if (read < lengthInOctets) {
            // Skip remaining bytes (used for padding).
            buffer.skipBytes(lengthInOctets - read);
        }

        return packet;
    }

    public static ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize, ReceiverReportPacket packet) {
        if ((currentCompoundLength < 0) || ((currentCompoundLength % 4) > 0)) {
            throw new IllegalArgumentException("Current compound length must be a non-negative multiple of 4");
        }
        if ((fixedBlockSize < 0) || ((fixedBlockSize % 4) > 0)) {
            throw new IllegalArgumentException("Padding modulus must be a non-negative multiple of 4");
        }

        // Common header + sender ssrc
        int size = 4 + 4;
        ChannelBuffer buffer;
        if (packet.receptionReports != null) {
            size += packet.receptionReports.size() * 24;
        }

        // If packet was configured to have padding, calculate padding and add it.
        int padding = 0;
        if (fixedBlockSize > 0) {
            // If padding modulus is > 0 then the padding is equal to:
            // (global size of the compound RTCP packet) mod (block size)
            // Block size alignment might be necessary for some encryption algorithms
            // RFC section 6.4.1
            padding = fixedBlockSize - ((size + currentCompoundLength) % fixedBlockSize);
            if (padding == fixedBlockSize) {
                padding = 0;
            }
        }
        size += padding;

        // Allocate the buffer and write contents
        buffer = ChannelBuffers.buffer(size);
        // First byte: Version (2b), Padding (1b), RR count (5b)
        byte b = packet.getVersion().getByte();
        if (padding > 0) {
            b |= 0x20;
        }
        b |= packet.getReceptionReportCount();
        buffer.writeByte(b);
        // Second byte: Packet Type
        buffer.writeByte(packet.type.getByte());
        // Third byte: total length of the packet, in multiples of 4 bytes (32bit words) - 1
        int sizeInOctets = (size / 4) - 1;
        buffer.writeShort(sizeInOctets);
        // Next 24 bytes: ssrc, ntp timestamp, rtp timestamp, octet count, packet count
        buffer.writeInt((int) packet.senderSsrc);
        // Payload: report blocks
        if (packet.getReceptionReportCount() > 0) {
            for (ReceptionReport block : packet.receptionReports) {
                buffer.writeBytes(block.encode());
            }
        }

        if (padding > 0) {
            // Final bytes: padding
            for (int i = 0; i < (padding - 1); i++) {
                buffer.writeByte(0x00);
            }

            // Final byte: the amount of padding bytes that should be discarded.
            // Unless something's wrong, it will be a multiple of 4.
            buffer.writeByte(padding);
        }

        return buffer;
    }

    @Override
    public ChannelBuffer encode(int currentCompoundLength, int fixedBlockSize) {
        return encode(currentCompoundLength, fixedBlockSize, this);
    }

    @Override
    public ChannelBuffer encode() {
        return encode(0, 0, this);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("ReceiverReportPacket{")
                .append("senderSsrc=").append(this.senderSsrc)
                .append(", receptionReports=").append(this.receptionReports)
                .append('}').toString();
    }
}
