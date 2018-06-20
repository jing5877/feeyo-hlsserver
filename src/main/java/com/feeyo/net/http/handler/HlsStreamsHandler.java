package com.feeyo.net.http.handler;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

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

import com.feeyo.hls.HlsLiveStreamMagr;
import com.feeyo.util.velocity.VelocityBuilder;


public class HlsStreamsHandler implements IRequestHandler {
	
	@Override
	public Type getType() {
		return IRequestHandler.Type.VM;
	}

	@Override
	public boolean isFilted() {
		return true;
	}

	@Override
	public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		VelocityBuilder velocityBuilder = new VelocityBuilder();
		Map<String,Object> model = new HashMap<String, Object>();
		
		model.put("streams", HlsLiveStreamMagr.INSTANCE().getAllLiveStream()); 
		
		String htmlText = velocityBuilder.generate("streams.vm", "UTF8", model);
		
		HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, htmlText.length());
		response.headers().set(HttpHeaders.Names.CONTENT_TYPE, "text/html; charset=UTF-8");

		ChannelBuffer buffer = ChannelBuffers.copiedBuffer(htmlText, Charset.defaultCharset());// CharsetUtil.UTF_8);
		response.setContent(buffer);

		ChannelFuture channelFuture = ctx.getChannel().write(response);
		if (channelFuture.isSuccess()) {
			channelFuture.getChannel().close();
		}
		
	}

}
