package com.feeyo.audio.transcoding;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.audio.codec.faac.AacEncodeException;

/**
 * Faac encoder pool
 *
 * @author Tr!bf wangyamin@variflight.com
 *
 * a temporary method to make the faac encoder memory usage stable
 */
public class AacEncoderPool {
	
	private static Logger LOGGER = LoggerFactory.getLogger( AacEncoderPool.class );
	
    private final ConcurrentLinkedQueue<AacEncoder> encoders = new ConcurrentLinkedQueue<>();
    private final AtomicInteger encoderNum = new AtomicInteger(0);

    public AacEncoder borrow(float sampleRate, int sampleSizeInBits, int channels, boolean createWhenEmpty) {
    	
    	LOGGER.info("AacEncoderPool current size: "+ encoders.size()+" / " + encoderNum.get());
        
    	AacEncoder encoder = encoders.poll();
    	if ( encoder == null && createWhenEmpty ) {
			encoderNum.getAndIncrement();
			return new AacEncoder((int) sampleRate, channels, sampleSizeInBits);
    	}
    	return encoder;
    }

    public void recycle(AacEncoder aacEncoder) {
        try {
        	aacEncoder.fadeOut();
        } catch (AacEncodeException e) {
        	LOGGER.warn("failed to recycle faac encoder: "+e.getError() );
        	aacEncoder.close();
            encoderNum.getAndDecrement();
        }
        encoders.offer(aacEncoder);
    }

    @Override
    public String toString() {
        return "AacEncoderPool: " + encoders.size() + " / " + encoderNum.get();
    }
}
