package com.feeyo.net.http.handler;

import java.nio.charset.Charset;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.util.CharsetUtil;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import com.feeyo.hls.AdsMagr;
import com.feeyo.hls.HlsLiveStreamMagr;
import com.feeyo.hls.HlsLiveStreamType;
import com.feeyo.net.http.util.HlsRpcUtil;

/**
 * Hls service manage
 * 
 * @author zhuam
 *
 */
public class HlsManageHandler implements IRequestHandler {

	@Override
	public Type getType() {
		return IRequestHandler.Type.MANAGE;
	}

	@Override
	public boolean isFilted() {
		return true;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		//
		String reponseTxt = "ERROR";
		
		HttpRequest request = (DefaultHttpRequest) e.getMessage();
		 
		byte[] bytes = null;
        if (request.getContent() instanceof CompositeChannelBuffer) {
            int i = ((CompositeChannelBuffer) request.getContent()).capacity();
            bytes = new byte[i];
            request.getContent().getBytes(0, bytes, 0, i);
        } else {
            bytes = request.getContent().array();
        }
		
		String queryString = new String(bytes, CharsetUtil.UTF_8);	
		
		JSONObject q = JSON.parseObject( queryString );
		int code = q.getInteger("code");
		switch( code ) {
		case HlsRpcUtil.START_CODE: 
			{
				Long streamId = q.getLong("streamId");
				Integer streamType = q.getInteger("streamType");
				List<String> aliasNames = (List<String>)q.get("aliasNames");
				Float sampleRate = q.getFloat("sampleRate");
				Integer sampleSizeInBits = q.getInteger("sampleSizeInBits");
				Integer channels = q.getInteger("channels");
				Integer fps = q.getInteger("fps");
				
				// 参数校验
				if (streamId == null || streamType == null || (aliasNames == null || aliasNames.isEmpty() ) ) {
					break;
					
				} else {
					
					if (  (streamType == HlsLiveStreamType.AAC || streamType == HlsLiveStreamType.PCM ) &&
							(sampleRate == null || sampleSizeInBits == null || channels == null) ) {
						break;
						
					} else if (  (streamType == HlsLiveStreamType.YUV || streamType == HlsLiveStreamType.H264 ) && fps == null ) {
						break;
						
					} else if ( streamType == HlsLiveStreamType.AAC_H264_MIXED &&
							(sampleRate == null || sampleSizeInBits == null || channels == null || fps == null )) {
						break;
					} 
				}
			
				// 
				HlsLiveStreamMagr.INSTANCE().startHlsLiveStream(streamId, streamType, aliasNames, 
						sampleRate, sampleSizeInBits, channels, fps);
				
				reponseTxt = "OK";
			}
			break;
		case HlsRpcUtil.CLOSE_CODE:
			{
				Long streamId = q.getLong("streamId");
				if ( streamId != null ) {
					HlsLiveStreamMagr.INSTANCE().closeHlsLiveStream(streamId);
					reponseTxt = "OK";
				}
			}
			break;
		case HlsRpcUtil.CLOSE_ALL_CODE:
			{
				HlsLiveStreamMagr.INSTANCE().closeAllHlsLiveStream();
				reponseTxt = "OK";
			}
			break;
		case HlsRpcUtil.UPD_ALIAS_CODE:
			{
				Long streamId = q.getLong("streamId");
				List<String> aliasNames = (List<String>)q.get("aliasNames");
				if ( streamId != null && ( aliasNames != null  && !aliasNames.isEmpty()) ) {
					HlsLiveStreamMagr.INSTANCE().updateHlsLiveStreamAliasNamesById(streamId, aliasNames);
					reponseTxt = "OK";
				}
			}
			break;
		case HlsRpcUtil.UPD_ADS_CODE:
			Boolean isHasAds = q.getBoolean("isHasAds");
			if ( isHasAds != null ) {
				AdsMagr.setHasAds(isHasAds);	
				reponseTxt = "OK";
			}
			break;
		}
		
		//
		DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
		response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, reponseTxt.length());
		response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "text/plain;charset=UTF-8"); 
		 
		ChannelBuffer buffer = ChannelBuffers.copiedBuffer(reponseTxt, Charset.defaultCharset());
		response.setContent(buffer);
		
		ChannelFuture channelFuture = ctx.getChannel().write(response);
		if (channelFuture.isSuccess()) {
			channelFuture.getChannel().close();
		}
		
	}

}
