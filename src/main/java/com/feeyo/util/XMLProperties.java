package com.feeyo.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides the the ability to use simple XML property files. Each property is
 * in the form X.Y.Z, which would map to an XML snippet of:
 * <pre>
 * &lt;X&gt;
 *     &lt;Y&gt;
 *         &lt;Z&gt;someValue&lt;/Z&gt;
 *     &lt;/Y&gt;
 * &lt;/X&gt;
 * </pre>
 * The XML file is passed in to the constructor and must be readable and
 * writable. Setting property values will automatically persist those value
 * to disk. The file encoding used is UTF-8.
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public class XMLProperties {
	
	private static Logger LOGGER = LoggerFactory.getLogger( XMLProperties.class );

    private Document document;

    /**
     * Parsing the XML file every time we need a property is slow. Therefore,
     * we use a Map to cache property values that are accessed more than once.
     */
    private Map<String, String> propertyCache = new HashMap<>();

    /**
     * Creates a new empty XMLPropertiesTest object.
     *
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties() throws IOException {
       buildDoc(new StringReader("<root />"));
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param fileName the full path the file that properties should be read from
     *                 and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties(String fileName) throws IOException {
        this(Paths.get(fileName));
    }

    /**
     * Loads XML properties from a stream.
     *
     * @param in the input stream of XML.
     * @throws IOException if an exception occurs when reading the stream.
     */
    public XMLProperties(InputStream in) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            buildDoc(reader);
        }
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param file the file that properties should be read from and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    @Deprecated
    public XMLProperties(File file) throws IOException {
        this(file.toPath());
    }

    /**
     * Creates a new XMLPropertiesTest object.
     *
     * @param file the file that properties should be read from and written to.
     * @throws IOException if an error occurs loading the properties.
     */
    public XMLProperties(Path file) throws IOException {
        if (Files.notExists(file)) {
            // Attempt to recover from this error case by seeing if the
            // tmp file exists. It's possible that the rename of the
            // tmp file failed the last time was running,
            // but that it exists now.
            Path tempFile;
            tempFile = file.getParent().resolve(file.getFileName() + ".tmp");
            if (Files.exists(tempFile)) {
            	LOGGER.error("WARNING: " + file.getFileName() + " was not found, but temp file from " +
                        "previous write operation was. Attempting automatic recovery." +
                        " Please check file for data consistency.");
                Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            // There isn't a possible way to recover from the file not
            // being there, so throw an error.
            else {
                throw new NoSuchFileException("XML properties file does not exist: "
                        + file.getFileName());
            }
        }
        // Check read and write privs.
        if (!Files.isReadable(file)) {
            throw new IOException("XML properties file must be readable: " + file.getFileName());
        }
        if (!Files.isWritable(file)) {
            throw new IOException("XML properties file must be writable: " + file.getFileName());
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
             buildDoc(reader);
        }
    }

    /**
     * Returns the value of the specified property.
     *
     * @param name the name of the property to get.
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name) {
    	return getProperty(name, true);
    }

    /**
     * Returns the value of the specified property.
     *
     * @param name the name of the property to get.
     * @param ignoreEmpty Ignore empty property values (return null)
     * @return the value of the specified property.
     */
    public synchronized String getProperty(String name, boolean ignoreEmpty) {
        String value = propertyCache.get(name);
        if (value != null) {
            return value;
        }

        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String aPropName : propName) {
            element = element.element(aPropName);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return null.
                return null;
            }
        }
        // At this point, we found a matching property, so return its value.
        // Empty strings are returned as null.
        value = element.getTextTrim();
        if (ignoreEmpty && "".equals(value)) {
            return null;
        }
        else {
            // Add to cache so that getting property next time is fast.
            propertyCache.put(name, value);
            return value;
        }
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public List<String> getProperties(String name, boolean asList) {
        List<String> result = new ArrayList<>();
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return result;
            }
        }
        // We found matching property, return names of children.
        Iterator<Element> iter = element.elementIterator(propName[propName.length - 1]);
        Element prop;
        String value;
        while (iter.hasNext()) {
        	prop = iter.next();
            // Empty strings are skipped.
            value = prop.getTextTrim();
            if (!"".equals(value)) {
                result.add(value);
            }
        }
        return result;
    }
    
    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @deprecated Retained for backward compatibility. Prefer getProperties(String, boolean)
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public String[] getProperties(String name) {
    	return (String[]) getProperties(name, false).toArray();
    }

    /**
     * Return all values who's path matches the given property
     * name as a String array, or an empty array if the if there
     * are no children. This allows you to retrieve several values
     * with the same property name. For example, consider the
     * XML file entry:
     * <pre>
     * &lt;foo&gt;
     *     &lt;bar&gt;
     *         &lt;prop&gt;some value&lt;/prop&gt;
     *         &lt;prop&gt;other value&lt;/prop&gt;
     *         &lt;prop&gt;last value&lt;/prop&gt;
     *     &lt;/bar&gt;
     * &lt;/foo&gt;
     * </pre>
     * If you call getProperties("foo.bar.prop") will return a string array containing
     * {"some value", "other value", "last value"}.
     *
     * @param name the name of the property to retrieve
     * @return all child property values for the given node name.
     */
    public Iterator getChildProperties(String name) {
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy,
        // stopping one short.
        Element element = document.getRootElement();
        for (int i = 0; i < propName.length - 1; i++) {
            element = element.element(propName[i]);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return Collections.EMPTY_LIST.iterator();
            }
        }
        // We found matching property, return values of the children.
        Iterator<Element> iter = element.elementIterator(propName[propName.length - 1]);
        ArrayList<String> props = new ArrayList<>();
        Element prop;
        String value;
        while (iter.hasNext()) {
        	prop = iter.next();
        	value = prop.getText();
            props.add(value);
        }
        return props.iterator();
    }

    /**
     * Returns the value of the attribute of the given property name or <tt>null</tt>
     * if it doesn't exist.
     *
     * @param name the property name to lookup - ie, "foo.bar"
     * @param attribute the name of the attribute, ie "id"
     * @return the value of the attribute of the given property or <tt>null</tt> if
     *      it doesn't exist.
     */
    public String getAttribute(String name, String attribute) {
        if (name == null || attribute == null) {
            return null;
        }
        String[] propName = parsePropertyName(name);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String child : propName) {
            element = element.element(child);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                break;
            }
        }
        if (element != null) {
            // Get its attribute values
            return element.attributeValue(attribute);
        }
        return null;
    }


    /**
     * Returns a list of names for all properties found in the XML file.
     *
     * @return Names for all properties in the file
     */
    public List<String> getAllPropertyNames() {
    	List<String> result = new ArrayList<>();
    	for (String propertyName : getChildPropertyNamesFor(document.getRootElement(), "")) {
    		if (getProperty(propertyName) != null) {
    			result.add(propertyName);
    		}
    	}
    	return result;
    }
    
    private List<String> getChildPropertyNamesFor(Element parent, String parentName) {
    	List<String> result = new ArrayList<>();
    	for (Element child : (Collection<Element>) parent.elements()) {
    		String childName = new StringBuilder(parentName)
							.append(parentName.isEmpty() ? "" : ".")
							.append(child.getName())
							.toString();
    		if (!result.contains(childName)) {
	    		result.add(childName);
	    		result.addAll(getChildPropertyNamesFor(child, childName));
    		}
    	}
    	return result;
    }

    /**
     * Return all children property names of a parent property as a String array,
     * or an empty array if the if there are no children. For example, given
     * the properties <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>, then
     * the child properties of <tt>X.Y</tt> are <tt>A</tt>, <tt>B</tt>, and
     * <tt>C</tt>.
     *
     * @param parent the name of the parent property.
     * @return all child property values for the given parent.
     */
    public String[] getChildrenProperties(String parent) {
        String[] propName = parsePropertyName(parent);
        // Search for this property by traversing down the XML hierarchy.
        Element element = document.getRootElement();
        for (String aPropName : propName) {
            element = element.element(aPropName);
            if (element == null) {
                // This node doesn't match this part of the property name which
                // indicates this property doesn't exist so return empty array.
                return new String[]{};
            }
        }
        // We found matching property, return names of children.
        List children = element.elements();
        int childCount = children.size();
        String[] childrenNames = new String[childCount];
        for (int i = 0; i < childCount; i++) {
            childrenNames[i] = ((Element)children.get(i)).getName();
        }
        return childrenNames;
    }




    /**
     * Builds the document XML model up based the given reader of XML data.
     * @param in the input stream used to build the xml document
     * @throws java.io.IOException thrown when an error occurs reading the input stream.
     */
    private void buildDoc(Reader in) throws IOException {
        try {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            document = xmlReader.read(in);
        }
        catch (Exception e) {
        	LOGGER.error("Error reading XML properties", e);
            throw new IOException(e.getMessage());
        }
    }

  

    /**
     * Returns an array representation of the given property. 
     * properties are always in the format "prop.name.is.this" which would be
     * represented as an array of four Strings.
     *
     * @param name the name of the property.
     * @return an array representation of the given property.
     */
    private String[] parsePropertyName(String name) {
        List<String> propName = new ArrayList<>(5);
        // Use a StringTokenizer to tokenize the property name.
        StringTokenizer tokenizer = new StringTokenizer(name, ".");
        while (tokenizer.hasMoreTokens()) {
            propName.add(tokenizer.nextToken());
        }
        return propName.toArray(new String[propName.size()]);
    }

}