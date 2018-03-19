package com.feeyo;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.HlsLiveStreamMagr;
import com.feeyo.net.http.HttpServer;
import com.feeyo.net.udp.UdpServer;
import com.feeyo.util.Globals;
import com.feeyo.util.Log4jInitializer;



/**
 * HTTP Live Streaming(HLS), 支持语音、视频的直播
 * 
 * @author xuwenfeng@variflight.com
 * @author zhuam
 */

public class HlsServer {
	
	public static void main(String[] args) {
		
		String osName = System.getProperties().getProperty("os.name");
		final String directory;

		if (osName.indexOf("Window") >= 0 || osName.indexOf("Mac") >= 0 ) {
			directory = System.getProperty("user.dir");

		} else {
			File dir = new File(System.getProperty("user.dir"));
			directory = dir.getParent();
		}
		
		// 设置 LOG4J
		Log4jInitializer.configureAndWatch( directory, "log4j.xml", 30000L);
		
		//app home
		Globals.setHomeDirectory(directory);
		Globals.setConfigName("hls.xml");
		
		
		final Logger LOGGER = LoggerFactory.getLogger(HlsServer.class);
		
		try {
			int httpPort = Globals.getIntProperty("http.port", -1);
			int udpPort = Globals.getIntProperty("udp.port", -1);
			
			//udp server
			final UdpServer udpServer = new UdpServer();
			udpServer.startup(udpPort);
			
			//http server
			final HttpServer httpServer = new HttpServer();
			httpServer.startup(httpPort);
			
			HlsLiveStreamMagr.INSTANCE().startup();

			LOGGER.info("##hls-server startup ");

			//shutdown hook
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					if (udpServer != null) 
						udpServer.close();

					if (httpServer != null) 
						httpServer.close();

					HlsLiveStreamMagr.INSTANCE().close();
					LOGGER.info("##hls-server shutdown");
				}
			});
			
		} catch(Throwable e) {
			LOGGER.info("", e);
			System.exit(0);	
		}
	}
	
}