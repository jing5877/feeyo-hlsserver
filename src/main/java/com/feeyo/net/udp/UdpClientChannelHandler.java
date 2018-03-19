package com.feeyo.net.udp;

import java.net.InetSocketAddress;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author zhuam
 *
 */
public class UdpClientChannelHandler extends SimpleChannelUpstreamHandler {
	
	private Logger LOGGER = LoggerFactory.getLogger( UdpClientChannelHandler.class );
	
	
	// 支持 byte dump
	//---------------------------------------------------------------------
	protected static char getChar(byte b) {
		return (b < 32 || b > 127) ? '.' : (char) b;
	}
	
	protected static String dump(byte[] data, int offset, int length) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(" byte dump log ");
		sb.append(System.lineSeparator());
		sb.append(" offset ").append( offset );
		sb.append(" length ").append( length );
		sb.append(System.lineSeparator());
		int lines = (length - 1) / 16 + 1;
		for (int i = 0, pos = 0; i < lines; i++, pos += 16) {
			sb.append(String.format("0x%04X ", i * 16));
			for (int j = 0, pos1 = pos; j < 16; j++, pos1++) {
				sb.append(pos1 < length ? String.format("%02X ", data[offset + pos1]) : "   ");
			}
			sb.append(" ");
			for (int j = 0, pos1 = pos; j < 16; j++, pos1++) {
				sb.append(pos1 < length ? getChar(data[offset + pos1]) : '.');
			}
			sb.append(System.lineSeparator());
		}
		sb.append(length).append(" bytes").append(System.lineSeparator());
		return sb.toString();
	}
	
	@Override
	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		//

        InetSocketAddress addr = (InetSocketAddress)ctx.getChannel().getRemoteAddress();
        if (addr == null){
            addr = (InetSocketAddress)e.getRemoteAddress();
        }

        ChannelBuffer channelBuffer = (ChannelBuffer) e.getMessage();
        byte[] data = channelBuffer.array();
        
        LOGGER.debug( dump(data, 0, data.length) );
		
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
		super.exceptionCaught(ctx, e);
	}

}
