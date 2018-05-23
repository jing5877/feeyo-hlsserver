package com.feeyo.net.http.token;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TokenMagr {
	
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
	
	
	public void deleteTokenByAccessString(String access_string) throws Exception {
		
	}
	
	public Token getTokenByAccessString(String access_string) throws Exception {
		
		Token token = null;
		
		
        return token;
	}

}
