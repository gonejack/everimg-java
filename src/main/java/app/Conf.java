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
    private final static String env_file = System.getenv("CONFIG_FILE");

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

    public static String get(String key, String def) {
        return props.getProperty(key, def);
    }

    public static int get(String key, int def) {
        return Optional.ofNullable(props.getProperty(key))
                        .map(Integer::valueOf)
                        .orElse(def);
    }

    public static String mustGet(String key) {
        String value = props.getProperty(key);

        if (value == null) {
            logger.error("config {} is null", key);

            System.exit(-1);
        }

        logger.debug("get {} = {}", key, value);

        return value;
    }
}
