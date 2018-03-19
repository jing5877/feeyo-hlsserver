package com.feeyo.net.http.filter;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequest;


/**
 * filter out the new hls request if without enough bandwidth
 *
 * @author Tr!bf wangyamin@variflight.com
 */
public class HlsTrafficFilter implements IFilter {

    @Override
    public boolean doFilter(ChannelHandlerContext ctx, MessageEvent e) {
        boolean isPassed = false;

        HttpRequest request = (DefaultHttpRequest) e.getMessage();
        String[] path = request.getUri().split("/");
        String requestFile = path[3];

        if (requestFile.endsWith(".ts")) {
            isPassed = true;
        } else {
        	isPassed = true;
        }
        return isPassed;
    }
}
