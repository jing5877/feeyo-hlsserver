package com.feeyo.hls;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

/**
 * Advertisement watch dog
 *
 * @author Tr!bf wangyamin@variflight.com
 *
 */
public class AdsMagr {

	private static Logger LOGGER = LoggerFactory.getLogger(AdsMagr.class);

	private static Map<String, AdItem> adItemCache = new HashMap<String, AdItem>();
	private static final int AV_MAX_LENGTH = 20 * 1024 * 1024;
	
	private static volatile boolean isHasAds = false;
	private String adsType = "audio";
	
	private int aacRawDataPtr = 0;
	private int h264RawDataPtr = 0;
	private int mixedRawDataPtr = 0;
	
	private byte[] aacRawDataBuf = null;
	private byte[] h264RawDataBuf = null;
	private byte[] mixedRawDataBuf = null;
	
	private AacTsSegmenter aacTsSegmenter = new AacTsSegmenter();
	private H264TsSegmenter h264TsSegmenter = new H264TsSegmenter();
	private AacH264MixedTsSegmenter mixedTsSegmenter = new AacH264MixedTsSegmenter();
	

	private Map<String, List<TsSegment>> adsTsSegs = new HashMap<String, List<TsSegment>>();
	
	public AdsMagr() {
		getAdItemCache();
		genAndCacheAdTsSegments();
	}
	
	private void getAdItemCache() {
		
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
						adItemCache.put(adItem.name, adItem);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}
	
	//
	private void genAndCacheAdTsSegments() {
		
		StringBuffer sbf = new StringBuffer();
		sbf.append(System.getProperty("log4jHome")).append(File.separator).append("data");
		
		File adsDirectory = new File(sbf.toString());
		if (adsDirectory.exists() && adsDirectory.isDirectory()) {
			
			//sorted
			Set<String> adnames = adItemCache.keySet();
			List<String> adNamesSorted = new ArrayList<String>(adnames);
			Collections.sort(adNamesSorted, new Comparator<String>() {
				@Override
				public int compare(String o1, String o2) {
					return o1.compareToIgnoreCase(o2);
				}
			});
			
			for(String adname : adnames) {
				
				sbf.setLength(0);
				sbf.append(adsDirectory.getPath()).append(File.separator).append(adname);
				
				AdItem adItem = adItemCache.get(adname);
				File file = new File(sbf.toString());
				if(file.isFile() && file.exists()) {
					
					InputStream in = null;
					try {
						in = new FileInputStream(file);
						byte[] adRawData = new byte[(int) file.length()];
						in.read(adRawData, 0, adRawData.length);
						
						switch(adItem.type) {
						
						case "audio":
							if(aacRawDataBuf == null)
								aacRawDataBuf = new byte[AV_MAX_LENGTH];
							
							aacTsSegmenter.initialize(adItem.sampleRate, adItem.sampleSizeInBits, adItem.channels, adItem.fps);
							System.arraycopy(adRawData, 0, aacRawDataBuf, aacRawDataPtr, adRawData.length);
							aacRawDataPtr += adRawData.length;
							break;
							
						case "video":
							if(h264RawDataBuf == null)
								h264RawDataBuf = new byte[AV_MAX_LENGTH];
							
							h264TsSegmenter.initialize(adItem.sampleRate, adItem.sampleSizeInBits, adItem.channels, adItem.fps);
							System.arraycopy(adRawData, 0, h264RawDataBuf, h264RawDataPtr, adRawData.length);
							h264RawDataPtr += adRawData.length;
							break;
							
						case "mixed":
							if(mixedRawDataBuf == null)
								mixedRawDataBuf = new byte[AV_MAX_LENGTH];
							
							mixedTsSegmenter.initialize(adItem.sampleRate, adItem.sampleSizeInBits, adItem.channels, adItem.fps);
							System.arraycopy(adRawData, 0, mixedRawDataBuf, mixedRawDataPtr, adRawData.length);
							mixedRawDataPtr += adRawData.length;
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
			
			// cache aac ts
			if(aacRawDataBuf != null) {
				
				List<TsSegment> aacTsSegs = new ArrayList<TsSegment>();
				aacTsSegmenter = new AacTsSegmenter();
				aacTsSegmenter.setPts(aacTsSegmenter.getPtsIncPerFrame() * 3); // 3 ： TS_PES_AU_NUM
				int rawDataPtr = 0;
				int tsNum = aacTsSegmenter.calcTsNum(aacRawDataPtr);

				byte[] frameBuf = new byte[1024];
				for (int i = 0; i < tsNum; i++) {
					if (rawDataPtr + frameBuf.length < aacRawDataPtr) {
						System.arraycopy(aacRawDataBuf, rawDataPtr, frameBuf, 0, frameBuf.length);
						rawDataPtr += frameBuf.length;
					} else if (rawDataPtr < aacRawDataPtr) {
						Arrays.fill(frameBuf, (byte) 0);
						System.arraycopy(aacRawDataBuf, rawDataPtr, frameBuf, 0, aacRawDataPtr - rawDataPtr);
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
			
			// cache h264 ts
			if(h264RawDataBuf != null) {
				
				List<TsSegment> h264TsSegs = new ArrayList<TsSegment>();
				h264TsSegmenter = new H264TsSegmenter();
				int index = 0;
				
				int ptr = 0;
				while(ptr < h264RawDataPtr) {
					
					int len = ptr + 2048 < h264RawDataPtr ?  2048 : h264RawDataPtr - ptr;
					byte[] dest = new byte[len]; 
					System.arraycopy(h264RawDataBuf, ptr, dest, 0, len);
					byte[]  tsSegment = h264TsSegmenter.getTsBuf(V5PacketType.H264_STREAM, dest);
					if(tsSegment != null)
						h264TsSegs.add(new TsSegment((++index)+".ts", tsSegment, h264TsSegmenter.getTsSegTime(),true));
					ptr += 2048;
				}
				
				adsTsSegs.put("video", h264TsSegs);
			}
			
			if(mixedRawDataBuf != null) {
				List<TsSegment> mixedTsSegs = new ArrayList<TsSegment>();
				// TODO segment
				
				adsTsSegs.put("mixed", mixedTsSegs);
			}
		}
	}
	
	
	public List<TsSegment> getAdsTsSegments() {
		return adsTsSegs.get(adsType);
	}
	
	public String getAdsType() {
		return adsType;
	}

	public void setAdsType(String adsType) {
		this.adsType = adsType;
	}

	private Document loadXmlDoc(String uri) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(uri);
		return doc;
	}

	public void close() {
		
		adItemCache.clear();
		adsTsSegs.clear();
		
		aacRawDataPtr = 0;
		h264RawDataPtr = 0;
		mixedRawDataPtr = 0;
		
		aacRawDataBuf = null;
		h264RawDataBuf = null;
		mixedRawDataBuf = null;
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
	}
	
	public static void main(String[] args) {
		
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
		
		AdsMagr adsMagr = new AdsMagr();
		List<TsSegment> tsSegs;
		
		tsSegs = adsMagr.getAdsTsSegments();
		adsMagr.setAdsType("video");
		tsSegs = adsMagr.getAdsTsSegments();
		if(tsSegs != null)
			System.out.println(tsSegs.size());
	}

}