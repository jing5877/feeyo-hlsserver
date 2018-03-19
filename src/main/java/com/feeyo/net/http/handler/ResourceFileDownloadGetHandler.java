package com.feeyo.net.http.handler;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureProgressListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.DefaultFileRegion;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedFile;

import com.feeyo.net.http.util.HttpUtil;
import com.feeyo.util.Globals;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.IF_MODIFIED_SINCE;

/**
 * 静态文件下载
 * 
 * @author zhuam
 *
 */
public class ResourceFileDownloadGetHandler implements IRequestHandler {
	
	private static final String READ_ONLY = "r";
	private static final int HTTP_CACHE_SECONDS = 60;

	public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		HttpRequest request = (DefaultHttpRequest) e.getMessage();
		String uri = request.getUri();
		
		final String path = Globals.getHomeDirectory() + File.separator + "resources";
		String fileName = path + uri;

		RandomAccessFile raf;
		try {
			File file = new File(fileName);

			if (file.isHidden() || !file.exists() || !file.isFile()) {
				HttpUtil.sendError(ctx, HttpResponseStatus.NOT_FOUND);
				return;
			}

			String ifModifiedSince = request.headers().get(IF_MODIFIED_SINCE);
			if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
				SimpleDateFormat dateFormatter = new SimpleDateFormat(HttpUtil.HTTP_DATE_FORMAT, Locale.US);
				Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);

				long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
				long fileLastModifiedSeconds = file.lastModified() / 1000;
				if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
					HttpUtil.sendNotModified(ctx);
					return;
				}
			}

			raf = new RandomAccessFile(file, READ_ONLY);
			long fileLength = raf.length();
			long timeMillis = System.currentTimeMillis();
			long expireMillis = timeMillis + HTTP_CACHE_SECONDS * 1000;		
			
			final DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
			response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, fileLength);
			response.headers().add(HttpHeaders.Names.CONTENT_TYPE, HttpUtil.getMimeType(request.getUri()) + "; charset=UTF-8");
			response.headers().add(HttpHeaders.Names.DATE,   HttpUtil.getCurrentDate());
			response.headers().add(HttpHeaders.Names.EXPIRES, HttpUtil.getDateString(expireMillis));						//http 1.0的 Expires & Pragma
			/**
			 * Expires: Mon, 19 Nov 2012 08:40:01 GMT , 指定cache的绝对过期时间，和Cache-Control一起使用时以后者为准。
			 */
			
			response.headers().add(HttpHeaders.Names.CACHE_CONTROL, "private, max-age="+ HTTP_CACHE_SECONDS);	//http 1.1的 Cache-Control
			/**
			 * private : 只有用户端会cache,  public : 用户浏览器和中间proxy都会cache, max-age=xxx : 设置cache的最大存活时间，单位s
			 */
			
			response.headers().add(HttpHeaders.Names.LAST_MODIFIED, HttpUtil.getDateString(file.lastModified()));
			/**
			 * 基于最后修改时间的 Last-Modified
			 */
			
			Channel ch = e.getChannel();
			
			// 写HTTP 初始头部信息
		    ch.write(response);
			
		    // 写内容
		    ChannelFuture writeFuture;
		    if (ch.getPipeline().get(SslHandler.class) != null) {		    	
	            // HTTPS 下不能使用零拷贝
	            writeFuture = ch.write(new ChunkedFile(raf, 0, fileLength, 8192));
	            
		    } else {
		    	// 没有SSL/TLS、COMPRESS 的情况下, 才可使用零拷贝
				final DefaultFileRegion region = new DefaultFileRegion(raf.getChannel(), 0, fileLength);
				writeFuture = ch.write(region);
				writeFuture.addListener(new ChannelFutureProgressListener() {
					@Override
					public void operationComplete(final ChannelFuture future) {
						region.releaseExternalResources();
					}
					@Override
					public void operationProgressed(ChannelFuture future, long amount, long current, long total) {
						//LOGGER.info( String.format("%s: %d / %d (+%d)%n", path, current, total, amount) );
					}
				});
		    }
			
		} catch (Exception e2) {
			HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
			ChannelFuture channelFuture = ctx.getChannel().write(response);
			if (channelFuture.isSuccess()) {
				channelFuture.getChannel().close();
			}
		}
	}
	
	@Override
	public boolean isFilted() {
		return false;
	}

	@Override
	public Type getType() {
		return IRequestHandler.Type.OTHER;
	}
	
}