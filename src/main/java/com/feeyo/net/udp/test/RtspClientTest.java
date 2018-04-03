package com.feeyo.net.udp.test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.rtsp.RtspHeaders;
import org.jboss.netty.handler.codec.rtsp.RtspMethods;
import org.jboss.netty.handler.codec.rtsp.RtspVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtspClientTest {
	
private static final Logger LOGGER = LoggerFactory.getLogger( "RtspClient" );
	
	private String server_address;
	private String url;
	
	private RtspConnection rtspConn;
	private RtpDataSource rtpDataSource;	
	
	public RtspClientTest(String url) {
		this.url = url;
	}
	
	public void start() throws IOException {			
		// parse  
		Pattern pattern = Pattern.compile("^rtsp://([^:/]+)(:([0-9]+))?");
		Matcher m = pattern.matcher(url);
		if ( !m.find() ) {			
			System.out.println("Illegal RTSP address[" + url + "]");			
			throw new IllegalArgumentException("Illegal RTSP address[" + url + "]");
		}
		String host = m.group(1);
		int port = Integer.parseInt(m.group(3));
		this.server_address = host;
		
		this.rtspConn = new RtspConnection(host, port);
		this.rtspConn.connect();		
		
		describe();		
		setup();		
		play();		
	}
	
	public void describe() {			
		DefaultHttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, url);
		request.headers().add(RtspHeaders.Names.ACCEPT, "application/sdp");
		HttpResponse resp = rtspConn.send(request).get();
		ChannelBuffer data = resp.getContent();
		byte[] array = new byte[ data.readableBytes() ];
		data.readBytes(array);
		String sdp = new String( array );
		
		System.out.println("-----------------------------");	
		String[] lines = sdp.split("\n");
		for (String line : lines) {			
			System.out.println( line );
		}
	}
	
	public boolean setup() throws IOException {
		
		int[] ports = PortUtil.findAvailablePorts(2);		
		String transport = String.format("RTP/AVP;unicast;client_port=%d-%d", ports[0], ports[1]);	
		
		HttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, url );	
        request.headers().add( RtspHeaders.Names.TRANSPORT, transport );
        
        HttpResponse resp = rtspConn.send(request).get();
		if ( resp == null ) {
			LOGGER.warn("fail setup {}", url);
			return false;
		}
        
		transport = resp.headers().get(RtspHeaders.Names.TRANSPORT);
		if (!StringUtils.startsWithIgnoreCase(transport, "RTP/AVP/UDP;unicast")
				&& !StringUtils.startsWithIgnoreCase(transport, "RTP/AVP;unicast")) {
			LOGGER.error("can't support {}", transport);
			return false;
		}

		// UDP Transport
		int client_port_0 = ports[0];
		int client_port_1 = ports[1];
		int server_port_0 = 0;
		int server_port_1 = 0;
		int ssrc = 0;
		
		Matcher matcher = Pattern.compile("([^\\s=;]+)=(([^-;]+)(-([^;]+))?)").matcher(transport);
		while (matcher.find()) {
			String key = matcher.group(1).toLowerCase();
			if ("client_port".equals(key)) {
				client_port_0 = Integer.parseInt( matcher.group(3) );
				client_port_1 = Integer.parseInt( matcher.group(5) );
			} else if ("server_port".equals(key)) {
				server_port_0 = Integer.parseInt( matcher.group(3) );
				server_port_1 = Integer.parseInt( matcher.group(5) );
			} else if ("ssrc".equals(key)) {
				ssrc = Integer.parseInt( matcher.group(2) );
			} else {
				LOGGER.warn("ignored [{}={}]", key, matcher.group(2));
			}
		}
		
		System.out.println(server_address + ", " + server_port_0 +  ", " +  server_port_1 
				+ ", " +  client_port_0 + ", " +  client_port_1  + ", " + ssrc);
		
		final String sessionId = resp.headers().get(RtspHeaders.Names.SESSION);
		
        this.rtpDataSource = new RtpDataSource(server_address, server_port_0, server_port_1, client_port_0, client_port_1);	
        this.rtpDataSource.setSsrc(ssrc);
        this.rtpDataSource.setSessionId(sessionId);
        boolean connected = this.rtpDataSource.connect();
        if ( !connected ) {
			throw new IOException("fail connect " + transport);
		}
        
        return true;
        
	}
	
	public void play() {
		DefaultHttpRequest request = new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.PLAY, url );	
		request.headers().add(RtspHeaders.Names.RANGE, "npt=0.000-");		
		this.rtspConn.send(request);
	}
		
	public static void main(String[] args) {
		
		String rtspUrl = "";
		RtspClientTest fmsClient = new RtspClientTest(rtspUrl);
		try {
			fmsClient.start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
