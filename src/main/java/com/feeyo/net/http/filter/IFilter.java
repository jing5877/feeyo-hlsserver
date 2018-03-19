package com.feeyo.net.http.filter;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

public interface IFilter {
	
	public boolean doFilter(ChannelHandlerContext ctx, MessageEvent e);

}
