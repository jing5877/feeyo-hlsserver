package com.feeyo.hls.m3u8;

public class M3U8 {
	
	private long seq;
	private byte[] buf;
	private long time;
	
	public M3U8(long seq, byte[] buf, long time) {
		this.seq = seq;
		this.buf = buf;
		this.time = time;
	}
	
	public long getSeq() {
		return seq;
	}

	public byte[] getBuf() {
		return buf;
	}

	public long getTime() {
		return time;
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append("seq=").append( seq ).append(", ");
		sb.append("time=").append( time ).append("\r\n");
		sb.append("buf=").append( new String( buf ) ).append("\r\n");
		return sb.toString();
	}
}
