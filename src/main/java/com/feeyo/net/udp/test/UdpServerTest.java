package com.feeyo.net.udp.test;

import java.io.File;

import com.feeyo.HlsCtx;
import com.feeyo.HlsServer;
import com.feeyo.hls.ads.AdsMagr;
import com.feeyo.net.http.HttpServer;
import com.feeyo.net.udp.UdpServer;

public class UdpServerTest {
	
	//
	public static void main(String[] args) {
		
		
		
		String osName = System.getProperties().getProperty("os.name");
		final String directory;

		if (osName.indexOf("Window") >= 0 || osName.indexOf("Mac") >= 0 ) {
			directory = System.getProperty("user.dir");

		} else {
			File dir = new File(System.getProperty("user.dir"));
			directory = dir.getParent();
		}
		
		HlsCtx.INSTANCE().init(directory);
		AdsMagr.initialize();
		
		UdpServer udpServer = new UdpServer();
		udpServer.startup( 7000 );
		
		// http://127.0.0.1:8888/hls/11/live.m3u8
		HttpServer httpServer = new HttpServer();
		httpServer.startup(8888);
		
	}

}
