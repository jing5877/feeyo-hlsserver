package com.feeyo.net.udp;

import java.net.InetSocketAddress;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;

public class UdpClient {

	private ConnectionlessBootstrap bootstrap;
	private Channel channel;
	
	public UdpClient(final UdpClientChannelHandler channelHandler) {
		
		bootstrap = new ConnectionlessBootstrap(new NioDatagramChannelFactory());
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				pipeline.addLast("handler", channelHandler);
				return pipeline;
			}
		});
		
		bootstrap.setOption("localAddress", new InetSocketAddress(10002));
		channel = bootstrap.bind();
	}
	

	public void write(byte[] payload, InetSocketAddress addr) {
		
		ChannelBuffer channelBuffer = ChannelBuffers.buffer( payload.length );
		channelBuffer.writeBytes( payload );
		channel.write(channelBuffer, addr);
	}
	
}
