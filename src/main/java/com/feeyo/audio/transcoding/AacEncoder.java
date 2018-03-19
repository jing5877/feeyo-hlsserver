package com.feeyo.audio.transcoding;

import com.feeyo.audio.codec.faac.AacEncodeException;
import com.feeyo.audio.codec.faac.AacError;
import com.feeyo.audio.codec.faac.FaacEncoder;
import com.feeyo.audio.codec.faac.FaacUtils;

/**
 * hls faac encoder, which cache input data for a complete frame
 *
 * @author Tr!bf wangyamin@variflight.com
 */
public class AacEncoder extends FaacEncoder {
    private byte[] inBuffer;                            // pcm frame buffer, which is 1 frame audio data for faac encoder
    private int inBufferPtr = 0;                        // pcmFrameBuf write pointer


    public AacEncoder(long sampleRate, int numChannels, int bitsPerSample) {
        super(sampleRate, numChannels, bitsPerSample);
        inBuffer = new byte[getInBufferSize()];
    }

    public synchronized int encodeZeroFrame() throws AacEncodeException {
        return faacEncEncode(jni_encoder_addr, FaacUtils.ZERO_PCM_DATA, directBuffer);
    }

    @Override
    public synchronized byte[] encode(byte[] pcmData) throws AacEncodeException {
        if (pcmData.length > getInBufferSize()) {
            throw new AacEncodeException(AacError.INPUT_DATA_TOO_LARGE);
        }

        int ret;
        int requiredLen = getInBufferSize() - inBufferPtr;
        int reset = pcmData.length - requiredLen;

        if (reset >= 0) {
            System.arraycopy(pcmData, 0, inBuffer, inBufferPtr, requiredLen);

            ret = faacEncEncode(jni_encoder_addr, pcmData, directBuffer);

            if (reset > 0)
                System.arraycopy(pcmData, requiredLen, inBuffer, 0, reset);

            inBufferPtr = reset;
        } else {
            System.arraycopy(pcmData, 0, inBuffer, inBufferPtr, pcmData.length);
            inBufferPtr += pcmData.length;
            ret = 0;
        }

        if (ret < 0) {
            throw new AacEncodeException(ret);
        }

        if (ret == 0)
            return new byte[0];

        byte[] data = new byte[ret];
        directBuffer.rewind();
        directBuffer.get(data);
        return data;
    }

    public void fadeOut() throws AacEncodeException {
        for (int i=0; i<10; i++) {
            int ret = encodeZeroFrame();
            if (ret < 50)
                break;
        }
    }
}
