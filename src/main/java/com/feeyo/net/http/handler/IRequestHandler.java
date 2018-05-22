package com.feeyo.net.http.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

public interface IRequestHandler {
	
	// handler 类型
	public static enum Type {
		NONE(1),
		HLS(2),
		AUTH(3);
			
		private final int value;
		
		Type(int value){
			this.value = value;
		}
		
		public int getValue() {
            return value;
        }
	}
	
	public Type getType();
	
	public boolean isFilted();

	public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception;
	
}
