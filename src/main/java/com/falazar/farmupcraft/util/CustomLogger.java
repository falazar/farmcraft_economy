package com.falazar.farmupcraft.util;

import com.falazar.farmupcraft.FarmUpCraft;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class CustomLogger {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final String source;
    private final String className;

    // Flags to enable/disable specific logging levels
    private static boolean isDebugEnabled = true;
    private static boolean isInfoEnabled = true;
    private static boolean isWarningEnabled = true;
    private static boolean isErrorEnabled = true;

    // Constructor with source only
    //public CustomLogger(String source) {
    //    this.source = source;
    //    this.className = null;
    //}

    public CustomLogger() {
        this.source = FarmUpCraft.MODID;
        this.className = null;
    }

    public CustomLogger(String className) {
        this.source = FarmUpCraft.MODID;
        this.className = className;
    }

    // Constructor with source and class name
    public CustomLogger(String source, String className) {
        this.source = source;
        this.className = className;
    }

    // Method to log debug messages
    public void debug(String message) {
        if (isDebugEnabled) {
            LOGGER.info(formatMessage("DEBUG", message)); // Using LOGGER.info for debug messages
        }
    }

    // Method to log formatted debug messages
    public void debug(String message, Object... args) {
        if (isDebugEnabled) {
            LOGGER.info(formatMessage("DEBUG", message), args);
        }
    }

    // Method to log info messages
    public void info(String message) {
        if (isInfoEnabled) {
            LOGGER.info(formatMessage("INFO", message));
        }
    }

    // Method to log formatted info messages
    public void info(String message, Object... args) {
        if (isInfoEnabled) {
            LOGGER.info(formatMessage("INFO", message), args);
        }
    }

    // Method to log warning messages
    public void warn(String message) {
        if (isWarningEnabled) {
            LOGGER.info(formatMessage("WARNING", message)); // Using LOGGER.info for warnings
        }
    }

    // Method to log formatted warning messages
    public void warn(String message, Object... args) {
        if (isWarningEnabled) {
            LOGGER.info(formatMessage("WARNING", message), args); // Using LOGGER.info for warnings
        }
    }

    // Method to log error messages
    public void error(String message) {
        if (isErrorEnabled) {
            LOGGER.info(formatMessage("ERROR", message)); // Using LOGGER.info for errors
        }
    }

    // Method to log formatted error messages
    public void error(String message, Object... args) {
        if (isErrorEnabled) {
            LOGGER.info(formatMessage("ERROR", message), args); // Using LOGGER.info for errors
        }
    }

    // Method to format messages with level and metadata
    private String formatMessage(String level, String message) {
        StringBuilder formattedMessage = new StringBuilder();
        formattedMessage.append("[").append(level).append("] ");
        if (source != null) {
            formattedMessage.append("[").append(source).append("] ");
        }
        if (className != null) {
            formattedMessage.append("[").append(className).append("] ");
        }
        formattedMessage.append(message);
        return formattedMessage.toString();
    }

    // Static methods to enable/disable logging levels
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
