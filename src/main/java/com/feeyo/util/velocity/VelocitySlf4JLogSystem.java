package com.feeyo.util.velocity;

import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VelocitySlf4JLogSystem implements LogChute {
    private static final Logger LOGGER = LoggerFactory.getLogger(VelocitySlf4JLogSystem.class);

    @Override
    public void init(RuntimeServices rs) throws Exception {
        // Nothing to do
    }

    @Override
    public void log(int level, String message) {
        switch (level) {
            case LogChute.WARN_ID:
                LOGGER.warn(message);
                break;
            case LogChute.INFO_ID:
                LOGGER.info(message);
                break;
            case LogChute.TRACE_ID:
                LOGGER.trace(message);
                break;
            case LogChute.ERROR_ID:
                LOGGER.error(message);
                break;
            case LogChute.DEBUG_ID:
            default:
                LOGGER.debug(message);
                break;
        }
    }

    @Override
    public void log(int level, String message, Throwable t) {
        switch (level) {
            case LogChute.WARN_ID:
                LOGGER.warn(message, t);
                break;
            case LogChute.INFO_ID:
                LOGGER.info(message, t);
                break;
            case LogChute.TRACE_ID:
                LOGGER.warn(message, t);
                break;
            case LogChute.ERROR_ID:
                LOGGER.error(message, t);
                break;
            case LogChute.DEBUG_ID:
            default:
                LOGGER.debug(message, t);
                break;
        }
    }

    @Override
    public boolean isLevelEnabled(int level) {
        switch (level) {
            case LogChute.DEBUG_ID:
                return LOGGER.isDebugEnabled();
            case LogChute.INFO_ID:
                return LOGGER.isInfoEnabled();
            case LogChute.TRACE_ID:
                return LOGGER.isTraceEnabled();
            case LogChute.WARN_ID:
                return LOGGER.isWarnEnabled();
            case LogChute.ERROR_ID:
                return LOGGER.isErrorEnabled();
            default:
                return true;
        }
    }
}