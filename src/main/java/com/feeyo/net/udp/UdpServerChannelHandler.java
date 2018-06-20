package com.feeyo.net.udp;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ChildChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.HlsLiveStreamMagr;
import com.feeyo.net.udp.packet.V5Packet;
import com.feeyo.net.udp.packet.V5PacketDecoder;
import com.feeyo.net.udp.packet.V5PacketErrorException;
import com.feeyo.net.udp.packet.V5PacketType;

/**
 * 支持 S2S 语音及业务数据流 传输
 * 
 * @author zhuam
 *
 */
public class UdpServerChannelHandler extends SimpleChannelHandler {

	private final Logger LOGGER = LoggerFactory.getLogger(UdpServerChannelHandler.class);
	
	private static V5PacketDecoder decoder = new V5PacketDecoder();
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		InetSocketAddress addr = (InetSocketAddress) ctx.getChannel().getRemoteAddress();
		if (addr == null) {
			addr = (InetSocketAddress) e.getRemoteAddress();
		}

		ChannelBuffer channelBuffer = (ChannelBuffer) e.getMessage();
		byte[] data = channelBuffer.array();
		
		// decode err
		if ( data.length < V5Packet.HEAD_LENGTH ) {
			byte[] response = new byte[2]; 
			response[0] = 0x11;
			response[1] = 0x13;
			e.getChannel().write( ChannelBuffers.copiedBuffer( response ) , addr);
			return;
		}
		
		try {
			
			V5Packet packet = decoder.decode( data );
			if ( packet != null ) {
				switch( packet.getPacketType() ) {
				case V5PacketType.PCM_STREAM:
				case V5PacketType.AAC_STREAM:
				case V5PacketType.YUV422_STREAM:
				case V5PacketType.H264_STREAM:
					HlsLiveStreamMagr.INSTANCE().handleStream(packet);		
					break;
				}
			}
			
			// RESP
			byte[] response =  new byte[11];
			response[0] = 0x11;
			response[1] = 0x12;
			
			// packetId
			response[2] = data[15];
			response[3] = data[16];
			response[4] = data[17];
			response[5] = data[18];
			
			// packetOffset
			response[6] = data[23];
			response[7] = data[24];
			response[8] = data[25];
			response[9] = data[26];
			
			response[10] = data[6];
			e.getChannel().write( ChannelBuffers.copiedBuffer( response ) , addr);
			
		} catch (V5PacketErrorException e1) {
			
			LOGGER.warn("packet decode err:", e1);
			
			byte[] response = new byte[2]; 
			response[0] = 0x11;
			response[1] = 0x13;
			e.getChannel().write( ChannelBuffers.copiedBuffer( response ) , addr);
		} 
	}

	@Override
	public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		super.channelClosed(ctx, e);
	}

	@Override
	public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		super.channelConnected(ctx, e);
	}

	@Override
	public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
		super.channelDisconnected(ctx, e);
	}

	@Override
	public void childChannelClosed(ChannelHandlerContext ctx, ChildChannelStateEvent e) throws Exception {
		super.childChannelClosed(ctx, e);
	}

}
