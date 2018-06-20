package com.feeyo.net.http.handler;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

public interface IRequestHandler {
	
	// handler 类型
	public static enum Type {
		VM(1),
		API(2),
		HLS(3),
		MANAGE(4),
		AUTH(5),
		OTHER(6);
			
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
