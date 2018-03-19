package com.feeyo.audio.codec.faac;

import sun.nio.ch.DirectBuffer;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/**
 * FaacEncoder only support the WAV S16LE format
 * @author tribf wangyamin@variflight.com
 *
 * 1. new FaacEncoder(long sampleRate, int numChannels, int bitsPerSample);
 * 2. inBufferSize = getInBufferSize(); inBufferSize indicates the input buffer size for one faac frame
 * 3. aacEncode(byte[] pcmData, int len); len sould equal to inBufferSize on last step
 * 4. in the end invoke closeAacEncoder(); close the faac encoder and release the jni malloc memory
 *
 * for no streaming scenario, it's suggested to use: public byte[] aacEncode (byte[] rawBuf), which handles S16LE raw
 * buffer and return the faac data with ADTS package
 */
public class FaacEncoder {
    private native long setupAacEncoder(long sampleRate, int numChannels, int bitsPerSample);
    protected native int faacEncEncode(long faacEncAddr, byte[] pcmData, ByteBuffer directBuffer);
    private native int getMaxOutputBytes(long faacEncAddr);
    private native int getInputSamplesPerFrame(long faacEncAddr);
    private native int getInputBufferSize(long faacEncAddr);
    private native void closeAacEncoder(long faacEncAddr);

    protected long jni_encoder_addr = 0;                  // used by jni, store the faac encoder struct's address
    protected ByteBuffer directBuffer;
    private int inBufferSize = 0;
    private int maxOutBufferSize = 0;
    private long sampleRate;
    private int numChannels;
    private int bitsPerSample;

    static {
		String osName = System.getProperties().getProperty("os.name");
		if (osName.indexOf("Window") >= 0 ) {
			System.load(System.getProperty("log4jHome") + File.separator + "lib" + File.separator + "jni"
					+ File.separator + "windows" + File.separator + "faacEncoder.dll");
			
		} else if (osName.indexOf("Mac") >= 0) {
			System.load(System.getProperty("log4jHome") + File.separator + "lib" + File.separator + "jni"
					+ File.separator + "mac" + File.separator + "libfaacEncoder.dylib");
			
		} else {
			System.load(System.getProperty("log4jHome") + File.separator + "lib" + File.separator + "jni"
					+ File.separator + "linux" + File.separator + "libfaacEncoder.so");
		}
    }

    public FaacEncoder(long sampleRate, int numChannels, int bitsPerSample) {
        this.sampleRate = sampleRate;
        this.numChannels = numChannels;
        this.bitsPerSample = bitsPerSample;

        jni_encoder_addr = setupAacEncoder(sampleRate, numChannels, bitsPerSample);
        inBufferSize = getInputBufferSize(jni_encoder_addr);
        maxOutBufferSize = getMaxOutputBytes(jni_encoder_addr);

        directBuffer = ByteBuffer.allocateDirect(maxOutBufferSize);
        directBuffer.order(ByteOrder.nativeOrder());
    }

    public void reset() {
        closeAacEncoder(jni_encoder_addr);
        jni_encoder_addr = setupAacEncoder(sampleRate, numChannels, bitsPerSample);
        inBufferSize = getInputBufferSize(jni_encoder_addr);
        maxOutBufferSize = getMaxOutputBytes(jni_encoder_addr);

        if (directBuffer == null) {
            directBuffer = ByteBuffer.allocateDirect(maxOutBufferSize);
            directBuffer.order(ByteOrder.nativeOrder());
        }
    }

    public int getInBufferSize() {
        return inBufferSize;
    }

    public long getJni_encoder_addr() {
        return jni_encoder_addr;
    }

    public int getMaxOutputBytes() {
        return maxOutBufferSize;
    }

    public byte[] encode(byte[] pcmBuf, int offset, int len) throws AacEncodeException {
        len = len > 0 ? len : 0;

        byte[] pcmData = new byte[len];
        System.arraycopy(pcmBuf,offset,pcmData,0,len);
        return encode(pcmData);
    }

    public byte[] encode(byte[] pcmBuf, int len) throws AacEncodeException {
        len = len > 0 ? len : 0;

        byte[] pcmData = new byte[len];
        System.arraycopy(pcmBuf,0,pcmData,0,len);
        return encode(pcmData);
    }

    public synchronized byte[] encode(byte[] pcmFrameData) throws AacEncodeException {
        int ret = faacEncEncode(jni_encoder_addr, pcmFrameData, directBuffer);
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

    public synchronized void close() {
        closeAacEncoder(jni_encoder_addr);
        jni_encoder_addr = 0;

        if (directBuffer != null) {
            if (directBuffer.isDirect())
                ((DirectBuffer) directBuffer).cleaner().clean();
            directBuffer = null;
        }
    }
}
