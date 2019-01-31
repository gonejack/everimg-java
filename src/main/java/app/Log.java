package app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.util.Optional;

public class Log {
    private final static String LOG_LEVEL = System.getenv("LOG_LEVEL");
    static {
        String logLevel = Optional.ofNullable(LOG_LEVEL).map(s -> s.trim().toLowerCase()).orElse("info");
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel);
    }

    public static <T> Logger newLogger(Class<T> clz) {
        return LoggerFactory.getLogger(clz);
    }
}
