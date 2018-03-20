package com.feeyo.util;


import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Md5 {	
	
	private static final char HEX_DIGITS[] =  { 
		'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' 
    };  
	
	public static String toHexString(byte[] b) {  
	    StringBuilder sb = new StringBuilder(b.length * 2);    
	    for (int i = 0; i < b.length; i++) {    
	        sb.append(HEX_DIGITS[(b[i] & 0xf0) >>> 4]);    
	        sb.append(HEX_DIGITS[b[i] & 0x0f]);    
	    }    
	    return sb.toString();    
	}  
	
	public static void main(String args[]) {
		System.out.println(md5_32("123456"));
	}
	
	
	// MD5_16 取了 MD5_32 的中间16位, (8, 24)
	public static String md5_16(String text) {
	   String result = md5_32( text );
	   return result.substring(8, 24);
	}
	
	// 一致的 MD5 32位加密 小写
	public static String md5_32(String text) {
		
		String result = "";		
		try {  
	        MessageDigest digest = java.security.MessageDigest.getInstance("MD5");  
	        digest.update(text.getBytes());  
	        byte messageDigest[] = digest.digest();  
	                                  
	        result = toHexString(messageDigest);  
	        
	    } catch (NoSuchAlgorithmException e) {  
	        e.printStackTrace();  
	    }              
		
		//System.out.println("MD5:" + result);	    
		return result;  
	}

}