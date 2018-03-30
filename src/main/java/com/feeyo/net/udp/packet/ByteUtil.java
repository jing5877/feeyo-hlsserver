package com.feeyo.net.udp.packet;

import java.util.ArrayList;
import java.util.List;

public class ByteUtil {
	
	// 将字节数组 转换为 十六进制字符串
	public static String asHex(byte[] buf) {
		return asHex(buf, 0, buf.length);
	}

	public static String asHex(byte[] buf, int offset, int len) {
		StringBuffer sb = new StringBuffer();
		int end = offset + len;
		for (int i = offset; i < end; i++) {
			sb.append(Integer.toHexString((buf[i] >>> 4) & 0x0F));
			sb.append(Integer.toHexString(buf[i] & 0x0F));
		}
		return sb.toString();
	}
	
	// 指定 dst 的偏移值, 将 src (0 - length) 的内容复制到 dst 数组中
	public static int copyBytes(byte[] src, byte[] dst, int offset) {
		for (int k = 0; k < src.length; k++) {
			dst[offset + k] = src[k];
		}
		return src.length;
	}

	// 指定 dst 的偏移值, 将 src (0 - 指定 len) 的内容复制到 dst 数组中
	public static int copyBytes(byte[] src, byte[] dst, int offset, int len) {
		for (int k = 0; k < len; k++) {
			dst[offset + k] = src[k];
		}
		return len;
	}
	
	// 指定 dst 的偏移值, 将 src (指定偏移 src_off + 指定 len )的内容复制到 dst 数组中
	public static int copyBytes(byte[] src, int src_off, byte[] dst, int dst_off, int len) {
		for (int k = 0; k < len; k++) {
			dst[dst_off + k] = src[src_off + k];
		}
		return len;
	}

	// 获取一个 int 字节 #0 (i.e. 低字节)
	public static byte getByte0(long i) {
		return (byte) ( i & 0xFF );
	}

	// 获取一个 int 字节 #1
	public static byte getByte1(long i) {
		return (byte) ( (i & 0xFF00) >> 8 );
	}

	// 获取一个 int 字节 #2
	public static byte getByte2(long i) {
		return (byte) ( (i & 0xFF0000) >> 16 );
	}

	// 获取一个 int 字节 #3 (i.e. 高字节)
	public static byte getByte3(long i) {
		return (byte) ( (i & 0xFF000000) >> 24 );
	}
	
	
	public static byte getByte4(long i) {
		return (byte) ( (i & 0xFF000000) >> 32 );
	}
	
	public static byte getByte5(long i) {
		return (byte) ( (i & 0xFF000000) >> 40 );
	}
	
	public static byte getByte6(long i) {
		return (byte) ( (i & 0xFF000000) >> 48 );
	}
	
	public static byte getByte7(long i) {
		return (byte) ( (i & 0xFF000000) >> 56 );
	}
	
	
	// 通过四个字节获取一个长整形
	public static int bytesToInt(byte b3, byte b2, byte b1, byte b0) {
		return    ((((int) b3) & 0xFF) << 24) 
				| ((((int) b2) & 0xFF) << 16) 
				| ((((int) b1) & 0xFF) << 8)
				| (((int)  b0) & 0xFF);
	}
	
	public static long bytesToLong(byte b7, byte b6, byte b5, byte b4, byte b3, byte b2, byte b1, byte b0) {
		return 	  ((((long) b7) & 0xFF) << 56) 
				| ((((long) b6) & 0xFF) << 48) 
				| ((((long) b5) & 0xFF) << 40)
	            | ((((long) b4) & 0xFF) << 32) 
	            | ((((long) b3) & 0xFF) << 24) 
	            | ((((long) b2) & 0xFF) << 16)
	            | ((((long) b1) & 0xFF) << 8) 
	            | (((long) b0) & 0xFF);
	}
	
	public static byte[] longToBytes(long i) {
		return new byte[] {
				(byte) ((i >> 56) & 0xFF),  
				(byte) ((i >> 48) & 0xFF),  
				(byte) ((i >> 40) & 0xFF),  
				(byte) ((i >> 32) & 0xFF),  
				(byte) ((i >> 24) & 0xFF),  
			    (byte) ((i >> 16) & 0xFF),     
			    (byte) ((i >> 8) & 0xFF),     
			    (byte) (i & 0xFF)  
		};
	}
	
	public static List<Integer> kmp(byte[] src, byte[] pattern){
		List<Integer> indexs = new ArrayList<Integer>();
		if(src.length <0 || pattern.length > src.length)
			return indexs;
		
		//计算next[]
		int[] next = new int[pattern.length];
        next[0] = 0;
        for(int i = 1,j = 0; i < pattern.length; i++){
            while(j > 0 && pattern[j] != pattern[i]){
                j = next[j - 1];
            }
            if(pattern[i] == pattern[j]){
                j++;
            }
            next[i] = j;
        }
        
        for(int i = 0, j = 0; i < src.length; i++){
            while(j > 0 && src[i] != pattern[j]){
                j = next[j - 1];
            }
            if(src[i] == pattern[j]){
                j++;
            }
            if(j == pattern.length){
            	indexs.add(i-j+1);
            	j = 0;
            }
        }
        return indexs;
    }
	
	public static void main(String[] args) {
		
		long id = Integer.MAX_VALUE;
		
		byte[] pdu = new byte[8];
		
		int i = 0;
		pdu[i++] = ByteUtil.getByte3(id);	//BIG
		pdu[i++] = ByteUtil.getByte2(id);
		pdu[i++] = ByteUtil.getByte1(id);
		pdu[i++] = ByteUtil.getByte0(id);
		
		long newId = bytesToInt(pdu[0], pdu[1], pdu[2], pdu[3]);
		System.out.println( "id=" + id + ", newId=" + newId);
		
		i = 0;
		pdu[i++] = ByteUtil.getByte7(id);	//BIG
		pdu[i++] = ByteUtil.getByte6(id);
		pdu[i++] = ByteUtil.getByte5(id);
		pdu[i++] = ByteUtil.getByte4(id);
		pdu[i++] = ByteUtil.getByte3(id);
		pdu[i++] = ByteUtil.getByte2(id);
		pdu[i++] = ByteUtil.getByte1(id);
		pdu[i++] = ByteUtil.getByte0(id);
		long newId2 = bytesToLong(pdu[0], pdu[1], pdu[2], pdu[3], pdu[4], pdu[5], pdu[6], pdu[7]);
		System.out.println( "id=" + id + ", newId2=" + newId2);
		
		
	}

}
