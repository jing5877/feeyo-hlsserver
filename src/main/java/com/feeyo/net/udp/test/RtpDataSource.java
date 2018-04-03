package com.feeyo.net.udp.test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.HlsLiveStreamType;
import com.feeyo.net.http.util.HlsRpcUtil;
import com.feeyo.net.udp.UdpClient;
import com.feeyo.net.udp.UdpClientChannelHandler;
import com.feeyo.net.udp.packet.ByteUtil;
import com.feeyo.net.udp.packet.V5Packet;
import com.feeyo.net.udp.packet.V5PacketEncoder;
import com.feeyo.net.udp.packet.V5PacketIdGenerator;
import com.feeyo.net.udp.packet.V5PacketType;
import com.feeyo.net.udp.test.UdpClientTest.BuffPacket;
import com.feeyo.net.udp.test.packet.DataPacket;
import com.feeyo.net.udp.test.packet.ReceiverReportPacket;
import com.feeyo.net.udp.test.packet.ReceptionReport;
import com.feeyo.net.udp.test.packet.RtpVersion;

public class RtpDataSource {
	
	private static final Logger LOGGER = LoggerFactory.getLogger( "RtpDataSource" );
	
	private int ssrc;
	private String server_address;
	private int server_port_0;
	private int server_port_1;	
	private int client_port_0;
	private int client_port_1;
	
	private String sessionId;

	private ConnectionlessBootstrap bootstrap;
	private DatagramChannel channel;  
	
	private boolean started = false;
	private boolean connected = false;
	
	
	private ConcurrentHashMap<Integer, BuffPacket> map = new ConcurrentHashMap<Integer, BuffPacket>();
	private V5PacketIdGenerator idGenerator = new V5PacketIdGenerator();
	private int currentIndex = 1;
	
	private Timer timer;
	
	public RtpDataSource(String server_address, int server_port_0, int server_port_1, int client_port_0, int client_port_1) {		
		this.server_address = server_address;
		this.server_port_0 = server_port_0;
		this.server_port_1 = server_port_1;
		this.client_port_0 = client_port_0;
		this.client_port_1 = client_port_1;
	}
	
	public void setSsrc(int ssrc) {
		this.ssrc = ssrc;
	}
	
	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public void start() {
		
		if (!connected) {
			throw new IllegalStateException("please call connect first");
		}
		
		if (started) {
			throw new IllegalStateException("its started");
		}
		started = true;
	}
	
	public boolean connect() {
		
		ChannelFactory factory = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
		
		bootstrap = new ConnectionlessBootstrap( factory );
		bootstrap.getPipeline().addLast("handler", new SimpleChannelUpstreamHandler() {			
			
			private long old = 0 ;	
			
			@Override
			public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
				
				try {					
					ChannelBuffer channelBuffer = (ChannelBuffer) e.getMessage();
					byte[] data = channelBuffer.array();
					BuffPacket buff11 = new BuffPacket(idGenerator.getId(), DataPacket.decode(data).getDataAsArray());
					map.put(buff11.id , buff11);
					
					long now = System.currentTimeMillis();
					if ( now - old > (5 * 1000) ) {
						old = now;
						
						// send rtcp
						ReceiverReportPacket controlPacket = new ReceiverReportPacket();
						controlPacket.setVersion( RtpVersion.V2 );
						controlPacket.setSenderSsrc( ssrc );
						
						ReceptionReport block = new ReceptionReport();
						block.setCumulativeNumberOfPacketsLost(0);
						block.setDelaySinceLastSenderReport(0);
						block.setExtendedHighestSequenceNumberReceived(0);
						block.setFractionLost((short)0);
						block.setInterArrivalJitter(0);
						block.setLastSenderReport(0);
						block.setSsrc( ssrc );						
						controlPacket.addReceptionReportBlock(block);
						
						ChannelBuffer buffer = controlPacket.encode();
						channel.write(buffer, new InetSocketAddress(server_address, server_port_1));	
					}
					
				} catch (Exception e1) {
					e1.printStackTrace();
					LOGGER.error("", e1);
				}
			}
		});
		channel = (DatagramChannel) bootstrap.bind(new InetSocketAddress( client_port_0 ));
		
		
		this.timer = new Timer();
		this.timer.schedule(new TimerTask() {
			@Override
			public void run() {				
				if ( channel != null ) {
					byte[] bb = sessionId.getBytes();
					ChannelBuffer out = ChannelBuffers.buffer(bb.length);
					out.writeBytes( bb );
					channel.write(out, new InetSocketAddress(server_address, server_port_0));
				}				
			}	
			
		}, 0, 2000);	
		
		Thread thread = new Thread(new TransmitWorker());
		thread.start();
		
		connected = true;
		return true;
	}
	
	public void disconnect() {
		
		if( channel != null ){  
            channel.close();  
            channel = null;
        }  
		
		if ( bootstrap != null ) {
			bootstrap.releaseExternalResources();
		}		
		
		//TODO: block
		PortUtil.removePort( client_port_0 );
		PortUtil.removePort( client_port_1 );
		
		if ( timer != null ) {
			timer.cancel();
			timer = null;
		}
	}
	
	class TransmitWorker extends UdpClientChannelHandler implements Runnable {
		
		private int MTU = 4096;
		private byte[] reserved = new byte[] {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
		
		private V5PacketEncoder encoder = new V5PacketEncoder();
		private int packetSender = 1000131;
		
		private AtomicBoolean linkConnectionStatus = new AtomicBoolean(false);
		private UdpClient udpClient = null;
		
		public TransmitWorker() {
			String MANAGE_URI = "http://localhost:8888/hls/manage";
			long streamId = 1000131;
			List<String> aliasNames = Arrays.asList("11", "22");
			
			boolean resp = HlsRpcUtil.INSTANCE().startLiveStream(MANAGE_URI, streamId, HlsLiveStreamType.PCM, aliasNames, 
					8000F, 16, 1, 25);
			if ( !resp ) {
				System.out.println("Initialze failed!");
				return;
			}
			
			udpClient = new UdpClient(this);
		}
		
		@Override
		public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
			
			InetSocketAddress addr = (InetSocketAddress)ctx.getChannel().getRemoteAddress();
	        if (addr == null){
	            addr = (InetSocketAddress)e.getRemoteAddress();
	        }
	        
	        ChannelBuffer channelBuffer = (ChannelBuffer) e.getMessage();
	        byte[] buff = channelBuffer.array();
	        
	        System.out.println("######################################");
	        System.out.println( dump(buff, 0, buff.length) );
	        
	        if ( buff[0] == 0x11 && buff[1] == 0x12 ) {
	        	int pktId = (int)ByteUtil.bytesToInt(buff[2], buff[3], buff[4], buff[5]);
	        	if(buff[10] == V5PacketType.PCM_STREAM) {
	        	
		        	BuffPacket  pkt2 = map.get( pktId );
		        	if ( pkt2 != null ) {
		        		pkt2.isResp = true;
		        		linkConnectionStatus.set(true);
		        	}
	        	}
	        	System.out.println( pktId );
	        }
		}

		@Override
		public void run() {
			while(true) {
				BuffPacket buff11 = map.get(currentIndex);
				if(buff11 != null) {
					while(!map.get(currentIndex).isResp) {
						
						List<V5Packet> packets = encoder.encode(MTU, packetSender, V5PacketType.PCM_STREAM, reserved, buff11.id, buff11.buff);
						if(packets != null) {

							for(V5Packet packet : packets) {
							
								byte[] payload = encoder.encode(packet);
								if(payload != null)
									udpClient.write(payload, new InetSocketAddress("127.0.0.1", 7000));
								
								try {
									Thread.sleep(10);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								
								while(!linkConnectionStatus.get()) {
								}
							}
						}
					}
					map.remove(currentIndex++);
				}
			}
		}
		
	}

}
