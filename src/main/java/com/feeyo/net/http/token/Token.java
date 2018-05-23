package com.feeyo.net.http.token;

public class Token {
	
	public static final String ACCESS_STRING = "hls_access_token";

	private String accessString; 	// 令牌
	private int expiresIn; 			// 有效期、天
	private int createAt; 			// 时间
	
	
	public String getAccessString() {
		return accessString;
	}

	public void setAccessString(String accessString) {
		this.accessString = accessString;
	}

	public int getExpiresIn() {
		return expiresIn;
	}

	public void setExpiresIn(int expiresIn) {
		this.expiresIn = expiresIn;
	}

	public int getCreateAt() {
		return createAt;
	}

	public void setCreateAt(int createAt) {
		this.createAt = createAt;
	}
}
