package com.feeyo.net.http.filter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.cookie.Cookie;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.net.http.token.Token;
import com.feeyo.net.http.token.TokenMagr;


public class AuthCheckFilter implements IFilter {
	
	private static Logger LOGGER = LoggerFactory.getLogger( AuthCheckFilter.class );


	@Override
	public boolean doFilter(ChannelHandlerContext ctx, MessageEvent messageEvent) {
		
		HttpRequest request = (DefaultHttpRequest) messageEvent.getMessage();
		
		boolean isPass = true;
		
		// 解析 Cookie
		Map<String, Cookie> cookieMap = new HashMap<String, Cookie>();
		
		String value = request.headers().get( HttpHeaders.Names.COOKIE );
		Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(value != null ? value : "");

		Iterator<Cookie> it = cookies.iterator();
		while( it.hasNext() ) {
			Cookie c = it.next();
			cookieMap.put(c.name(), c);
		}
		
		Cookie cookie = cookieMap.get( Token.ACCESS_STRING );
		if ( cookie != null ) {		
			
			//提取 access_token
			String access_token = cookie.value();
			try {					
				Token token = TokenMagr.getInstance().getTokenByAccessString(access_token);
				if ( token != null ) {
					
					int now = (int)(System.currentTimeMillis() / 1000L);
					int expires =  token.getCreateTimestamp() + ( token.getExpiresIn() * 60 * 60 * 24);
					if ( expires < now) {
						TokenMagr.getInstance().deleteTokenByAccessString( access_token );
						isPass = false;
					}
					
				} else {					
					isPass = false;
				}
				
			} catch( Exception e) {
				LOGGER.error("filter error  -- > " + request.getUri(), e);
			}
			
		} else {
			isPass = false;
		}
		
		return isPass;
		
		
	}

}
