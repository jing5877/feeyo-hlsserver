package com.feeyo.net.http.handler;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import com.feeyo.util.velocity.VelocityBuilder;


public class WelcomeHandler implements IRequestHandler {

	@Override
	public void execute(ChannelHandlerContext ctx, MessageEvent e) {
	
		VelocityBuilder velocityBuilder = new VelocityBuilder();
		String htmlText = velocityBuilder.generate("index.vm", "UTF8", null);	
		
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, htmlText.length());
		response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");
		
		ChannelBuffer buffer = ChannelBuffers.copiedBuffer(htmlText, CharsetUtil.UTF_8);
		response.setContent(buffer);

		ChannelFuture channelFuture = ctx.getChannel().write(response);
		if (channelFuture.isSuccess()) {
			channelFuture.getChannel().close();
		}
	}
	
	@Override
	public boolean isFilted() {
		return false;
	}

	@Override
	public Type getType() {
		return Type.VM;
	}

}
