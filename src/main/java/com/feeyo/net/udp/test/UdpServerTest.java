package com.feeyo.net.udp.test;

import java.io.File;

import com.feeyo.net.http.HttpServer;
import com.feeyo.net.udp.UdpServer;
import com.feeyo.util.Log4jInitializer;

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
		
		// 设置 LOG4J （加载faacEncoder.dll）
		Log4jInitializer.configureAndWatch( directory, "log4j.xml", 30000L);
		
		UdpServer udpServer = new UdpServer();
		udpServer.startup( 7000 );
		
		// http://127.0.0.1:8888/hls/11/live.m3u8
		HttpServer httpServer = new HttpServer();
		httpServer.startup(8888);
		
	}

}
