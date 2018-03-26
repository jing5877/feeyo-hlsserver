package com.feeyo.hls.ads;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.feeyo.HlsCtx;
import com.feeyo.audio.codec.faac.FaacUtils;
import com.feeyo.cfg.AdsCfg;
import com.feeyo.hls.ts.TsSegment;
import com.feeyo.hls.ts.segmenter.AacH264MixedTsSegmenter;
import com.feeyo.hls.ts.segmenter.AacTsSegmenter;
import com.feeyo.hls.ts.segmenter.H264TsSegmenter;
import com.feeyo.net.udp.packet.V5PacketType;
import com.feeyo.util.Md5;

/**
 * ADS
 *
 */
public class AdsMagr {

	private static Logger LOGGER = LoggerFactory.getLogger(AdsMagr.class);

	private static volatile boolean isHasAds = false;
	
	// md5 ->> segments
	private static Map<String, List<TsSegment>> adsSegs = new HashMap<String, List<TsSegment>>();
	
	
	//
	public static void initialize() {
		
		String adsPath = HlsCtx.INSTANCE().getHomePath() + File.separator + "data";
		
			
		File adsDirectory = new File( adsPath );
		if (adsDirectory.exists() && adsDirectory.isDirectory()) {
			
				List<AdsCfg> adsCfgs = HlsCtx.INSTANCE().getAdsCfgs();
				for(AdsCfg adsCfg: adsCfgs) {
					
					
					String md5 = Md5.md5_32( adsCfg.getType() + adsCfg.getSampleRate() + adsCfg.getSampleSizeInBits() + 
							adsCfg.getChannels() + adsCfg.getFps());
					
					String filePath =  HlsCtx.INSTANCE().getHomePath() + File.separator + "data" + File.separator + adsCfg.getName();
					File file = new File( filePath );
					if(file.isFile() && file.exists()) {
						
						InputStream in = null;
						try {
							in = new FileInputStream(file);
							byte[] adRawData = new byte[(int) file.length()];
							in.read(adRawData, 0, adRawData.length);
							
							switch( adsCfg.getType() ) {
							
							case "audio":
								
								AacTsSegmenter aacTsSegmenter = new AacTsSegmenter();
								aacTsSegmenter.initialize(adsCfg.getSampleRate(), adsCfg.getSampleSizeInBits(), adsCfg.getChannels(), adsCfg.getFps());
								
								// cache aac ts
								if(adRawData != null) {
									
									List<TsSegment> aacTsSegs = new ArrayList<TsSegment>();
									aacTsSegmenter = new AacTsSegmenter();
									aacTsSegmenter.setPts(aacTsSegmenter.getPtsIncPerFrame() * 3); // 3 ： TS_PES_AU_NUM
									int rawDataPtr = 0;
									int tsNum = aacTsSegmenter.calcTsNum(adRawData.length);
	
									byte[] frameBuf = new byte[1024];
									for (int i = 0; i < tsNum; i++) {
										if (rawDataPtr + frameBuf.length < adRawData.length) {
											System.arraycopy(adRawData, rawDataPtr, frameBuf, 0, frameBuf.length);
											rawDataPtr += frameBuf.length;
										} else if (rawDataPtr < adRawData.length) {
											Arrays.fill(frameBuf, (byte) 0);
											System.arraycopy(adRawData, rawDataPtr, frameBuf, 0, adRawData.length - rawDataPtr);
											rawDataPtr += frameBuf.length;
										} else {
											frameBuf = FaacUtils.ZERO_PCM_DATA;
										}
	
										byte[] tsSegment = aacTsSegmenter.getTsBuf(V5PacketType.AAC_STREAM, frameBuf);
										if (tsSegment != null) {
											aacTsSegs.add(new  TsSegment((i+1)+".ts",tsSegment,aacTsSegmenter.getTsSegTime(),true));
										}
									}
									
									adsSegs.put(md5, aacTsSegs);
								}
								
								break;
								
							case "video":
								
								H264TsSegmenter h264TsSegmenter = new H264TsSegmenter();
								h264TsSegmenter.initialize(adsCfg.getSampleRate(), adsCfg.getSampleSizeInBits(), adsCfg.getChannels(), adsCfg.getFps());
								// cache h264 ts
								if(adRawData != null) {
									
									List<TsSegment> h264TsSegs = new ArrayList<TsSegment>();
									h264TsSegmenter = new H264TsSegmenter();
									int index = 0;
									
									int ptr = 0;
									while(ptr < adRawData.length) {
										
										int len = ptr + 2048 < adRawData.length ?  2048 : adRawData.length - ptr;
										byte[] dest = new byte[len]; 
										System.arraycopy(adRawData, ptr, dest, 0, len);
										byte[]  tsSegment = h264TsSegmenter.getTsBuf(V5PacketType.H264_STREAM, dest);
										if(tsSegment != null)
											h264TsSegs.add(new TsSegment((++index)+".ts", tsSegment, h264TsSegmenter.getTsSegTime(),true));
										ptr += 2048;
									}
									
									adsSegs.put(md5, h264TsSegs);
								}
								
								break;
								
							case "mixed":
								AacH264MixedTsSegmenter mixedTsSegmenter = new AacH264MixedTsSegmenter();
								mixedTsSegmenter.initialize(adsCfg.getSampleRate(), adsCfg.getSampleSizeInBits(), adsCfg.getChannels(), adsCfg.getFps());
								if(adRawData != null) {
									List<TsSegment> mixedTsSegs = new ArrayList<TsSegment>();
									// TODO segment
									
									adsSegs.put(md5, mixedTsSegs);
								}
								
								break;
								
							default :
								continue;
							}
							
						} catch (Exception e) {
							LOGGER.error(e.getMessage());
						}finally {
							if(in != null) {
								try {
									in.close();
								} catch (IOException e) {
								}
							}
						}
					}
			}
			
		}
	}
	
	public List<TsSegment> getAdsTsSegments(String type, float sampleRate, int sampleSizeInBits, int channels, int fps) {
		
		String md5Key = Md5.md5_32(type + sampleRate + sampleSizeInBits + channels + fps );
		return adsSegs.get( md5Key );
	}

	public static boolean isHasAds() {
		return isHasAds;
	}

	public static void setHasAds(boolean isHasAds) {
		AdsMagr.isHasAds = isHasAds;
	}

}