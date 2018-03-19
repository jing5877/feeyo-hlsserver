package com.feeyo.mpeg2ts.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.feeyo.mpeg2ts.TsReader;

public class TsReaderTest {
	
	private static int TS_PACKET_SIZE = 188;
	
	static byte[] getPatBuf() {
		
		byte[] buf = new byte[TS_PACKET_SIZE];
		
		// reset
		for(int i=0; i<TS_PACKET_SIZE; i++) {
			buf[i] = (byte) 0xff;
		}
		
		// PAT packet
		buf[0] = (byte)0x47;
		buf[1] = (byte)0x40;
		buf[2] = (byte)0x00;
		buf[3] = (byte)0x10;
		buf[4] = (byte)0x00;
		buf[5] = (byte)0x00;
		buf[6] = (byte) 0xb0;
		buf[7] = (byte)0x00;
		buf[8] = (byte)0x01;
		buf[9] = (byte) 0xc1;
		buf[10] = (byte)0x00;
		buf[11] = (byte)0x00;
		buf[12] = (byte)0x00;
		buf[13] = (byte)0x01;
		buf[14] = (byte) 0xf0;
		buf[15] = (byte)0x00;
		buf[16] = (byte)0x2a;
		buf[17] = (byte)0xb1;
		buf[18] = (byte)0xb2;
		
		return buf;
		
	}
	
	static byte[] getPmtBuf() {
		
		byte[] buf = new byte[TS_PACKET_SIZE];
		
		// reset
		for(int i=0; i<TS_PACKET_SIZE; i++) {
			buf[i] = (byte) 0xff;
		}
		
		// PMT packet
		buf[0] = (byte)0x47;
		buf[1] = (byte)0x50;
		buf[2] = (byte)0x00;
		buf[3] = (byte)0x10;
		buf[4] = (byte)0x00;
		buf[5] = (byte)0x02;
		buf[6] = (byte)0xb0;
		buf[7] = (byte)0x17;
		buf[8] = (byte)0x00;
		buf[9] = (byte)0x01;
		buf[10] = (byte)0xc1;
		buf[11] = (byte)0x00;
		buf[12] = (byte)0x00;
		buf[13] = (byte)0xe1;
		buf[14] = (byte)0x00;
		buf[15] = (byte)0xf0;
		buf[16] = (byte)0x00;
		buf[17] = (byte)0x1b;
		buf[18] = (byte)0xe1;
		buf[19] = (byte)0x00;
		buf[20] = (byte)0xf0;
		buf[21] = (byte)0x00;
		buf[22] = (byte)0x0f;
		buf[23] = (byte)0xe1;
		buf[24] = (byte)0x01;
		buf[25] = (byte)0xf0;
		buf[26] = (byte)0x00;
		buf[27] = (byte)0x2f;
		buf[28] = (byte)0x44;
		buf[29] = (byte)0xb9;
		buf[30] = (byte)0x9b;
		
		return buf;
		
	}
	
	static byte[] getAvBuf() {
		
		byte[] buf = new byte[] {
				(byte)0x47, (byte)0x41, (byte)0x01, (byte)0x30, (byte)0x0c, (byte)0x40, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, 
				(byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff, (byte)0x00, (byte)0x00, (byte)0x01, 
				(byte)0xc0, (byte)0x00, (byte)0xa5, (byte)0x80, (byte)0x80, (byte)0x05, (byte)0x21, (byte)0x00, (byte)0xcb, (byte)0xe9, 
				(byte)0xe5, (byte)0xff,
				
				(byte)0xf1, (byte)0x50, (byte)0x80, (byte)0x13, (byte)0xbf, (byte)0xfc, (byte)0x21, (byte)0x47, (byte)0xfe, (byte)0xff, 
				(byte)0x99, (byte)0x82, (byte)0xc5, (byte)0x11, (byte)0x21, (byte)0x2a, (byte)0xe8, (byte)0x7f, (byte)0x80, (byte)0x45, 
				(byte)0xc2, (byte)0x78, (byte)0x3b, (byte)0x82, (byte)0x83, (byte)0x2f, (byte)0xcf, (byte)0x09, (byte)0x27, (byte)0x60, 
				(byte)0x4f, (byte)0xaf,
				
				(byte)0xc9, (byte)0x5b, (byte)0xe7, (byte)0xf1, (byte)0x2f, (byte)0x52, (byte)0x7d, (byte)0x1b, (byte)0xc4, (byte)0x78, 
				(byte)0x0d, (byte)0x40, (byte)0x19, (byte)0xeb, (byte)0xaf, (byte)0xf8, (byte)0x1c, (byte)0xf0, (byte)0xab, (byte)0xd3, 
				(byte)0x90, (byte)0xeb, (byte)0x69, (byte)0x1f, (byte)0x6a, (byte)0x76, (byte)0x14, (byte)0xca, (byte)0xe6, (byte)0xa7, 
				(byte)0xc2, (byte)0x85,
				
				(byte)0x06, (byte)0x99, (byte)0x43, (byte)0x15, (byte)0x09, (byte)0xd4, (byte)0x15, (byte)0x12, (byte)0x90, (byte)0x65, 
				(byte)0xf1, (byte)0x20, (byte)0x20, (byte)0x20, (byte)0x08, (byte)0xf6, (byte)0x6a, (byte)0x51, (byte)0x09, (byte)0x65, 
				(byte)0xdd, (byte)0x3e, (byte)0xe1, (byte)0xb6, (byte)0x4d, (byte)0x64, (byte)0x63, (byte)0xdd, (byte)0xf7, (byte)0xfe, 
				(byte)0xda, (byte)0x9b,
				
				(byte)0x3a, (byte)0xb0, (byte)0x5a, (byte)0x33, (byte)0x93, (byte)0x86, (byte)0x9f, (byte)0x6c, (byte)0x79, (byte)0x32, 
				(byte)0xfa, (byte)0x07, (byte)0xd2, (byte)0x6f, (byte)0xbb, (byte)0x67, (byte)0x24, (byte)0xcb, (byte)0x79, (byte)0xca, 
				(byte)0x7b, (byte)0x11, (byte)0x11, (byte)0xa2, (byte)0x1a, (byte)0xf5, (byte)0xbd, (byte)0xc6, (byte)0x98, (byte)0xf1, 
				(byte)0x2b, (byte)0x83,
				
				(byte)0x85, (byte)0xce, (byte)0xcb, (byte)0xc0, (byte)0x02, (byte)0x8d, (byte)0xaf, (byte)0x52, (byte)0x91, (byte)0xdc, 
				(byte)0xad, (byte)0x08, (byte)0x98, (byte)0xda, (byte)0x1a, (byte)0xc7, (byte)0x8f, (byte)0x4d, (byte)0x8a, (byte)0xad, 
				(byte)0x62, (byte)0x57, (byte)0x18, (byte)0xa2, (byte)0x22, (byte)0x8a, (byte)0x20, (byte)0xe0
		};
		
		return buf;
		
	}
	
	static byte[] getFileAVBuf() {
		
		StringBuffer path = new StringBuffer();
		path.append( System.getProperty("user.dir") )
		.append( File.separator ).append( "testdata" )
		.append( File.separator ).append( "ec6d6ac2-283c-4f86-bbb3-ea4e03a214bf.ts" ); 
		//.append( File.separator ).append( "1000kbps-00001.ts" );
		//.append( File.separator ).append( "38.ts" );
		
		byte[] buf = null;
		try {
			buf = Files.readAllBytes( new File( path.toString()).toPath() );
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return buf;
	}
	
	public static void main(String[] args) {
		
		/*
		audio
		channelCount 1/2
		sampleRate 44100/22050/8000
		计算 BitRate 
		
		video
		width / height
		pixelType ? YUV420P 
		videoBitRate = 360000;
		 */
		
		
		/*
		-2
		10000010 -> 11111101
		
		11111111111111111111111111111110
		10000010
		*/
		
		
		byte bb = -2;
		System.out.println(  Integer.toBinaryString(bb)  );
		System.out.println(  Integer.toBinaryString(bb & 0xff) );
	
		
		TsReader  tsReader = new TsReader();
		
		//tsReader.parseTsPacket( getPatBuf() );
		//tsReader.parseTsPacket( getPmtBuf() );
		//tsReader.parseTsPacket( getAvBuf() );
		
		tsReader.parseTsPacket( getFileAVBuf() );
		
		
	}

}
