package com.feeyo.net.http.util;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.*;
import org.jboss.netty.handler.codec.http.cookie.Cookie;
import org.jboss.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Http util
 * 
 * @author tribf wangyamin@variflight.com
 */
public class HttpUtil {
	
    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    private static final String HTTP_MIME_TYPE__VIDEO_MP2T = "video/MP2T";
    private static final String HTTP_MIME_TYPE__TEXT = "text/html";
    private static final String HTTP_MIME_TYPE__MPEG_URL_APPLE = "application/vnd.apple.mpegurl";

    private static Map<String, String> DEFAULT_MIME_TYPES;

    static {
        Map<String, String> mimeTypes = new HashMap<String, String>();
        mimeTypes.put("mp3",HTTP_MIME_TYPE__VIDEO_MP2T);
        mimeTypes.put("ts",HTTP_MIME_TYPE__VIDEO_MP2T);
        mimeTypes.put("m3u8",HTTP_MIME_TYPE__MPEG_URL_APPLE);
        mimeTypes.put("txt", "text/plain");
        mimeTypes.put("css", "text/css");
        mimeTypes.put("csv", "text/csv");
        mimeTypes.put("htm", "text/html");
        mimeTypes.put("html", "text/html");
        mimeTypes.put("js", "application/javascript");
        mimeTypes.put("xhtml", "application/xhtml+xml");
        mimeTypes.put("json", "application/json");
        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("zip", "application/zip");
        mimeTypes.put("tar", "application/x-tar");
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("jpg", "image/jpg");
        mimeTypes.put("tiff", "image/tiff");
        mimeTypes.put("tif", "image/tif");
        mimeTypes.put("png", "image/png");
        mimeTypes.put("svg", "image/svg+xml");
        mimeTypes.put("ico", "image/vnd.microsoft.icon");
        DEFAULT_MIME_TYPES = Collections.unmodifiableMap(mimeTypes);
    }
    
    private static final HttpResponse redirect(HttpResponseStatus status, String url) {
    	 HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status);
    	 response.headers().set(HttpHeaders.Names.LOCATION, url);
    	 response.headers().set(HttpHeaders.Names.CONTENT_LENGTH, 0);
         return response;
    }
    
    public static final HttpResponse redirectFound(String url){
        return redirect(HttpResponseStatus.FOUND, url);
    }
    
    public static final HttpResponse redirectTemporarily(String url){
        return redirect(HttpResponseStatus.TEMPORARY_REDIRECT, url);
    }

    public static final HttpResponse redirectPermanently(String url){
        return redirect(HttpResponseStatus.MOVED_PERMANENTLY, url);
    }

    public static void sendError(ChannelHandlerContext ctx, HttpResponseStatus code) throws InterruptedException {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, code);
        ChannelFuture channelFuture = ctx.getChannel().write(response).sync();
        if (channelFuture.isSuccess()) {
            channelFuture.getChannel().close();
        }
    }

    public static void sendNotModified(ChannelHandlerContext ctx) {
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_MODIFIED);
        ChannelFuture channelFuture = ctx.getChannel().write(response);
        if (channelFuture.isSuccess()) {
            channelFuture.getChannel().close();
        }
    }

    public static void sendNotModified(ChannelHandlerContext ctx, HttpResponse response) {
        ChannelFuture channelFuture = ctx.getChannel().write(response);
        if (channelFuture.isSuccess()) {
            channelFuture.getChannel().close();
        }
    }

    public static String getMimeType (String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return HTTP_MIME_TYPE__TEXT;
        }
        String mimeType = DEFAULT_MIME_TYPES.get(filename.substring(lastDot+1).toLowerCase());
        if (mimeType == null)
            return  HTTP_MIME_TYPE__TEXT;
        else
            return mimeType;
    }

    public static String getDateString(long timemillis) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));
        return dateFormatter.format(new Date(timemillis));
    }

    public static String getCurrentDate() {
        return getDateString(System.currentTimeMillis());
    }

    public static Map<String, String> parseQueryString(String uriQuery) {
        Map<String, String> paramMap = new HashMap<String, String>();
        if (uriQuery.startsWith("?")) {
            uriQuery = uriQuery.substring(1, uriQuery.length());
        }
        String[] querys = uriQuery.split("&");
        for (String query : querys) {
            String[] pair = query.split("=");
            if (pair.length == 2) {
                paramMap.put(pair[0], pair[1]);
            }
        }
        return paramMap;
    }

    public static boolean isCookieExist(HttpHeaders headers, String cookieName){
        String cookiesString = headers.get(HttpHeaders.Names.COOKIE);
        if (cookiesString != null)
        {
            Set<Cookie> cookies = ServerCookieDecoder.STRICT.decode(cookiesString);
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.name())) {
                    return true;
                }
            }
        }

        return false;
    }
}
