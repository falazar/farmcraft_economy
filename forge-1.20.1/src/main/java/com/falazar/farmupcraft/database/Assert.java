package com.falazar.farmupcraft.database;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class Assert {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void assertEquals(String expected, String actual, String message) {
        if (expected == null && actual != null || !expected.equals(actual)) {
            LOGGER.error("{} Expected: {}, but got: {}", message, expected, actual);
        }
    }

    public static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            LOGGER.error("{} Expected not to be null.", message);
        }
    }

    public static void assertNull(Object obj, String message) {
        if (obj != null) {
            LOGGER.error("{} Expected to be null, but got: {}", message, obj);
        }
    }

    public static void assertThrows(Runnable runnable, String expectedMessage) {
        try {
            runnable.run();
            LOGGER.error("Expected exception was not thrown.");
        } catch (Exception e) {
            if (e.getMessage() == null || !e.getMessage().contains(expectedMessage)) {
                LOGGER.error("Expected exception with message: {}, but got: {}", expectedMessage, e.getMessage());
            }
        }
    }
}
