package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.neoforge.FarmUpCraftNeoForge;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class CustomLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean isDebugEnabled = true;
    private static boolean isInfoEnabled = true;
    private static boolean isWarningEnabled = true;
    private static boolean isErrorEnabled = true;

    private final String source;
    private final String className;

    public CustomLogger() {
        this(FarmUpCraftNeoForge.MOD_ID, null);
    }

    public CustomLogger(String className) {
        this(FarmUpCraftNeoForge.MOD_ID, className);
    }

    public CustomLogger(String source, String className) {
        this.source = source;
        this.className = className;
    }

    public void debug(String message) {
        if (isDebugEnabled) {
            LOGGER.info(formatMessage("DEBUG", message));
        }
    }

    public void debug(String message, Object... args) {
        if (isDebugEnabled) {
            LOGGER.info(formatMessage("DEBUG", message), args);
        }
    }

    public void info(String message) {
        if (isInfoEnabled) {
            LOGGER.info(formatMessage("INFO", message));
        }
    }

    public void info(String message, Object... args) {
        if (isInfoEnabled) {
            LOGGER.info(formatMessage("INFO", message), args);
        }
    }

    public void warn(String message) {
        if (isWarningEnabled) {
            LOGGER.info(formatMessage("WARNING", message));
        }
    }

    public void warn(String message, Object... args) {
        if (isWarningEnabled) {
            LOGGER.info(formatMessage("WARNING", message), args);
        }
    }

    public void error(String message) {
        if (isErrorEnabled) {
            LOGGER.info(formatMessage("ERROR", message));
        }
    }

    public void error(String message, Object... args) {
        if (isErrorEnabled) {
            LOGGER.info(formatMessage("ERROR", message), args);
        }
    }

    private String formatMessage(String level, String message) {
        StringBuilder formatted = new StringBuilder("[").append(level).append("] ");
        if (source != null) {
            formatted.append("[").append(source).append("] ");
        }
        if (className != null) {
            formatted.append("[").append(className).append(":")
                    .append(Thread.currentThread().getStackTrace()[3].getLineNumber()).append("] ");
        }
        return formatted.append(message).toString();
    }

    public static void setDebugEnabled(boolean enabled) {
        isDebugEnabled = enabled;
    }

    public static void setInfoEnabled(boolean enabled) {
        isInfoEnabled = enabled;
    }

    public static void setWarningEnabled(boolean enabled) {
        isWarningEnabled = enabled;
    }

    public static void setErrorEnabled(boolean enabled) {
        isErrorEnabled = enabled;
    }
}
