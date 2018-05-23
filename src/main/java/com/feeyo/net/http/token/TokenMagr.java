package com.feeyo.net.http.token;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenMagr {
	
	private static final Logger LOGGER = LoggerFactory.getLogger( TokenMagr.class );
	
    private static ScheduledExecutorService scheduledThreadPoolExecutor = Executors.newScheduledThreadPool(1);
	
	private static final ConcurrentHashMap<String, Token> tokenCache = new ConcurrentHashMap<String, Token>();
	
	private static TokenMagr instance = null;
	
	public static TokenMagr getInstance() {
		if (instance == null) {
			synchronized (TokenMagr.class) {
				instance = new TokenMagr();
			}
		}
		return instance;
	}
	
	private TokenMagr() {
		
		scheduledThreadPoolExecutor.scheduleAtFixedRate( new Runnable() {

			@Override
			public void run() {
				
				try {
	            	
					int now = (int) (System.currentTimeMillis() / 1000L);
	            	
	            	Iterator<Token> it = tokenCache.values().iterator();
	            	while( it.hasNext() ) {
	            		
	            		Token token =  it.next();
	            		if ( token != null ) {
	            			
							int expires = token.getCreateAt() + (token.getExpiresIn() * 60 * 60 * 24);
							if (expires < now) {
								tokenCache.remove( token.getAccessString() );
							}
	            		}
	            	}
	                
				} catch(Throwable e) {
					LOGGER.warn("token err:", e);
				}
              
			}
    		
    	}, 5, 5, TimeUnit.MINUTES);
		
	}	

	public String createToken() {
		
		String access_string = UUID.randomUUID().toString() ;
		
		Token token = new Token();
		token.setAccessString( access_string );
		token.setExpiresIn( 1 );
		token.setCreateAt( (int)(System.currentTimeMillis() / 1000L) );
		
		tokenCache.put(access_string, token);
		
		return access_string;
	}
	

	public Token getToken(String access_string) throws Exception {
		Token token = tokenCache.get(access_string);
        return token;
	}

	
	public void deleteToken(String access_string) throws Exception {
		tokenCache.remove(access_string);
	}
	
}