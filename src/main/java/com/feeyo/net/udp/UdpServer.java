package com.feeyo.net.udp;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ConnectionlessBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictor;
import org.jboss.netty.channel.FixedReceiveBufferSizePredictorFactory;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.channel.socket.nio.NioDatagramChannelFactory;


/**
 * UDP Server
 * 
 * @author zhuam
 *  
 */
public class UdpServer {
	
	private ConnectionlessBootstrap bootstrap;
	private DatagramChannel datagramChannel;

	public void startup(int port) {
		
		ChannelFactory channelFactory = new NioDatagramChannelFactory(Executors.newCachedThreadPool());
		
		bootstrap = new ConnectionlessBootstrap( channelFactory );
		bootstrap.setOption("reuseAddress", false);
		bootstrap.setOption("child.reuseAddress", false);		
		bootstrap.setOption("readBufferSize", 1024 * 1024 * 15); //15M
		bootstrap.setOption("writeBufferSize", 1024 * 20);		
		bootstrap.setOption("receiveBufferSizePredictor", new FixedReceiveBufferSizePredictor(1024 * 3));
		bootstrap.setOption("receiveBufferSizePredictorFactory", new FixedReceiveBufferSizePredictorFactory(1024 * 3));
		bootstrap.setPipelineFactory( new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline pipeline = Channels.pipeline();
				pipeline.addLast("handler", new UdpServerChannelHandler());
				return pipeline;
			}			
		});		
		datagramChannel = (DatagramChannel) bootstrap.bind( new InetSocketAddress( port ) );
	}
	
	public void close() {
		
		if ( datagramChannel != null && datagramChannel.isOpen() ) {
			datagramChannel.close();
			datagramChannel = null;
		}
		
		if ( bootstrap != null ) {
			bootstrap.releaseExternalResources();
		}
	}

}
