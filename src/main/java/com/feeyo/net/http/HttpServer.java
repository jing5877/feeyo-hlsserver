package com.feeyo.net.http;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;

public class HttpServer {

	private ServerBootstrap bootstrap;
	private Channel channel;

	public void startup(int port) {		
		
		final int maxContentLength = 1024 * 1024 * 1024;
		
		ThreadFactory serverBossTF = new NamedThreadFactory("NETTYSERVER-BOSS-");
		ThreadFactory serverWorkerTF = new NamedThreadFactory("NETTYSERVER-WORKER-");
		
		bootstrap = new ServerBootstrap( new NioServerSocketChannelFactory(
				Executors.newCachedThreadPool(serverBossTF),  
				Executors.newCachedThreadPool(serverWorkerTF)));

		bootstrap.setOption("connectTimeoutMillis", 10000);
		bootstrap.setOption("reuseAddress", true); 	// kernel optimization
		bootstrap.setOption("keepAlive", true); 	// for mobiles & our
		bootstrap.setOption("tcpNoDelay", true); 	// better latency over
		
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
			@Override
			public ChannelPipeline getPipeline() throws Exception {
				ChannelPipeline p = Channels.pipeline();
				p.addLast("http-encoder", new HttpResponseEncoder());
				p.addLast("http-decoder", new HttpRequestDecoder());
				p.addLast("http-aggregator", new HttpChunkAggregator( maxContentLength ));
				p.addLast("server-handler", new HttpServerRequestHandler());
				return p;
			}
		});
		channel = bootstrap.bind(new InetSocketAddress(port));

	}

	public void close() {		
		if ( channel != null ) {
			channel.close().awaitUninterruptibly();
		}
		
		if (bootstrap != null) {
			bootstrap.releaseExternalResources();
		}
	}	
	
	static class NamedThreadFactory implements ThreadFactory {

		protected final String id;
		protected final AtomicInteger n = new AtomicInteger(1);

		public NamedThreadFactory(String id) {
			this.id = id;
		}

		@Override
		public Thread newThread(Runnable r) {
			String name = id + "-" + n.getAndIncrement();
			Thread thread = new Thread(r, name);
			thread.setDaemon(true);
			return thread;
		}
	}

}