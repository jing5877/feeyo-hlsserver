package com.feeyo.hls.ts.segmenter;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.audio.codec.faac.AacEncodeException;
import com.feeyo.audio.codec.faac.AacError;
import com.feeyo.audio.codec.faac.FaacUtils;
import com.feeyo.audio.transcoding.AacEncoder;
import com.feeyo.audio.transcoding.AacEncoderPool;
import com.feeyo.net.udp.packet.V5PacketType;

/**
 * aac transcoding
 * 
 * pcm to aac
 * 
 */
public class AacTranscodingTsSegmenter extends AacTsSegmenter {
	
	private static Logger LOGGER = LoggerFactory.getLogger( AacTranscodingTsSegmenter.class );
	
    private static AacEncoderPool aacEncoderPool = new AacEncoderPool();
    private AacEncoder aacEncoder;
    
    public AacTranscodingTsSegmenter() {
    	
    	super();

        aacEncoder = aacEncoderPool.borrow(this.sampleRate, this.sampleSizeInBits, this.channels, true);
        
        int maxAacBufLen = TS_PES_AU_NUM * aacEncoder.getMaxOutputBytes();
        aacBuf = new byte[maxAacBufLen];

        prepare4nextTs();
        
        
        // process the cached packets, need tens of milliseconds and the interval between rtp packet is 128ms
        for (int i=0; i<8; i++){
            getTsBuf(V5PacketType.PCM_STREAM, FaacUtils.ZERO_PCM_DATA, null);
        }
    }

	public void prepare4nextTs() {
		aacBufPtr = 0;
		tsSecsPtr = 0;
		aacFrameCounter = 0;
		tsSegmentLen = 0;
		tsWriter.reset();

		for (int i = 0; i < tsSecs.length; i++) {
			tsSecs[i] = null;
		}
	}

	public byte[][] getAdTsBuf(byte[] rawData) {
		
		int rawDataLen = rawData.length;
		int rawDataPtr = 0;
		int tsNum = calcTsNum(rawDataLen);
		byte[][] tsSegments = new byte[tsNum][];

		byte[] frameBuf = new byte[2048];
		for (int i = 0; i < tsNum;) {
			if (rawDataPtr + frameBuf.length < rawDataLen) {
				System.arraycopy(rawData, rawDataPtr, frameBuf, 0, frameBuf.length);
				rawDataPtr += frameBuf.length;
			} else if (rawDataPtr < rawDataLen) {
				Arrays.fill(frameBuf, (byte) 0);
				System.arraycopy(rawData, rawDataPtr, frameBuf, 0, rawDataLen - rawDataPtr);
				rawDataPtr += frameBuf.length;
			} else {
				frameBuf = FaacUtils.ZERO_PCM_DATA;
			}

			byte[] tsSegment = getTsBuf(V5PacketType.PCM_STREAM, frameBuf, null);
			if (tsSegment != null) {
				tsSegments[i++] = tsSegment;
			}
		}


        try {
            aacEncoder.fadeOut();
        } catch (AacEncodeException e) {
            aacEncoder.close();
            aacEncoder = aacEncoderPool.borrow(this.sampleRate, this.sampleSizeInBits, this.channels, true);
            LOGGER.warn("failed when fade out the ads's aac encoder: ", e.getError());
        }

        return tsSegments;
    }

	@Override
	public byte[] transcoding(byte rawDataType, byte[] rawData) {
		
		byte[] aacFrameData = null;
        try {
            aacFrameData = aacEncoder.encode(rawData);
        } catch (AacEncodeException e) {
            if (    e.getError() == AacError.JNI_ENCODE_ERROR ||
                    e.getError() == AacError.JNI_INVALIDE_ADDRESS ||
                    e.getError() == AacError.JNI_ENCODER_BROKEN ) {

                aacEncoder.close();
                aacEncoder = aacEncoderPool.borrow(this.sampleRate, this.sampleSizeInBits, this.channels, true);
            }
            LOGGER.warn("Failed when processing faac encode: ", e.getError());
        }
        return aacFrameData;
	}

    @Override
 	public void close() {
         if (aacEncoder != null) {
             aacEncoderPool.recycle(aacEncoder);
             aacEncoder = null;
         }
         super.close();
     }
}