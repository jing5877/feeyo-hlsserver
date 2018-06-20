package com.feeyo.test.mpeg2ts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

import com.feeyo.mpeg2ts.Pes;
import com.feeyo.mpeg2ts.Ts;
import com.feeyo.mpeg2ts.TsReader;
import com.feeyo.mpeg2ts.TsWriter;
import com.feeyo.mpeg2ts.TsWriter.FrameData;
import com.feeyo.mpeg2ts.TsWriter.FrameDataType;
import com.feeyo.mpeg2ts.util.TsUtil;

public class TsWriterTest {
	
	private static byte[] readTsFile(String pathname) {
		byte[] buf = null;
		try {
			buf = Files.readAllBytes( new File( pathname ).toPath() );
		} catch (IOException e) {
			e.printStackTrace();
		}
		return buf;
	}
	
	private static void writeTsFile(String pathname, byte[] buf) {

		FileOutputStream fos = null;
		try {

			File myFile = new File( pathname );
			if (!myFile.exists()) {
				myFile.createNewFile();
			}
			
			fos = new FileOutputStream(myFile);
			fos.write(buf);
			fos.flush();
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fos != null)
					fos.close();
			} catch (Exception ex) {
			}
		}
	}
	
	
	private static byte[] append(byte[] oldBuffer, byte[] newBuffer, int offset, int length) {
		if (newBuffer == null) {
			return oldBuffer;
		}
		
		if (newBuffer.length != length) {
			byte[] buf = new byte[ length ];
			System.arraycopy(newBuffer, offset, buf, 0, length);
			newBuffer = buf;
		}
		
		if (oldBuffer == null) {
			oldBuffer = newBuffer;
			return oldBuffer;
		}
		oldBuffer = TsUtil.margeByteArray(oldBuffer, newBuffer);
		return oldBuffer;
	}
	
	public static void main(String[] args) {
		
		byte[] buf1 = readTsFile( "/Users/zhuam/git/variflight/feeyo-hlsserver/testdata/ec6d6ac2-283c-4f86-bbb3-ea4e03a214bf.ts" );
	
		//
		TsReader tsReader = new TsReader();
		
		
		int frameLength = 0;
		int audioFrameLength = 0;
		int videoFrameLength = 0;
		
		Ts[] tsPackets = tsReader.parseTsPacket( buf1 );
		for(int i=0; i<tsPackets.length; i++) {
			Ts ts = tsPackets[i];
			if ( ts instanceof Pes ) {
				Pes pes = (Pes)ts;
				
				if( pes.payload_unit_start_indicator == 1 ) {
					frameLength++;
					
					if ( pes.stream_id == Ts.AUDIO_STREAM_ID ) {
						audioFrameLength++;
					} else {
						videoFrameLength++;
					}
				}
			}
		}
		
		
		//
		FrameData[] frames = new FrameData[ frameLength ];
		int frameIdx = 0;
		int currentFrameIdx = 0;
		
		for(int i=0; i<tsPackets.length; i++) {
			Ts ts = tsPackets[i];
			if ( ts instanceof Pes ) {
				Pes pes = (Pes)ts;
				
				if( pes.payload_unit_start_indicator == 1 ) {

					currentFrameIdx = frameIdx;
					frames[ currentFrameIdx ] = new FrameData();

					frames[ currentFrameIdx ].buf = append( frames[ currentFrameIdx ].buf, pes.es_data, 0, pes.es_data.length);
					frames[ currentFrameIdx ].dts =  pes.dts;
					frames[ currentFrameIdx ].pts = pes.pts;
					frames[ currentFrameIdx ].isAudio = (pes.stream_id == Ts.AUDIO_STREAM_ID);
					
					
					frameIdx++;
					
					
				} else {
					frames[ currentFrameIdx ].buf= append( frames[ currentFrameIdx ].buf, pes.es_data, 0, pes.es_data.length);
					
				}
			}
		}
		
		TsWriter tsWriter1 = new TsWriter();
		byte[] tsFileBuf = tsWriter1.write(true, FrameDataType.MIXED, frames );
		
		//System.out.println( bytesToHexString( tsFileBuf, 0, tsFileBuf.length));
		
		// mix
		writeTsFile("/Users/zhuam/git/variflight/feeyo-hlsserver/testdata/test1.ts", tsFileBuf);
		

		int audioFrameIndex = 0;
		FrameData[] audioFrames = new FrameData[ audioFrameLength ];
		
		int videoFrameIndex = 0;
		FrameData[] videoFrames = new FrameData[ videoFrameLength ];

		
		for(int i = 0; i < frames.length; i++) {
			FrameData frame = frames[i];
			if ( frame.isAudio ) {
				audioFrames[audioFrameIndex] = frame;
				audioFrameIndex++;
			} else {
				videoFrames[videoFrameIndex] = frame;
				videoFrameIndex++;
			}
		}
		
		
		TsWriter tsWriter2= new TsWriter();
		

		byte[] tsFileBuf2 = tsWriter2.write(true, FrameDataType.AUDIO, audioFrames );
		writeTsFile("/Users/zhuam/git/variflight/feeyo-hlsserver/testdata/test2.ts", tsFileBuf2);
		
		TsWriter tsWriter3 = new TsWriter();
		byte[] tsFileBuf3 = tsWriter3.write(true, FrameDataType.VIDEO, videoFrames );
		writeTsFile("/Users/zhuam/git/variflight/feeyo-hlsserver/testdata/test3.ts", tsFileBuf3);

		
		
	}

}
