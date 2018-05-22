package com.feeyo.net.http.handler;

import java.util.List;
import java.util.Map;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;

public class AuthHandler implements IRequestHandler {

	@Override
	public Type getType() {
		return IRequestHandler.Type.NONE;
	}

	@Override
	public boolean isFilted() {
		return false;
	}

	@Override
	public void execute(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
		
		HttpRequest request = (DefaultHttpRequest) e.getMessage();
	    String uri = request.getUri();
	    
	    // action= login || logout || page
	    // user=
	    // pwd=
	    
	    QueryStringDecoder decoder = new QueryStringDecoder( uri );
	    Map<String, List<String>> parameters = decoder.getParameters();
	    if ( parameters != null && !parameters.isEmpty() ) {
	    	
	    	List<String> actionQ = parameters.get("action");
	    	if ( actionQ != null && !actionQ.isEmpty() ) {
	    		String action = actionQ.get(0);
	    		
	    		if ( "LOGIN".equalsIgnoreCase( action ) ) {
			    	List<String> userQ = parameters.get("user");
			    	List<String> pwdQ = parameters.get("pwd");
			    	
			    	//
			    	if ( userQ != null && !userQ.isEmpty() &&
			    			pwdQ != null && !pwdQ.isEmpty() ) {

			    		//
			    	}
			    	
			    	
	    		} else if ( "LOGOUT".equalsIgnoreCase( action ) ) {
	    			
	    			// logout
	    			
	    		} else if ( "PAGE".equalsIgnoreCase( action ) ) {
	    			
	    		}
	    	}
	    	
	    }
		
	}

}
