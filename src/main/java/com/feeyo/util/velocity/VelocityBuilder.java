package com.feeyo.util.velocity;

import java.io.File;
import java.io.StringWriter;
import java.util.Map;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.tools.generic.DateTool;
import org.apache.velocity.tools.generic.MathTool;
import org.apache.velocity.tools.generic.NumberTool;

import com.feeyo.HlsCtx;


public class VelocityBuilder {

    private static VelocityEngine ve;

    private static VelocityEngine engine() {
        if (ve == null) {
            String path = HlsCtx.INSTANCE().getHomePath() + File.separator + "resources" + File.separator + "vm";

            ve = new VelocityEngine();
            ve.setProperty("input.encoding", "UTF-8");
            ve.setProperty("output.encoding", "UTF-8");
            ve.setProperty("resource.loader", "file");
            ve.setProperty("file.resource.loader.cache", "true");
            ve.setProperty("file.resource.loader.modificationCheckInterval", "2");
            ve.setProperty("file.resource.loader.class", "org.apache.velocity.runtime.resource.loader.FileResourceLoader");
            ve.setProperty("file.resource.loader.path", path);
            ve.setProperty(Velocity.RUNTIME_LOG_LOGSYSTEM, new VelocitySlf4JLogSystem());
            
            try {
				ve.init();
			} catch (Exception e) {
			}
        }
        return ve;
    }

    private void mergeTemplate(String name, String encoding, Map<String, Object> model, StringWriter writer) 
    		throws ResourceNotFoundException, ParseErrorException, Exception {
    	
        VelocityContext velocityContext = new VelocityContext(model);
        velocityContext.put("dateSymbol", new DateTool());
        velocityContext.put("numberSymbol", new NumberTool());
        velocityContext.put("mathSymbol", new MathTool());
        
        Template template = engine().getTemplate(name, encoding);
        template.merge(velocityContext, writer);
    }


    public String generate(String templateName, String encoding, Map<String, Object> model) {
        try {
            StringWriter writer = new StringWriter();
            mergeTemplate(templateName, encoding, model, writer);
            writer.close();
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
