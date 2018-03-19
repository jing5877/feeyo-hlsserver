package com.feeyo.audio.codec;

public class PcmaEncoder extends Encoder {

	private final static int cClip = 32635;
	
	private static byte aLawCompressTable[] = new byte[]{
	        1, 1, 2, 2, 3, 3, 3, 3,
	        4, 4, 4, 4, 4, 4, 4, 4,
	        5, 5, 5, 5, 5, 5, 5, 5,
	        5, 5, 5, 5, 5, 5, 5, 5,
	        6, 6, 6, 6, 6, 6, 6, 6,
	        6, 6, 6, 6, 6, 6, 6, 6,
	        6, 6, 6, 6, 6, 6, 6, 6,
	        6, 6, 6, 6, 6, 6, 6, 6,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7,
	        7, 7, 7, 7, 7, 7, 7, 7
	    };

	public PcmaEncoder() {
		super();
	}

	@Override
	public byte[] process(byte[] media) {
		byte[] compressed = new byte[media.length / 2];

		int j = 0;
		for (int i = 0; i < compressed.length; i++) {
			short sample = (short) (((media[j++] & 0xff) | (media[j++]) << 8));
			compressed[i] = linearToALawSample(sample);
		}
		return compressed;
	}

	/**
	 * Compress 16bit value to 8bit value
	 * 
	 * @param sample
	 *            16-bit sample
	 * @return compressed 8-bit value.
	 */
	private byte linearToALawSample(short sample) {
		int sign;
		int exponent;
		int mantissa;
		int s;

		sign = ((~sample) >> 8) & 0x80;
		if (!(sign == 0x80)) {
			sample = (short) -sample;
		}
		if (sample > cClip) {
			sample = cClip;
		}
		if (sample >= 256) {
			exponent = (int) aLawCompressTable[(sample >> 8) & 0x7F];
			mantissa = (sample >> (exponent + 3)) & 0x0F;
			s = (exponent << 4) | mantissa;
		} else {
			s = sample >> 4;
		}
		s ^= (sign ^ 0x55);
		return (byte) s;
	}
}