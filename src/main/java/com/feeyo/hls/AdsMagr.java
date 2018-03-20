package com.feeyo.hls;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.feeyo.audio.codec.faac.FaacUtils;
import com.feeyo.hls.ts.TsSegment;
import com.feeyo.hls.ts.segmenter.AacH264MixedTsSegmenter;
import com.feeyo.hls.ts.segmenter.AacTsSegmenter;
import com.feeyo.hls.ts.segmenter.H264TsSegmenter;
import com.feeyo.net.udp.packet.V5PacketType;
import com.feeyo.util.Globals;
import com.feeyo.util.Log4jInitializer;
import com.feeyo.util.Md5;

/**
 * Advertisement watch dog
 *
 * @author Tr!bf wangyamin@variflight.com
 *
 */
public class AdsMagr {

	private static Logger LOGGER = LoggerFactory.getLogger(AdsMagr.class);

	private static Map<String, AdItem> md52AdItem = new HashMap<String, AdItem>();
	
	private static volatile boolean isHasAds = false;
	
	private static Map<String, List<TsSegment>> adsTsSegs = new HashMap<String, List<TsSegment>>();
	
	static {
		
		if(Globals.getHomeDirectory() == null) {
			
			String osName = System.getProperties().getProperty("os.name");
			final String directory;
	
			if (osName.indexOf("Window") >= 0 || osName.indexOf("Mac") >= 0 ) {
				directory = System.getProperty("user.dir");
	
			} else {
				File dir = new File(System.getProperty("user.dir"));
				directory = dir.getParent();
			}
			
			// 设置 LOG4J
			Log4jInitializer.configureAndWatch( directory, "log4j.xml", 30000L);
			
			//app home
			Globals.setHomeDirectory(directory);
			Globals.setConfigName("hls.xml");
		}
		
		initMd52AdItem();
		genAndCacheAdTsSegments();
	}
	
	private static void initMd52AdItem() {
		
		StringBuffer path = new StringBuffer();
		path.append(Globals.getHomeDirectory()).append(File.separator).append("hls.xml");
		
		try {
			Element element = loadXmlDoc(path.toString()).getDocumentElement();
			NodeList ads = element.getElementsByTagName("ads").item(0).getChildNodes();
			for (int i = 0; i < ads.getLength(); i++) {
				Node ad = ads.item(i);
				if (ad instanceof Element) {
					Element e = (Element) ad;
					String name = e.getNodeName();
					if ("file".equals(name)) {
						NamedNodeMap props = e.getAttributes();
						AdItem adItem = new AdItem();
						for (int k = 0; k < props.getLength(); k++) {
							Node prop = props.item(k);
							String nodeName = prop.getNodeName();
							String nodeText = prop.getTextContent();
							Field field = adItem.getClass().getField(nodeName);
							switch(nodeName){
							case "sampleSizeInBits":
							case "channels":
							case "fps":
								int intValue = nodeText == null ? 0 : Integer.parseInt(nodeText);
								field.set(adItem, intValue);
								break;
							
							case "sampleRate":
								float floatValue = nodeText == null ? 0 : Float.parseFloat(nodeText);
								field.set(adItem, floatValue);
								break;
								
							case "name":
							case "type":
								field.set(adItem, nodeText);
								break;
							}
						}
						md52AdItem.put(Md5.md5_32(adItem.toString()), adItem);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}
	
	//
	private static void genAndCacheAdTsSegments() {
		
		StringBuffer sbf = new StringBuffer();
		sbf.append(System.getProperty("log4jHome")).append(File.separator).append("data");
		
		File adsDirectory = new File(sbf.toString());
		if (adsDirectory.exists() && adsDirectory.isDirectory()) {
			
			for(AdItem adItem : md52AdItem.values()) {
				
				sbf.setLength(0);
				sbf.append(adsDirectory.getPath()).append(File.separator).append(adItem.name);
				
				File file = new File(sbf.toString());
				if(file.isFile() && file.exists()) {
					
					InputStream in = null;
					try {
						in = new FileInputStream(file);
						byte[] adRawData = new byte[(int) file.length()];
						in.read(adRawData, 0, adRawData.length);
						
						switch(adItem.type) {
						
						case "audio":
							
							AacTsSegmenter aacTsSegmenter = new AacTsSegmenter();
							aacTsSegmenter.initialize(adItem.sampleRate, adItem.sampleSizeInBits, adItem.channels, adItem.fps);
							
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
								
								adsTsSegs.put("audio", aacTsSegs);
							}
							
							break;
							
						case "video":
							
							H264TsSegmenter h264TsSegmenter = new H264TsSegmenter();
							
							h264TsSegmenter.initialize(adItem.sampleRate, adItem.sampleSizeInBits, adItem.channels, adItem.fps);
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
								
								adsTsSegs.put("video", h264TsSegs);
							}
							
							break;
							
						case "mixed":
							AacH264MixedTsSegmenter mixedTsSegmenter = new AacH264MixedTsSegmenter();
							mixedTsSegmenter.initialize(adItem.sampleRate, adItem.sampleSizeInBits, adItem.channels, adItem.fps);
							if(adRawData != null) {
								List<TsSegment> mixedTsSegs = new ArrayList<TsSegment>();
								// TODO segment
								
								adsTsSegs.put("mixed", mixedTsSegs);
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
		
		if(!isMatchConf(type, sampleRate, sampleSizeInBits, channels, fps)) {
			LOGGER.debug("## Not match the configure of sample param");
			return null;
		}
		
		return adsTsSegs.get(type);
	}
	
	private boolean isMatchConf(String type, float sampleRate, int sampleSizeInBits, int channels, int fps) {
		
		AdItem param = new AdItem();
		param.type = type;
		param.sampleRate = sampleRate;
		param.sampleSizeInBits = sampleSizeInBits;
		param.channels = channels;
		param.fps = fps;
		
		Set<String> md5List = md52AdItem.keySet();
		return md5List == null ? false : md5List.contains(Md5.md5_32(param.toString()));
	}

	private static Document loadXmlDoc(String uri) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(uri);
		return doc;
	}

	public static boolean isHasAds() {
		return isHasAds;
	}

	public static void setHasAds(boolean isHasAds) {
		AdsMagr.isHasAds = isHasAds;
	}
	
	static class AdItem {
		
		public String name;
		public String type;
		public float sampleRate;
		public int sampleSizeInBits;
		public int channels;
		public int fps;
		
		@Override
		public String toString() {
			
			StringBuffer sbf = new StringBuffer();
			switch(type) {
			case "audio":
				return sbf.append("type").append("audio").append("sampleRate").append(sampleRate)
						.append("sampleSizeInBits").append(sampleSizeInBits).append("channels").append(channels).toString();
			case "video":
				return  sbf.append("type").append("video").append("fps").append(fps).toString();
			case "mixed":
				return  sbf.append("type").append("mixed").append("sampleRate").append(sampleRate)
						.append("sampleSizeInBits").append(sampleSizeInBits).append("channels").append(channels).append("fps").append(fps).toString();
			}
			return null;
		}
	}
	
	public static void main(String[] args) {
		
		AdsMagr adsMagr = new AdsMagr();
		List<TsSegment> tsSegs;
		
		tsSegs = adsMagr.getAdsTsSegments("audio", 8000, 16, 1, 25);
		if(tsSegs != null)
			System.out.println(tsSegs.size());
	}

}