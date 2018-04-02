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
import com.feeyo.net.udp.packet.ByteUtil;
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
				
				// RESP
				byte[] response =  new byte[11];
				response[0] = 0x11;
				response[1] = 0x12;
				
				response[2] = ByteUtil.getByte3( packet.getPacketId() );
				response[3] = ByteUtil.getByte2( packet.getPacketId() );
				response[4] = ByteUtil.getByte1( packet.getPacketId() );
				response[5] = ByteUtil.getByte0( packet.getPacketId() );
				
				response[6] = ByteUtil.getByte3( packet.getPacketOffset() );
				response[7] = ByteUtil.getByte2( packet.getPacketOffset() );
				response[8] = ByteUtil.getByte1( packet.getPacketOffset() );
				response[9] = ByteUtil.getByte0( packet.getPacketOffset() );
				
				response[10] = packet.getPacketType();
				e.getChannel().write( ChannelBuffers.copiedBuffer( response ) , addr);
				
			}
		} catch (V5PacketErrorException e1) {
			LOGGER.warn("packet decode err:", e);
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
