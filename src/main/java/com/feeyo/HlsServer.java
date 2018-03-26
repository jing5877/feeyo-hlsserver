package com.feeyo;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.AdsMagr;
import com.feeyo.hls.HlsLiveStreamMagr;
import com.feeyo.net.http.HttpServer;
import com.feeyo.net.udp.UdpServer;



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
		
		HlsCtx.INSTANCE().init( directory );
		

		final Logger LOGGER = LoggerFactory.getLogger(HlsServer.class);
		
		try {
			
			// ads loading
			AdsMagr.initialize();
			
			int httpPort = Integer.parseInt( HlsCtx.INSTANCE().getServerMap().get("http_port") );
			int udpPort = Integer.parseInt( HlsCtx.INSTANCE().getServerMap().get("udp_port") );
			
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