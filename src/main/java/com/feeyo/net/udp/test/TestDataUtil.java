package com.feeyo.net.udp.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class TestDataUtil {
	
	public static byte[] getVideoData() {
		
		StringBuffer filePath = new StringBuffer();
		filePath.append( System.getProperty("user.dir") ).append( File.separator );
		filePath.append( "testdata" ).append( File.separator );
		filePath.append( "de_video1.h264" ); // video.h264     test.h264  video_old.h264
		
		byte[] data = null;
		try {
			data = Files.readAllBytes(new File(filePath.toString()).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public static byte[] getAudioData() {
		
		StringBuffer filePath = new StringBuffer();
		filePath.append( System.getProperty("user.dir") ).append( File.separator );
		filePath.append( "testdata" ).append( File.separator );
		filePath.append( "de_audio1.aac" );
		
		byte[] data = null;
		try {
			data = Files.readAllBytes(new File(filePath.toString()).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public static byte[] getTsData() {
		
		StringBuffer filePath = new StringBuffer();
		filePath.append( System.getProperty("user.dir") ).append( File.separator );
		filePath.append( "testdata" ).append( File.separator );
		filePath.append( "mux.ts" );
		
		
		byte[] data = null;
		try {
			data = Files.readAllBytes(new File(filePath.toString()).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}

}
