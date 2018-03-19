package com.feeyo.oss;

import com.feeyo.util.Globals;

public class OSSOperationConfig {
	
	// accessKeyId和accessKeySecret是OSS的访问密钥，您可以在控制台上创建和查看，
	public static final String accessKeyId = Globals.getXMLProperty("oss.accessKeyId"); 
	public static final String accessKeySecret = Globals.getXMLProperty("oss.accessKeySecret"); 
	
	public static final String endpoint = Globals.getXMLProperty("oss.endpoint", "oss-cn-hangzhou.aliyuncs.com");
	public static final String bucketName = Globals.getXMLProperty("oss.bucketName", "");
	
}
