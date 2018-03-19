package com.feeyo.net.http.handler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.hls.HlsClientSession;
import com.feeyo.hls.HlsLiveStream;
import com.feeyo.hls.HlsLiveStreamMagr;
import com.feeyo.hls.m3u8.M3U8;
import com.feeyo.hls.ts.TsSegment;
import com.feeyo.net.http.util.HttpUtil;
import com.feeyo.util.Versions;

/**
 *  live stream request handler
 * 
 * @author tribf wangyamin@variflight.com
 * @author xuwenfeng
 * @author zhuam
 * 
 */
public class HlsLiveHandler implements IRequestHandler {
	
	private static Logger LOGGER = LoggerFactory.getLogger( HlsLiveHandler.class );
    
    private static final String LIVE_M3U8 = "live.m3u8";
    private static final int LIVE_CACHE_TIME = 1000 * 30;

    @Override
    public Type getType() {
        return IRequestHandler.Type.HLS;
    }

    @Override
    public boolean isFilted() {
        return true;
    }

	@Override
    public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
        HttpRequest request = (DefaultHttpRequest) e.getMessage();

        String uri = request.getUri();
       
        String path = uri.split("[?]")[0].trim();
        String[] pathArray = path.split("/");
        String alias = pathArray[2];
        String requestFile = pathArray[3];
        
        
        // 校验 alias & requestFile
        if ( alias == null || requestFile == null ) {
        	HttpUtil.sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        
        // 根据 alias 获取 live 
        HlsLiveStream liveStream = HlsLiveStreamMagr.INSTANCE().getHlsLiveStreamByAlias( alias );
        if (liveStream == null  ) {
            HttpUtil.sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }
        
        
        // live.m3u8
        if ( requestFile.equals( LIVE_M3U8 ) ) {
        	
            HlsClientSession clientSession = null;
            
            // 提取 sid
            QueryStringDecoder decoder = new QueryStringDecoder(request.getUri());
            List<String> sessionId = decoder.getParameters().get("sid");
            if ( sessionId != null && !sessionId.isEmpty() ) {
            	 clientSession = liveStream.getClientSessionsById( sessionId.get(0) );
            }
            
            LOGGER.info("request m3u8 file,  uri={}, clientSession={}", uri, clientSession);
            
            // 重定向, 解决标识问题
            if ( clientSession == null  ) {
            	
            	
            	clientSession = liveStream.newClientSession();		
                 
            	StringBuffer url = new StringBuffer(50);
            	url.append( path ).append("?sid=").append( clientSession.getId() );
            	
            	LOGGER.info("response redirect, url={}", url.toString());
            	
            	HttpResponse response =  HttpUtil.redirectFound( url.toString() );
    			e.getChannel().write(response);
    			return;
            }
            
            M3U8 m3u8 = clientSession.getM3u8File( requestFile );
            
            DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            byte[] content = m3u8.getBuf();
            long fileMTime = m3u8.getTime();

            response.headers().add(HttpHeaders.Names.SERVER, Versions.SERVER_VERSION);
            response.headers().add(HttpHeaders.Names.DATE, HttpUtil.getDateString(fileMTime));
            response.headers().add(HttpHeaders.Names.CONTENT_TYPE, HttpUtil.getMimeType(requestFile));
            response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.length);
            response.headers().add(HttpHeaders.Names.CACHE_CONTROL, "private, max-age=5");	//
            response.setContent(ChannelBuffers.copiedBuffer(content));
            e.getChannel().write(response);
        	
        // 1...N.ts
        } else {
        	
        	LOGGER.info("request ts file, uri={} ", uri);
        	
        	int tsIndex = Integer.valueOf(requestFile.substring(0, requestFile.indexOf(".ts"))).intValue();
        	
        	// 
        	String ifModifiedSince = request.headers().get( HttpHeaders.Names.IF_MODIFIED_SINCE );
            if ( ifModifiedSince != null && !ifModifiedSince.isEmpty() ) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat(HttpUtil.HTTP_DATE_FORMAT, Locale.US);
                Date mdate = dateFormatter.parse(ifModifiedSince);
                int mdateSec = (int)(mdate.getTime()/1000L);

             	TsSegment tsSegment = liveStream.fetchTsSegment( tsIndex );
             	int fileMTimeSec = tsSegment != null ? (int) ( tsSegment.getCtime() / 1000L) : 0;
                if (mdateSec == fileMTimeSec) {
                    HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
                    response.headers().add(HttpHeaders.Names.CACHE_CONTROL, "max-age=1");
                    HttpUtil.sendNotModified(ctx, response);
                    return;
                }
            }
            
            TsSegment tsSegment = liveStream.fetchTsSegment( tsIndex );
            if ( tsSegment == null ) {
            	HttpUtil.sendError(ctx,HttpResponseStatus.NOT_FOUND);
            	return;
            }
            
         	DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            byte[] content = tsSegment.getData();
            long fileMTime = tsSegment.getCtime();

            response.headers().add(HttpHeaders.Names.SERVER, Versions.SERVER_VERSION);
            response.headers().add(HttpHeaders.Names.DATE, HttpUtil.getDateString(fileMTime));
            response.headers().add(HttpHeaders.Names.CONTENT_TYPE, HttpUtil.getMimeType(requestFile));
            response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, content.length);
            response.headers().add(HttpHeaders.Names.LAST_MODIFIED, HttpUtil.getDateString(fileMTime));
            response.headers().add(HttpHeaders.Names.EXPIRES, HttpUtil.getDateString(fileMTime + LIVE_CACHE_TIME));	// 相对当前的过期时间，以分钟为单位
            response.headers().add(HttpHeaders.Names.CACHE_CONTROL, "max-age="+( LIVE_CACHE_TIME / 1000));
            
            response.setContent(ChannelBuffers.copiedBuffer(content));
            e.getChannel().write(response);
        	
        }
    }
}