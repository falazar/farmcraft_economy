package com.falazar.farmupcraft.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomLoggerTest {
    private final List<LogEvent> events = new ArrayList<>();
    private final AbstractAppender appender = new AbstractAppender(
            "FarmUpCraftLoggerTest", null, PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
        @Override
        public void append(LogEvent event) {
            events.add(event.toImmutable());
        }
    };
    private final org.apache.logging.log4j.core.Logger logger =
            (org.apache.logging.log4j.core.Logger) LogManager.getLogger(CustomLogger.class);

    @BeforeEach
    void captureLogs() {
        enableAll();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreLogging() {
        logger.removeAppender(appender);
        appender.stop();
        enableAll();
    }

    @Test
    void preservesLegacyLabelsFormattingAndCaller() {
        CustomLogger log = new CustomLogger("ResourceRules");
        log.debug("debug {}", 1);
        log.info("info {}", 2);
        log.warn("warning {}", 3);
        log.error("error {}", 4);

        assertEquals(4, events.size());
        String[] labels = {"DEBUG", "INFO", "WARNING", "ERROR"};
        String[] messages = {"debug 1", "info 2", "warning 3", "error 4"};
        for (int i = 0; i < events.size(); i++) {
            String text = events.get(i).getMessage().getFormattedMessage();
            assertTrue(text.matches("\\[" + labels[i] + "\\] \\[farmupcraft\\] "
                    + "\\[ResourceRules:\\d+\\] " + messages[i]), text);
        }
    }

    @Test
    void supportsDefaultCustomAndAbsentSources() {
        new CustomLogger().info("default");
        new CustomLogger("custom", null).warn("custom");
        new CustomLogger(null, null).error("unscoped");

        assertEquals(List.of("[INFO] [farmupcraft] default", "[WARNING] [custom] custom",
                        "[ERROR] unscoped"),
                events.stream().map(event -> event.getMessage().getFormattedMessage()).toList());
    }

    @Test
    void independentlyGatesEachLegacyLoggingLabel() {
        CustomLogger log = new CustomLogger();
        CustomLogger.setDebugEnabled(false);
        log.debug("hidden");
        log.debug("hidden {}", 1);
        log.info("visible");
        CustomLogger.setInfoEnabled(false);
        CustomLogger.setWarningEnabled(false);
        CustomLogger.setErrorEnabled(false);
        log.info("hidden");
        log.info("hidden {}", 2);
        log.warn("hidden");
        log.warn("hidden {}", 3);
        log.error("hidden");
        log.error("hidden {}", 4);

        assertEquals(1, events.size());
        assertEquals("[INFO] [farmupcraft] visible", events.getFirst().getMessage().getFormattedMessage());
    }

    private static void enableAll() {
        CustomLogger.setDebugEnabled(true);
        CustomLogger.setInfoEnabled(true);
        CustomLogger.setWarningEnabled(true);
        CustomLogger.setErrorEnabled(true);
    }
}
