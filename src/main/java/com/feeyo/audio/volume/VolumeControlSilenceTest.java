package com.feeyo.audio.volume;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class VolumeControlSilenceTest {
	
	public static final double DEFAULT_SILENCE_THRESHOLD  = -70D;

	public static void main(String[] args)  {
		
		int cc = 0;
		int cc1 = 0;
		
		FileInputStream fileIs = null;
		try {
			
			File file = new File( "/Users/zhuam/Downloads/20180525221955.wav" );  //20180525204847.wav     20180525221955.wav
			fileIs = new FileInputStream( file );

			byte[] pcmData = new byte[ (int)( file.length() - 44) ];
			
			fileIs.skip(44);
			fileIs.read(pcmData, 0, pcmData.length); 
            
            //
            int chunkSize = 2048;
            int offset = 0;
            int length = pcmData.length;
            int chunkCount = ( length / chunkSize)  + ( length % chunkSize > 0 ? 1 : 0);
            for(int i=0; i<chunkCount; i++) {
            	
            	byte[] chunkBuf = null;
				
				int residue = length - offset;
				if ( residue < chunkSize ) {
					chunkBuf = new byte[ residue ];
				} else {
					chunkBuf = new byte[ chunkSize ];
				}
				
				System.arraycopy(pcmData, offset, chunkBuf, 0, chunkBuf.length);
				offset += chunkBuf.length;
				
				short[] chunkShortArr = VolumeUtil.toShortArray( chunkBuf );
				
				// 
				boolean isSilence = VolumeUtil.getSoundPressureLevel( chunkShortArr ) < DEFAULT_SILENCE_THRESHOLD;
				if ( isSilence ) {
					cc++;
				}
            	
				//
				
				float[] chunkFloatArr = VolumeUtil.toFloatArray( chunkBuf );
				boolean isSilence1 = VolumeUtil.getSoundPressureLevel( chunkFloatArr ) < DEFAULT_SILENCE_THRESHOLD;
				if ( isSilence1 ) {
					cc1++;
				}
            	
            }
            
        	System.out.println("audio silence=" + cc + ", cc1=" + cc1);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fileIs != null) {
				try {
					fileIs.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
	}

}
