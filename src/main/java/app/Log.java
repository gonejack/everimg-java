package app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;

import java.util.Optional;

public class Log {
    private final static String LOG_LEVEL = Optional.ofNullable(System.getenv("LOG_LEVEL"))
                                                    .map(s -> s.trim().toLowerCase())
                                                    .orElse("info");
    static {
        System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, LOG_LEVEL);
    }

    public static <T> Logger newLogger(Class<T> clz) {
        return LoggerFactory.getLogger(clz);
    }
}
