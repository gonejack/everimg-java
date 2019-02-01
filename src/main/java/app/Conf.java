package app;

import org.slf4j.Logger;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

public class Conf {
    private static Logger logger = Log.newLogger(Conf.class);
    private static Properties props = new Properties();
    private final static String res_file = "default.properties";
    private final static String env_file = System.getenv("CONF_FILE");

    public static void init() {
        loadFromSystem();
        loadFromFile();
    }
    private static void loadFromSystem() {
        System.getProperties().forEach((k, v) -> props.setProperty((String) k, (String) v));
    }
    private static void loadFromFile() {
        try (InputStream is = Conf.class.getResourceAsStream(res_file)) {
            props.load(is);
        }
        catch (Exception e) {
            logger.trace("error reading file", e);
        }

        if (Optional.ofNullable(env_file).isEmpty()) {
            logger.warn("env CONF_FILE is empty");
        }
        else {
            try (InputStream is = new FileInputStream(env_file)) {
                props.load(is);
            }
            catch (Exception e) {
                logger.trace("error reading file", e);
            }
        }
    }

    private static String get(String key) {
        return props.getProperty(key);
    }
    public static String get(String key, String def) {
        return props.getProperty(key, def);
    }
    public static int get(String key, int def) {
        int val = Optional.ofNullable(get(key)).map(Integer::valueOf).orElse(def);

        logger.debug("get {} = {}", key, val);

        return val;
    }
    public static boolean get(String key, boolean def) {
        boolean val = Optional.ofNullable(get(key)).map(s -> "true".equalsIgnoreCase(s.trim())).orElse(def);

        logger.debug("get {} = {}", key, val);

        return val;
    }
    public static String mustGet(String key) {
        String val = get(key);

        if (val == null) {
            logger.error("config {} is null", key);

            System.exit(-1);
        }
        else {
            logger.debug("get {} = {}", key, val);
        }

        return val;
    }
}
