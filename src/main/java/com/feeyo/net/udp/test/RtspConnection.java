package com.feeyo.net.udp.test;

import java.io.Closeable;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelException;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.rtsp.RtspHeaders;
import org.jboss.netty.handler.codec.rtsp.RtspRequestEncoder;
import org.jboss.netty.handler.codec.rtsp.RtspResponseDecoder;

public class RtspConnection implements Closeable {
	
	final private String host;
	final private int port;
	
	private String sessionId = null;
	private AtomicLong cseq = new AtomicLong(1);
	private Channel channel;
	private ClientBootstrap bootstrap;
	private ConcurrentHashMap<String, AsynFuture> futures = new ConcurrentHashMap<String, AsynFuture>();
	
	public RtspConnection(String host, int port) {
		super();
		this.host = host;
		this.port = port;
	}
	
	public AsynFuture send(HttpRequest request) {
		
		long seqNo = cseq.getAndIncrement();

		AsynFuture f = new AsynFuture();
		futures.put(String.valueOf(seqNo), f);
		
		if ( channel != null ) {
			request.headers().add(RtspHeaders.Names.CSEQ, seqNo);
			request.headers().add(RtspHeaders.Names.USER_AGENT, "FMS/1.0.0 (Feeyo Streaming Media v1.0)");
			if ( sessionId != null ) {
				request.headers().add(RtspHeaders.Names.SESSION, sessionId);
			}
			
			channel.write(request);
		} else { 
			f.handle(null);
		}
		return f;
	}
	

	
	public void connect() {
		
		final ChannelFactory factory = new NioClientSocketChannelFactory( 
				Executors.newCachedThreadPool(), Executors.newCachedThreadPool() );
		bootstrap = new ClientBootstrap(factory);
		bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
	            @Override
	            public ChannelPipeline getPipeline() throws Exception {
	                ChannelPipeline p = Channels.pipeline();
	                p.addLast("decoder", new RtspResponseDecoder());
	                p.addLast("encoder", new RtspRequestEncoder());
	                p.addLast("handler", new SimpleChannelUpstreamHandler(){
	                	
	                	@Override
	                	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception { 
	                		super.exceptionCaught(ctx, e);
	                		System.out.println(e.getCause().toString());
	                	}
	                	
	                	@Override
	                	public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
	                		
	                		if (e.getMessage() instanceof HttpResponse) {
	                			
	                			HttpResponse response = (HttpResponse) e.getMessage();
	                			
	                			/***
	                			 * Session=ab00012389721
	                			 * Session=ab00012389721;timeout=60
	                			 */
	                			String sessionLine = response.headers().get(RtspHeaders.Names.SESSION);
	                			if ( sessionLine != null ) {
	                				Matcher matcher = Pattern.compile("([^;]+)(.*(timeout=([\\d]+)).*)?").matcher( sessionLine );
	                				if (matcher.matches()) {
	                					sessionId = matcher.group(1);
	                				}
	                			}
	                			
	                			String seqNo = response.headers().get(RtspHeaders.Names.CSEQ);
	                			AsynFuture f = futures.remove(null != seqNo ? seqNo : "");
	                			if ( f != null ) {
	                				f.handle(response);
	                			} else {
	                				throw new ChannelException("Unknown CSEQ[" + seqNo + "]");
	                			}	
	                			
	                		} else {
	                			super.messageReceived(ctx, e);
	                		}	                		
	                	}                	
	                });
	                return p;
	            }
	        });
		
		final ChannelFuture future = bootstrap.connect( new InetSocketAddress(host, port) );
		future.awaitUninterruptibly();
		if ( !future.isSuccess() ) {
			throw new ChannelException(future.getCause());
		}
		channel = future.getChannel();
	}
	
	public void close() {
		
		if ( channel != null ) 
			channel.close();
		
		if ( bootstrap != null )
			bootstrap.getFactory().releaseExternalResources();
		
		if ( futures != null )
			futures.clear();
	}
	
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getSessionId() {
		return sessionId;
	}
	
	
	//------------------------------------
	
	public static final class AsynFuture implements Serializable {
		
		private static final long serialVersionUID = 1L;

		private HttpResponse response;
		private Semaphore lock = new Semaphore(0);

		void handle(HttpResponse response) {
			lock.release();
			this.response = response;
		}

		public HttpResponse get() throws ChannelException {
			return get(3, TimeUnit.SECONDS);			
		}

		public HttpResponse get(long timeout, TimeUnit unit) throws ChannelException {
			try {
				boolean got = lock.tryAcquire(timeout, unit);
				if (got) {
					lock.release();
				}
			} catch (InterruptedException e) {
				handleInterruptedException(e);
			}
			return response;
		}

		private void handleInterruptedException(InterruptedException ex) throws ChannelException {
			throw new ChannelException(ex.getMessage(), ex.getCause());
		}
	}
	
}