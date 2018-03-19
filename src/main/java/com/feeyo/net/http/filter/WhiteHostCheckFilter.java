package com.feeyo.net.http.filter;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;

/**
 * 支持主机白名单
 * 
 */
public class WhiteHostCheckFilter implements IFilter {
	
	private static List<String> whitehost =  new ArrayList<String>();			// 具体host的白名单
	private static List<Pattern> whitehostMask = new ArrayList<Pattern>();		// 网段的白名单
	
	private Pattern getMaskPattern(String host) {
		String regex = host.replace(".","\\.").replaceAll("[*]","[0-9]*").replaceAll("%","[0-9]*");
		return Pattern.compile( regex );
	}
	
	public WhiteHostCheckFilter(String... hosts ) {
		
		for(String host: hosts) {

			if ( !whitehost.contains( host ) ) {
				// 网段
				if (host.contains("*") || host.contains("%")) {
					whitehostMask.add(getMaskPattern(host));
				
				} else {
					whitehost.add(host);
				}
			}
		}
	}


	@Override
	public boolean doFilter(ChannelHandlerContext ctx, MessageEvent e) {
		
		boolean isPassed = false;
		
		// 获取client ip
		InetSocketAddress addr = (InetSocketAddress)ctx.getChannel().getRemoteAddress();
        if (addr == null){
            addr = (InetSocketAddress)e.getRemoteAddress();
        }
		String host =  addr.getAddress().getHostAddress();
		
		if ((whitehost == null || whitehost.size() == 0) && 
				(whitehostMask == null || whitehostMask.size() == 0)) {
			isPassed = true;

		} else {
			
			if (whitehostMask.size() > 0) {
				for (Pattern pattern : whitehostMask) {
					if (pattern.matcher(host).find()) {
						isPassed = true;
						break;
					}
				}
			}
			
			if ( !isPassed && whitehost.size() > 0) {
				isPassed = whitehost.contains( host );
			}
		}
		
		return isPassed;
	}

}
