package com.feeyo.audio.volume;


/**
 * 
 * @see https://github.com/JorenSix/TarsosDSP/blob/master/src/core/be/tarsos/dsp/io/TarsosDSPAudioFloatConverter.java
 */
public class AudioFloatConverter {
	
	 // PCM 16 bit, signed, little-endian
	  public float[] toFloatArray(byte[] in_buff, int in_offset,
              float[] out_buff, int out_offset, int out_len) {
          int ix = in_offset;
          int len = out_offset + out_len;
          for (int ox = out_offset; ox < len; ox++) {
              out_buff[ox] = ((short) ((in_buff[ix++] & 0xFF) | 
                         (in_buff[ix++] << 8))) * (1.0f / 32767.0f);
          }

          return out_buff;
      }

      public byte[] toByteArray(float[] in_buff, int in_offset, int in_len,
              byte[] out_buff, int out_offset) {
          int ox = out_offset;
          int len = in_offset + in_len;
          for (int ix = in_offset; ix < len; ix++) {
              int x = (int) (in_buff[ix] * 32767.0);
              out_buff[ox++] = (byte) x;
              out_buff[ox++] = (byte) (x >>> 8);
          }
          return out_buff;
      }
	
      
  
}
