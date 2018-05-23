package com.feeyo.net.http.handler;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
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
import org.jboss.netty.handler.codec.http.cookie.ClientCookieDecoder;
import org.jboss.netty.handler.codec.http.cookie.Cookie;
import org.jboss.netty.handler.codec.http.cookie.DefaultCookie;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieEncoder;

import com.feeyo.net.http.token.Token;
import com.feeyo.net.http.util.HttpUtil;
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
			    	
			    	String responseTxt = "";
			    	String access_token = "";
			    	
			    	//
			    	if ( userQ != null && !userQ.isEmpty() &&
			    			pwdQ != null && !pwdQ.isEmpty() ) {
			    		
			    		
			    		
			    		//
			    	}
			    	
			    	//
			    	HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
					response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, responseTxt.length());
					response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/plain;charset=UTF-8"); 

					DefaultCookie c = new DefaultCookie(Token.ACCESS_STRING, access_token);
					c.setMaxAge( 60 * 60 * 24 * 7 );
					c.setPath("/");
					
					Map<String, Cookie> cookies = getCookies( request );
					cookies.put(Token.ACCESS_STRING, c);
					
					for (Cookie cookie : cookies.values()) {
						response.headers().set(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
					}
					
					ChannelBuffer buffer = ChannelBuffers.copiedBuffer(responseTxt, Charset.defaultCharset());
					response.setContent(buffer);
					
					ChannelFuture channelFuture = ctx.getChannel().write(response);
					if (channelFuture.isSuccess()) {
						channelFuture.getChannel().close();
					}
			    	
			    	
	    		} else if ( "LOGOUT".equalsIgnoreCase( action ) ) {
	    			
	    			// logout
					HttpResponse response = HttpUtil.redirectFound("/");

					Map<String, Cookie> cookies = getCookies(request);
					Cookie c = cookies.get(Token.ACCESS_STRING);
					if (c != null) {
						c.setMaxAge(-1);
						c.setPath("/");
						cookies.put(Token.ACCESS_STRING, c);
					}

					for (Cookie cookie : cookies.values()) {
						response.headers().add(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
					}

					ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
	    	        
	    			
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
	
	private Map<String, Cookie> getCookies(HttpRequest request) {
		// 解析 Cookie
		Map<String, Cookie> cookies = new HashMap<String, Cookie>();
		List<String> allCookieHeaders = request.headers().getAll( HttpHeaders.Names.COOKIE );
		for (String aCookieHeader : allCookieHeaders) {
			Cookie c = ClientCookieDecoder.STRICT.decode(aCookieHeader);
			if (c != null) {
				cookies.put(c.name(), c);
			}
		}
		return cookies;
	}

}
