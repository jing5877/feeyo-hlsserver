package com.feeyo.hls.ts.segmenter;

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
        
        prepare4NextTs();
        
        
        // process the cached packets, need tens of milliseconds and the interval between rtp packet is 128ms
        for (int i=0; i<8; i++){
            getTsBuf(V5PacketType.PCM_STREAM, FaacUtils.ZERO_PCM_DATA, null);
        }
    }

	public void prepare4NextTs() {

		tsWriter.reset();

		aacBufPtr = 0;
		for (int i = 0; i < aacBufArr.length; i++) {
			aacBufArr[i] = null;
		}
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