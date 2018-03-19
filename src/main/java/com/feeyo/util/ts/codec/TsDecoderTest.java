package com.feeyo.util.ts.codec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.feeyo.net.udp.test.TestDataUtil;

public class TsDecoderTest {
	
	public static void main(String[] args) {
		
		StringBuffer filePath = new StringBuffer();
		filePath.append( System.getProperty("user.dir") ).append( File.separator );
		filePath.append( "testdata" ).append( File.separator );
		
		
		
		byte[] data = TestDataUtil.getTsData();
		
		TsDecoder decoder = new TsDecoder();
		byte[][] rawData = decoder.decode(data);
		
		if(rawData != null) {
			FileOutputStream vfos = null;
			FileOutputStream afos = null;
			try {
				vfos = new FileOutputStream(filePath.toString() + "de_video.h264");
				afos = new FileOutputStream(filePath.toString() + "de_audio.aac");
				
				vfos.write(rawData[0]);
				afos.write(rawData[1]);
				
				vfos.close();
				afos.close();
				
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if(vfos != null)
						vfos.close();
					if(afos != null)
						afos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}