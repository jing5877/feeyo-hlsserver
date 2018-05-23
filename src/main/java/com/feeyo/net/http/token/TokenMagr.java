package com.feeyo.net.http.token;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TokenMagr {
	
	private static final ConcurrentHashMap<String, Token> tokens = new ConcurrentHashMap<String, Token>();
	
	private static TokenMagr instance = null;
	
	public static TokenMagr getInstance() {
		if (instance == null) {
			synchronized (TokenMagr.class) {
				instance = new TokenMagr();
			}
		}
		return instance;
	}
	
	private TokenMagr() {}	
	

	public String createToken() {
		
		String access_string = UUID.randomUUID().toString() ;
		
		Token token = new Token();
		token.setAccessString( access_string );
		token.setExpiresIn( 2 );
		token.setCreateAt( (int)(System.currentTimeMillis() / 1000L) );
		
		tokens.put(access_string, token);
		
		return access_string;
	}
	

	public Token getToken(String access_string) throws Exception {
		Token token = tokens.get(access_string);
        return token;
	}

	
	public void deleteToken(String access_string) throws Exception {
		tokens.remove(access_string);
	}
	
	
}
