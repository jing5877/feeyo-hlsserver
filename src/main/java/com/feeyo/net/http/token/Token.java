package com.feeyo.net.http.token;

public class Token {
	
	public static final String ACCESS_STRING = "hls_access_token";

	private int userId; 			// 用户编号

	private String accessToken; 	// 令牌
	private int expiresIn; 			// 有效期、天
	private int createTimestamp; 	// 时间

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public int getExpiresIn() {
		return expiresIn;
	}

	public void setExpiresIn(int expiresIn) {
		this.expiresIn = expiresIn;
	}

	public int getCreateTimestamp() {
		return createTimestamp;
	}

	public void setCreateTimestamp(int createTimestamp) {
		this.createTimestamp = createTimestamp;
	}
}
