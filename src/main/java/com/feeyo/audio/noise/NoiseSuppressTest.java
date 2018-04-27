package com.feeyo.audio.noise;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

public class NoiseSuppressTest {
	
	public static void main(String[] args) {
		
		NoiseSuppress ns = new NoiseSuppress(8000, 1024);
		
		String filePath = "F:\\test\\1.pcm";

		byte[] fileBuff = null;
		try {
			fileBuff = Files.readAllBytes(new File(filePath.toString()).toPath());
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		for (int i = 0; i < fileBuff.length - 2048; i += 2048) {
			
			byte[] buff11 = new byte[2048];
			System.arraycopy(fileBuff, i, buff11, 0, 2048);
			buff11 = ns.noiseReductionProcess(buff11);
			write(buff11);
		}
		
	}
	
	private static void write(byte[] content) {
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream("F:\\test\\1_a2.pcm", true);
			fos.write(content);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if(fos != null)
					fos.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}
