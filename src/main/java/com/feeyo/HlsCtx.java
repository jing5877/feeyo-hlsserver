package com.feeyo;

import java.util.List;
import java.util.Map;

import com.feeyo.cfg.AdsCfg;
import com.feeyo.cfg.ConfigLoader;

public class HlsCtx {
	
	private String home = null;
	
	public Map<String, String> serverMap = null;
	public List<AdsCfg> adsCfgs = null;
	
	final static HlsCtx instance;

	static {
		instance = new HlsCtx();
	}
	
	public static HlsCtx INSTANCE() {
		return instance;
	}
	
	public String getHomePath() {
		return home;
	}
	
	public void init(String pathname) {
		home = pathname;
		System.setProperty("FEEYO_HLS_HOME", pathname);

		serverMap = ConfigLoader.loadServerMap( ConfigLoader.buidCfgAbsPathFor("server.xml") );
		adsCfgs = ConfigLoader.loadAdsCfgs( ConfigLoader.buidCfgAbsPathFor("ads.xml") );
		
	}

	public Map<String, String> getServerMap() {
		return serverMap;
	}

	public List<AdsCfg> getAdsCfgs() {
		return adsCfgs;
	}
	
	
}
