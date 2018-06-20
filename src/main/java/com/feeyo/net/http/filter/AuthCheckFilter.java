package com.feeyo.net.http.filter;

import java.util.Iterator;
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
		
		boolean isPass = true;
		
		HttpRequest request = (DefaultHttpRequest) messageEvent.getMessage();
		String access_string = request.headers().get(Token.ACCESS_STRING);
		if ( access_string == null ) {
			// 解析 Cookie
			String value = request.headers().get( HttpHeaders.Names.COOKIE );
			Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(value != null ? value : "");

			Iterator<Cookie> it = cookies.iterator();
			while( it.hasNext() ) {
				Cookie c = it.next();
				if ( Token.ACCESS_STRING.equals( c.name() ) ) {
					access_string = c.value();
					break;
				}
			}
		}
		
		
		if (access_string != null) {
			try {
				Token token = TokenMagr.getInstance().getToken(access_string);
				if (token != null) {

					int now = (int) (System.currentTimeMillis() / 1000L);
					int expires = token.getCreateAt() + (token.getExpiresIn() * 60 * 60 * 24);
					if (expires < now) {
						TokenMagr.getInstance().deleteToken(access_string);
						isPass = false;
					}

				} else {
					isPass = false;
				}

			} catch (Exception e) {
				LOGGER.error("filter error  -- > " + request.getUri(), e);
			}

		} else {
			isPass = false;
		}

		return isPass;
		
		
	}

}
