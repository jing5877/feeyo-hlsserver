package com.feeyo.net.udp.test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictor;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;

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

public class RtspClientTest {
	
	private ConnectionlessBootstrap dataBootstrap;
	private ChannelFactory factory = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
	private DatagramChannel dataChannel;
	
	private ConcurrentHashMap<Integer, BuffPacket> map = new ConcurrentHashMap<Integer, BuffPacket>();
	private V5PacketIdGenerator idGenerator = new V5PacketIdGenerator();
	private int currentIndex = 1;
	
	private String rtspUrl = "";
	
	public void startUp() {
		
		RtspClient rtspClient = new RtspClient(rtspUrl);
		rtspClient.start();
		
		Thread thread = new Thread(new TransmitWorker());
		thread.start();
		
		this.dataBootstrap = new ConnectionlessBootstrap(factory);
		dataBootstrap.setOption("receiveBufferSizePredictor", new FixedReceiveBufferSizePredictor(2048));
		dataBootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(2048));

		this.dataBootstrap.getPipeline().addLast("handler", new SimpleChannelUpstreamHandler() {
			@Override
			public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
					
				ChannelBuffer channelBuffer = (ChannelBuffer) e.getMessage();
				byte[] data = channelBuffer.array();
				if (data.length <= 12)
					return;
				BuffPacket buff11 = new BuffPacket(idGenerator.getId(), DataPacket.decode(data).getDataAsArray());
				map.put(buff11.id , buff11);
			}
		});
		this.dataChannel = (DatagramChannel) this.dataBootstrap.bind(new InetSocketAddress(8360));

	}
	
	public void close() {

		if (this.dataChannel != null)
			this.dataChannel.close();

		if (this.dataBootstrap != null)
			this.dataBootstrap.releaseExternalResources();
		
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
	
	public static void main(String[] args) {
		RtspClientTest udpClientTest = new RtspClientTest();
		udpClientTest.startUp();
	}

}
