package com.feeyo.net.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpHeaders.Values;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.net.http.filter.HlsTrafficFilter;
import com.feeyo.net.http.filter.IFilter;
import com.feeyo.net.http.filter.WhiteHostCheckFilter;
import com.feeyo.net.http.handler.HlsLiveHandler;
import com.feeyo.net.http.handler.HlsLivePlayListHandler;
import com.feeyo.net.http.handler.HlsManageHandler;
import com.feeyo.net.http.handler.HlsVodHandler;
import com.feeyo.net.http.handler.IRequestHandler;
import com.feeyo.net.http.handler.IRequestHandler.Type;
import com.feeyo.net.http.handler.ResourceFileDownloadGetHandler;
import com.feeyo.net.http.util.PathTrie;

/**
 * 
 * @author zhuam
 *
 */
public class HttpServerRequestHandler extends SimpleChannelUpstreamHandler {
	
	private static Logger LOGGER = LoggerFactory.getLogger( HttpServerRequestHandler.class );
	
	// 拦截器  (处理器类型    这个类型对应的拦截器list)
	private Map<IRequestHandler.Type, List<IFilter>> filterChain = new ConcurrentHashMap<IRequestHandler.Type, List<IFilter>>();
	
	// REST
    private final PathTrie<IRequestHandler> getHandlers = new PathTrie<IRequestHandler>();
    private final PathTrie<IRequestHandler> postHandlers = new PathTrie<IRequestHandler>();
    
    public HttpServerRequestHandler() {
    	super();    	
    	
    	// 注册处理器
		registerHandler(HttpMethod.GET, "/hls/*/*", new HlsLiveHandler());
		registerHandler(HttpMethod.GET, "/hls/vod/*/*", new HlsVodHandler());
		registerHandler(HttpMethod.GET, "/hls/playlist", new HlsLivePlayListHandler());
		
		registerHandler(HttpMethod.POST, "/hls/manage", new HlsManageHandler());

		// 流控
		registerFilter(new HlsTrafficFilter(), Type.HLS);
		
		// 白名单
		registerFilter(new WhiteHostCheckFilter(), Type.MANAGE);
    }

    private void registerFilter(IFilter filter, IRequestHandler.Type ...types) {
    	for (IRequestHandler.Type type : types) {
    		List<IFilter> filterList = filterChain.get(type);
    		if (filterList == null) {
    			filterList = new ArrayList<IFilter>();
    			filterChain.put(type, filterList);
    		}
    		filterList.add(filter);    	
    	}
    }
    
	private void registerHandler(HttpMethod method, String path, IRequestHandler handler) {
		if (method == HttpMethod.GET) {
			getHandlers.insert(path, handler);

		} else if (method == HttpMethod.POST) {
			postHandlers.insert(path, handler);

		} else {
			throw new RuntimeException("HttpMethod is not supported");
		}
	}
    
    private IRequestHandler getHandler(HttpRequest request) {
    	
    	IRequestHandler handler = null;
    	
    	//解析QueryString    	
		String uriString = request.getUri();
		
		//获取Path
		String path = null;
    	int pathEndPos = uriString.indexOf('?');
		if (pathEndPos < 0) {
			path = uriString;
		} else {
			path = uriString.substring(0, pathEndPos);	
		}
		
		// 获取参数
		Map<String, String> parameters = new HashMap<String, String>();
		if (uriString.startsWith("?")) {
			uriString = uriString.substring(1, uriString.length());
		}
		String[] querys = uriString.split("&");
		for (String query : querys) {
			String[] pair = query.split("=");
			if (pair.length == 2) {								
				try {
					parameters.put(URLDecoder.decode(pair[0], "UTF8"), URLDecoder.decode(pair[1],"UTF8"));
				} catch (UnsupportedEncodingException e) {
					parameters.put(pair[0], pair[1]);
				}
			}
		}

        HttpMethod method = request.getMethod();
        if (method == HttpMethod.GET) {
        	handler = getHandlers.retrieve(path, parameters);
        	
        } else if (method == HttpMethod.POST) {
            	handler = postHandlers.retrieve(path, parameters);
        } 
        return handler;
    }
    
    @Override
	public void messageReceived(final ChannelHandlerContext ctx, final MessageEvent e) throws Exception {

    	processHttpRequest(ctx, e);
    	
	}
    
    private boolean processFilter(ChannelHandlerContext ctx, MessageEvent messageEvent, IRequestHandler requestHandler) {    	
    	List<IFilter> filters = filterChain.get(requestHandler.getType());
		for (IFilter filter : filters) {
			if (filter.doFilter(ctx, messageEvent)) {
				continue;
			} else {
				return false;
			}
		}
		return true;
    }
    
    private void processHttpRequest(ChannelHandlerContext ctx, MessageEvent e) {
    	
    	HttpRequest request = (DefaultHttpRequest) e.getMessage();
    	
    	String uri = request.getUri();
    	
    	IRequestHandler requestHandler = getHandler(request);
		if ( requestHandler != null ) {	
			
			boolean isFilted = requestHandler.isFilted();
			if ( isFilted && !processFilter(ctx, e, requestHandler) ) {

				IRequestHandler.Type type = requestHandler.getType();	
				if ( type == IRequestHandler.Type.VM ) {
					sendRedirect(ctx, "/v1/view/login");
					
				} else {
					HttpResponse response = buildDefaultResponse("", HttpResponseStatus.UNAUTHORIZED);
					sendResponse(ctx, response);
				}
				return;
			}
			
		} else {
			
			// path 路由不成功, 检测是否是静态文件下载
			int lastDot = uri.lastIndexOf('.');
			if (lastDot != -1) {			
				String extension = uri.substring(lastDot + 1).toLowerCase();
				if ( extension != null && extension.length() < 6 ) {
					requestHandler = new ResourceFileDownloadGetHandler();
				}			
			}
		}
		
		if ( requestHandler != null ) {
			try {
				requestHandler.execute(ctx, e);
			} catch (Exception e1) {
				LOGGER.error("http handler err:", e1);
				HttpResponse response = buildErrorResponse("internal error");
				sendResponse(ctx, response);
			}			
		} else {
			HttpResponse response = buildDefaultResponse("", HttpResponseStatus.NOT_FOUND);
			sendResponse(ctx, response);
		}    	
    }
    
    private HttpResponse buildErrorResponse(String errMsg) {
        return buildDefaultResponse(errMsg, HttpResponseStatus.SERVICE_UNAVAILABLE);
    }
    
    private HttpResponse buildDefaultResponse(String msg, HttpResponseStatus status){
    	HttpResponse errorResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
    	errorResponse.setContent( ChannelBuffers.copiedBuffer(msg, Charset.defaultCharset())  );
        return errorResponse;
    }

    
    private void sendRedirect(ChannelHandlerContext ctx, String newUri) {
    	HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FOUND);
        response.headers().set(HttpHeaders.Names.LOCATION, newUri);
        ctx.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    
    private void sendResponse(ChannelHandlerContext ctx, HttpResponse httpResponse){
        boolean close = false;
        ChannelFuture channelFuture = null;
        try {
            channelFuture = ctx.getChannel().write(httpResponse);
        } catch (Exception e) {
        	LOGGER.error("write response fail.", e);
            close = true;
        } finally {
            // close connection
            if (close || httpResponse == null || !Values.KEEP_ALIVE.equals(httpResponse.headers().get(HttpHeaders.Names.CONNECTION))) {
            	if (channelFuture.isSuccess()) {
        			channelFuture.getChannel().close();
        		}
            }
        }
    }
	
}