package com.feeyo.net.udp.test;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

import com.feeyo.hls.HlsLiveStreamType;
import com.feeyo.net.http.util.HlsRpcUtil;
import com.feeyo.net.udp.UdpClient;
import com.feeyo.net.udp.UdpClientChannelHandler;
import com.feeyo.net.udp.packet.ByteUtil;
import com.feeyo.net.udp.packet.V5Packet;
import com.feeyo.net.udp.packet.V5PacketEncoder;
import com.feeyo.net.udp.packet.V5PacketIdGenerator;
import com.feeyo.net.udp.packet.V5PacketType;


/**
 * https://dco4urblvsasc.cloudfront.net/811/81095_ywfZjAuP/game/index.m3u8
 * https://dco4urblvsasc.cloudfront.net/811/81095_ywfZjAuP/game/1000kbps-00001.ts
 * 
 * @author zhuam
 *
 */
public class UdpClientTest extends UdpClientChannelHandler {
	
	private LinkedHashMap<Integer, BuffPacket> map = new LinkedHashMap<Integer, BuffPacket>();
	
	private V5PacketEncoder encoder = new V5PacketEncoder();
	
	private static int MTU = 4096;
	private static int packetSender = 1000131;
	private AtomicBoolean linkConnectionStatus = new AtomicBoolean(false);
	
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		//
		
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
        	
        	BuffPacket  pkt = map.get( pktId );
        	if ( pkt != null ) {
        		linkConnectionStatus.compareAndSet(false, true);
        		pkt.isResp = true;
        	}
        	System.out.println( pktId );
        }
        

	}
	
	
	public void write() {
		
	

		
		String MANAGE_URI = "http://localhost:8888/hls/manage";
		long streamId = packetSender;
		int streamType = HlsLiveStreamType.H264;
		List<String> aliasNames = Arrays.asList("11", "22");
		float sampleRate = 8000F;
		int sampleSizeInBits = 16;
		int channels = 1;
		int fps = 25;
		
		boolean resp = HlsRpcUtil.INSTANCE().startLiveStream(MANAGE_URI, streamId, streamType, aliasNames, 
				sampleRate, sampleSizeInBits, channels, fps);
		if ( !resp ) {
			return;
		}
		
		
		UdpClient client = new UdpClient( this );
		
		//
		try {
			Thread.sleep(1000L);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		//丢弃最后不足2048的语音部分
		byte[] fileBuff = TestDataUtil.getVideoData(); 
		for(int i=0; i <= fileBuff.length - 2048; i= i+2048 ) {
			
			byte[] buff11 = new byte[2048];
			System.arraycopy(fileBuff, i, buff11, 0, 2048);
			
			int pktId = V5PacketIdGenerator.getINSTNCE().getId();
			map.put(pktId , new BuffPacket(pktId, buff11));
		}

		
		byte  packetType = V5PacketType.H264_STREAM;
		byte[] packetReserved = new byte[]{ 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
		while(true) {
			
			for(BuffPacket buffPkt : map.values() ) {
				
				List<V5Packet> packets = encoder.encode(MTU, packetSender, packetType, packetReserved, buffPkt.id, buffPkt.buff);
				if (packets != null) {
					
					// 确保发送成功
					while ( !map.get( buffPkt.id ).isResp ) {

						for (V5Packet p : packets) {
							byte[] payload = encoder.encode(p);
							client.write(payload, new InetSocketAddress("127.0.0.1", 7000));
						}
						
						//
						try {
							Thread.sleep(10L);	
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
						while(!linkConnectionStatus.get()) {
						}
						
					}
					
				}
			}
			
			for(BuffPacket buffPkt : map.values() ) {
				buffPkt.isResp = false;
			}
			
			//break;
			
		}
		
	}

	public static void main(String[] args) {
		
		UdpClientTest clientTest = new UdpClientTest();
		clientTest.write();		
	}
	
	
	class BuffPacket {
		public int id;
		public byte[] buff;
		public boolean isResp = false;
		
		public BuffPacket(int id, byte[] buff) {
			super();
			this.id = id;
			this.buff = buff;
		}
	}

}
