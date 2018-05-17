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
	
	private LinkedHashMap<Integer, BuffPacket> aacMap = new LinkedHashMap<Integer, BuffPacket>();
	private LinkedHashMap<Integer, BuffPacket> avcMap = new LinkedHashMap<Integer, BuffPacket>();
	
	private V5PacketEncoder encoder = new V5PacketEncoder();
	
	private static int MTU = 4096;
	private static int packetSender = 1000131;
	private AtomicBoolean aacLinkConnectionStatus = new AtomicBoolean(false);
	private AtomicBoolean avcLinkConnectionStatus = new AtomicBoolean(false);
	
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
        	
        	if(buff[10] == V5PacketType.H264_STREAM) {
            	
	        	BuffPacket  pkt = avcMap.get( pktId );
	        	if ( pkt != null ) {
	        		avcLinkConnectionStatus.compareAndSet(false, true);
	        		pkt.isResp = true;
	        	}
        	}else if(buff[10] == V5PacketType.AAC_STREAM) {
        	
	        	BuffPacket  pkt2 = aacMap.get( pktId );
	        	if ( pkt2 != null ) {
	        		aacLinkConnectionStatus.compareAndSet(false, true);
	        		pkt2.isResp = true;
	        	}
        	}
        	
        	System.out.println( pktId );
        }

	}
	
	public void write(int streamType,float sampleRate, int sampleSizeInBits, int channels, int fps) {
		
		String MANAGE_URI = "http://localhost:8888/hls/manage";
		long streamId = packetSender;
		List<String> aliasNames = Arrays.asList("11", "22");
		
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
		
		switch(streamType) {
		case HlsLiveStreamType.AAC:
			writeAac(client);
			break;
		case HlsLiveStreamType.H264:
			writeH264(client);
			break;
		case HlsLiveStreamType.AAC_H264_MIXED:
			writeMixed(client);
			break;
		}
		
	}
	
	private void writeMixed(final UdpClient client) {
		Thread t1 = new Thread(new Runnable() {

			@Override
			public void run() {
				writeAac(client);
			}
			
		});
		
		Thread t2 = new Thread(new Runnable() {

			@Override
			public void run() {
				writeH264(client);
			}
			
		});
		
		t1.start();
		t2.start();
	}


	public void writeAac(UdpClient client) {
		
		byte[] fileBuff = TestDataUtil.getAudioData(); 
		V5PacketIdGenerator aacIdGenerator = new V5PacketIdGenerator();
		List<Integer> list = ByteUtil.kmp(fileBuff, new byte[] {(byte) 0xff, (byte) 0xf1});
		for(int i=0; i < list.size(); i++ ) {
			byte[] buff11 = null;
			if(i < list.size() -1) {
				buff11 = Arrays.copyOfRange(fileBuff, list.get(i), list.get(i+1));
			}else {
				buff11 = Arrays.copyOfRange(fileBuff, list.get(i), fileBuff.length);
			}
			int pktId = aacIdGenerator.getId();
			aacMap.put(pktId , new BuffPacket(pktId, buff11));
		}

		
		byte packetType = V5PacketType.AAC_STREAM;
		long aacCounter = 0;
		while(true) {
			
			for(BuffPacket buffPkt : aacMap.values() ) {
				
				List<V5Packet> packets = encoder.encode(MTU, packetSender, packetType, ByteUtil.longToBytes(aacCounter++), buffPkt.id, buffPkt.buff);
				if (packets != null) {
					// 确保发送成功
					while ( !aacMap.get( buffPkt.id ).isResp ) {

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
						
						while(!aacLinkConnectionStatus.get()) {
						}
						
					}
					
				}
			}
			
			for(BuffPacket buffPkt : aacMap.values() ) {
				buffPkt.isResp = false;
			}
			
			//break;
			
		}
	
	}
	
	
	public void writeH264(UdpClient client) {
		
		byte[] fileBuff = TestDataUtil.getVideoData(); 
		V5PacketIdGenerator avcIdGenerator = new V5PacketIdGenerator();
		for(int i=0; i <= fileBuff.length - 2048; i= i+2048 ) {
			
			byte[] buff11 = new byte[2048];
			System.arraycopy(fileBuff, i, buff11, 0, 2048);
			
			int pktId = avcIdGenerator.getId();
			avcMap.put(pktId , new BuffPacket(pktId, buff11));
		}

		int pos = fileBuff.length % 2048;
		if( fileBuff.length % 2048 != 0) {
			byte[] buff11 = new byte[pos];
			System.arraycopy(fileBuff, fileBuff.length - pos, buff11, 0, pos);
			int pktId = avcIdGenerator.getId();
			avcMap.put(pktId , new BuffPacket(pktId, buff11));
		}
		
		byte  packetType = V5PacketType.H264_STREAM;
		
		int avcCounter = 0;
		while(true) {
			
			for(BuffPacket buffPkt : avcMap.values() ) {
				
				List<V5Packet> packets = encoder.encode(MTU, packetSender, packetType,  ByteUtil.longToBytes(avcCounter++), buffPkt.id, buffPkt.buff);
				if (packets != null) {
					
					// 确保发送成功
					while ( !avcMap.get( buffPkt.id ).isResp ) {

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
						
						while(!avcLinkConnectionStatus.get()) {
						}
						
					}
					
				}
			}
			
			for(BuffPacket buffPkt : avcMap.values() ) {
				buffPkt.isResp = false;
			}
			//break;
		}
	}

	public static void main(String[] args) {
		
		UdpClientTest clientTest = new UdpClientTest();
		//clientTest.write(HlsLiveStreamType.AAC_H264_MIXED, 44100F, 16, 2, 25);		
		clientTest.write(HlsLiveStreamType.AAC, 44100F, 16, 2, 25);		
	}
	
	
	static class BuffPacket {
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
