package com.feeyo.audio.codec.faac;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Utils of faac encoder
 *
 * @author Tr!bf wangyamin@variflight.com
 */

@SuppressWarnings("unused")
public class FaacUtils {
    public static final byte[] ZERO_PCM_DATA = new byte[2048];
    /**
     * the native aacEncode jni method wrappper
     *
     * @param pcmData in S16LE format
     * @return encoded aac byte[] like {[ADTS][AU]} {[ADTS][AU]} ... {[ADTS][AU]}
     */
    public static byte[] encodePcmToAAC (FaacEncoder encoder, byte[] pcmData) throws AacEncodeException {
        if (pcmData == null || pcmData.length == 0)
            return new byte[0];

        ByteBuffer byteBuffer = ByteBuffer.wrap(pcmData);
        byte[] pcmFrameBuf = new byte[encoder.getInBufferSize()];

        byte[] aacFrameData = new byte[0];
        byte[] aacRetData = null;

        int aacPutFrames = 0;
        int aacPutZeroFrames = 0;
        int aacGetFrames = 0;

        while (byteBuffer.hasRemaining() || aacFrameData.length > 0 || (aacPutFrames - aacPutZeroFrames - aacGetFrames) > 0) {
            aacPutFrames ++;
            int rest = byteBuffer.remaining();
            if (rest >= pcmFrameBuf.length) {
                byteBuffer.get(pcmFrameBuf);
                aacFrameData = encoder.encode(pcmFrameBuf, pcmFrameBuf.length);
            } else if (rest > 0) {
                byteBuffer.get(pcmFrameBuf,0,byteBuffer.remaining());
                aacFrameData = encoder.encode(pcmFrameBuf, rest);
            } else {
                aacPutZeroFrames++;
                aacFrameData = encoder.encode(pcmFrameBuf, 0);
            }

            if (aacFrameData.length > 0) {
                aacGetFrames ++;

                if (aacRetData == null) {
                    aacRetData = new byte[aacFrameData.length];
                    System.arraycopy(aacFrameData, 0, aacRetData, 0, aacFrameData.length);
                } else {
                    byte[] tmp = new byte[aacRetData.length+aacFrameData.length];
                    System.arraycopy(aacRetData, 0, tmp, 0, aacRetData.length);
                    System.arraycopy(aacFrameData, 0, tmp, aacRetData.length, aacFrameData.length);
                    aacRetData = tmp;
                }

                if (aacPutFrames - aacPutZeroFrames == aacGetFrames)
                    break;
            }
        }

        return aacRetData;
    }

    /**
     * @link https://wiki.multimedia.cx/index.php/ADTS
     *
     * AAAAAAAA AAAABCCD EEFFFFGH HHIJKLMM MMMMMMMM MMMOOOOO OOOOOOPP (QQQQQQQQ QQQQQQQQ)
     *
     * Header consists of 7 or 9 bytes (without or with CRC).
     *
     * Letter  Length (bits)   Description
     * A  12  syncword 0xFFF, all bits must be 1
     * B  1   MPEG Version: 0 for MPEG-4, 1 for MPEG-2
     * C  2   Layer: always 0
     * D  1   protection absent, Warning, set to 1 if there is no CRC and 0 if there is CRC
     * E  2   profile, the MPEG-4 Audio Object Type minus 1
     * F  4   MPEG-4 Sampling Frequency Index (15 is forbidden)
     * G  1   private bit, guaranteed never to be used by MPEG, set to 0 when encoding, ignore when decoding
     * H  3   MPEG-4 Channel Configuration (in the case of 0, the channel configuration is sent via an inband PCE)
     * I  1   originality, set to 0 when encoding, ignore when decoding
     * J  1   home, set to 0 when encoding, ignore when decoding
     * K  1   copyrighted id bit, the next bit of a centrally registered copyright identifier, set to 0 when encoding, ignore when decoding
     * L  1   copyright id start, signals that this frame's copyright id bit is the first bit of the copyright id, set to 0 when encoding, ignore when decoding
     * M  13  frame length, this value must include 7 or 9 bytes of header length: FrameLength = (ProtectionAbsent == 1 ? 7 : 9) + size(AACFrame)
     * O  11  Buffer fullness
     * P  2   Number of AAC frames (RDBs) in ADTS frame minus 1, for maximum compatibility always use 1 AAC frame per ADTS frame
     * Q  16  CRC if protection absent is 0
     */
    public static class AdtsHeaderInfo {
        public AdtsHeaderInfo parseHeader(byte[] aacData, int offset) {
            if (aacData == null || (aacData.length - offset) < 7)
                return this;

            if (aacData[0] == (byte) 0xff && (aacData[1] & 0xf0) == 0xf0) {
                id = (aacData[offset+1] & 0x08) >> 7;
                layer = (aacData[offset+1] & 0x06) >> 1;
                no_crc = aacData[offset+1] & 0x01;
                profile = (aacData[offset+2] & 0xc0) >> 6;                 // 0:Main profile 1:LC 2:SSR
                freq_idx = (aacData[offset+2] & 0x3c) >> 2;                // 11:8000Hz
                private_bit = (aacData[offset+2] & 0x02) >> 1;
                channel_conf = (aacData[offset+2] & 0x01) << 2;            // 1:front-center
                channel_conf |= (aacData[offset+3] & 0xc0) >> 6;

                original_copy = (aacData[offset+3] & 0x20) >> 5;
                home = (aacData[offset+3] & 0x10) >> 4;

                copyright_id = (aacData[offset+3] & 0x08) >> 3;
                copyright_id_start = (aacData[offset+3] & 0x04) >> 2;

                length = (aacData[offset+3] & 0x03) << 11;
                length |= aacData[offset+4] << 3;
                length |= (aacData[offset+5] & 0xe0) >> 5;

                bf = (aacData[offset+5] & 0x1f) << 6;
                bf |= (aacData[offset+6] & 0xfc) >> 2;                      // 0x7FF 说明是码率可变的码流。
                nb_blocks = (aacData[offset+6] & 0x03);

                integrity = true;
            }
            return this;
        }
        public int getId() {
            return id;
        }

        public int getProfile() {
            return profile;
        }

        public int getFreq_idx() {
            return freq_idx;
        }

        public int getChannel_conf() {
            return channel_conf;
        }

        public int getLength() {
            return length;
        }

		private boolean integrity = false;
        private int id = -1;
        private int layer = -1;
        private int no_crc = -1;
        private int profile = -1;
        private int freq_idx = -1;
        private int private_bit = -1;
        private int channel_conf = -1;
        private int original_copy = -1;
        private int home = -1;
        private int copyright_id = -1;
        private int copyright_id_start = -1;
        private int length = 0;
        private int bf = -1;
        private int nb_blocks = -1;
    }

    public static ArrayList<byte[]> getAdtsFrames(byte[] aacBuf) {
        int size = 0;
        int curPtr = 0;
        ArrayList<byte[]> ret = new ArrayList<>();

        while(true)
        {
            if((aacBuf.length - curPtr) < 7 )
            {
                return ret;
            }

            size = new AdtsHeaderInfo().parseHeader(aacBuf, curPtr).getLength();
            if (size > 0)
            {
                byte[] adtsFrame = new byte[size];
                System.arraycopy(aacBuf,curPtr,adtsFrame,0,size);
                curPtr += size;
                ret.add(adtsFrame);
            } else {
                return ret;
            }
        }
    }
}
