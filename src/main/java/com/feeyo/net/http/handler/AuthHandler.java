package com.feeyo.net.http.handler;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

import com.feeyo.util.velocity.VelocityBuilder;

// 
public class AuthHandler implements IRequestHandler {

	@Override
	public Type getType() {
		return IRequestHandler.Type.NONE;
	}

	@Override
	public boolean isFilted() {
		return false;
	}

	@Override
	public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		HttpRequest request = (DefaultHttpRequest) e.getMessage();
	    String uri = request.getUri();
	    
	    // action= login || logout || page
	    // user=
	    // pwd=
	    
	    QueryStringDecoder decoder = new QueryStringDecoder( uri );
	    Map<String, List<String>> parameters = decoder.getParameters();
	    if ( parameters != null && !parameters.isEmpty() ) {
	    	
	    	List<String> actionQ = parameters.get("action");
	    	if ( actionQ != null && !actionQ.isEmpty() ) {
	    		String action = actionQ.get(0);
	    		
	    		if ( "LOGIN".equalsIgnoreCase( action ) ) {
			    	List<String> userQ = parameters.get("user");
			    	List<String> pwdQ = parameters.get("pwd");
			    	
			    	//
			    	if ( userQ != null && !userQ.isEmpty() &&
			    			pwdQ != null && !pwdQ.isEmpty() ) {

			    		//
			    	}
			    	
			    	
	    		} else if ( "LOGOUT".equalsIgnoreCase( action ) ) {
	    			
	    			// logout
	    			
	    		} else if ( "PAGE".equalsIgnoreCase( action ) ) {
	    			
	    			
	    			VelocityBuilder velocityBuilder = new VelocityBuilder();
	    			String htmlText = velocityBuilder.generate("login.vm", "UTF8", null);
	    			
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
	    	
	    }
		
	}

}
