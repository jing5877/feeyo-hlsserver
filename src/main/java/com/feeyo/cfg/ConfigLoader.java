package com.feeyo.cfg;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class ConfigLoader {
	
	private static Logger LOGGER = LoggerFactory.getLogger( ConfigLoader.class );

	
	public static String buidCfgAbsPathFor(String fileName) {
		StringBuffer path = new StringBuffer();
		path.append( System.getProperty("FEEYO_HLS_HOME") ).append( File.separator )
		.append( "conf" ).append( File.separator ).append( fileName );
        return path.toString();
	}
	
	
	public static Map<String, String> loadServerMap(String uri) {	
		
		Map<String, String> map = new HashMap<String, String>();
		try {
			Element element = loadXmlDoc(uri).getDocumentElement();
			NodeList children = element.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				Node node = children.item(i);
				if (node instanceof Element) {
					Element e = (Element) node;
					String name = e.getNodeName();
					if ("property".equals(name)) {
						String key = e.getAttribute("name");
						String value = e.getTextContent();
						map.put(key, value);		
					}
				}
			}

		} catch (Exception e) {
			LOGGER.error("load server.xml err " + e);
		}		
		return map;
	}
	
	
	public static List<AdsCfg> loadAdsCfgs(String uri) {

		List<AdsCfg> adsCfgs = new ArrayList<AdsCfg>();
		
		try {

			NodeList nodesElements = loadXmlDoc( uri ).getElementsByTagName("file");
			for (int i = 0; i < nodesElements.getLength(); i++) {
				Node nodesElement = nodesElements.item(i);
				NamedNodeMap nameNodeMap = nodesElement.getAttributes();
				String name = getAttribute(nameNodeMap, "name", null);
				String type = getAttribute(nameNodeMap, "type", null);
				
				AdsCfg adsCfg = new AdsCfg( name, type);
				
				if ( type != null &&  type.equalsIgnoreCase("audio") ) {
					float sampleRate = getFloatAttribute(nameNodeMap, "sampleRate", 8000f);
					int sampleSizeInBits = getIntAttribute(nameNodeMap, "sampleSizeInBits", 16);
					int channels = getIntAttribute(nameNodeMap, "channels", 1);
					
					adsCfg.setSampleRate(sampleRate);
					adsCfg.setSampleSizeInBits(sampleSizeInBits);
					adsCfg.setChannels(channels);
					
					
				} else if ( type != null &&  type.equalsIgnoreCase("video") ) {
					
					int fps = getIntAttribute(nameNodeMap, "fps", 1);
					adsCfg.setFps(fps);
					
				} else if ( type != null &&  type.equalsIgnoreCase("mixed") ) {
					
					float sampleRate = getFloatAttribute(nameNodeMap, "sampleRate", 8000f);
					int sampleSizeInBits = getIntAttribute(nameNodeMap, "sampleSizeInBits", 16);
					int channels = getIntAttribute(nameNodeMap, "channels", 1);
					int fps = getIntAttribute(nameNodeMap, "fps", 1);
					
					adsCfg.setSampleRate(sampleRate);
					adsCfg.setSampleSizeInBits(sampleSizeInBits);
					adsCfg.setChannels(channels);
					adsCfg.setFps(fps);
					
				}
				
				
				adsCfgs.add( adsCfg );
			}
		} catch (Exception e) {
			LOGGER.error("load ads.xml err " + e);
		}
		return adsCfgs;
	}
	
	private static Document loadXmlDoc(String uri) throws Exception {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(uri);
		return doc;
	}

	private static String getAttribute(NamedNodeMap map, String attr, String defaultVal) {
		return getValue(map.getNamedItem(attr), defaultVal);
	}

	private static int getIntAttribute(NamedNodeMap map, String attr, int defaultVal) {
		return getIntValue(map.getNamedItem(attr), defaultVal);
	}
	

	private static float getFloatAttribute(NamedNodeMap map, String attr, float defaultVal) {
		return getFloatValue(map.getNamedItem(attr), defaultVal);
	}

	private static String getValue(Node node, String defaultVal) {
		return node == null ? defaultVal : node.getNodeValue();
	}

	private static int getIntValue(Node node, int defaultVal) {
		return node == null ? defaultVal : Integer.valueOf(node.getNodeValue());
	}
	
	private static float getFloatValue(Node node, float defaultVal) {
		return node == null ? defaultVal : Float.valueOf(node.getNodeValue());
	}
	

	@SuppressWarnings("unused")
	private static List<Node> getChildNodes(Node theNode, String childElName) {
		LinkedList<Node> nodes = new LinkedList<Node>();
		NodeList childs = theNode.getChildNodes();
		for (int j = 0; j < childs.getLength(); j++) {
			if (childs.item(j).getNodeType() == Document.ELEMENT_NODE && childs.item(j).getNodeName().equals(childElName)) {
				nodes.add(childs.item(j));
			}
		}
		return nodes;
	}
	

}
