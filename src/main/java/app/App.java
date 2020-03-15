package app;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class App {
    private final static Logger logger = Log.newLogger(App.class);
    private final List<Component> services = new ArrayList<>();
    private final List<Component> workers = new ArrayList<>();

    static {
        Log.init();
        Config.init();
    }

    private void start() throws Exception {
        logger.info("开始启动");
        for (Component service : services) {
            service.start();
        }
        for (Component worker : workers) {
            worker.start();
        }
        logger.info("启动完成");
    }

    private void stop() {
        logger.info("开始退出");
        for (Component worker : workers) {
            try {
                worker.stop();
            } catch (Exception e) {
                logger.error("关闭出错:", e);
            }
        }
        for (Component service : services) {
            try {
                service.stop();
            } catch (Exception e) {
                logger.error("关闭出错:", e);
            }
        }
        logger.info("退出完成");
    }

    public void addService(Component service) {
        services.add(service);
    }

    public void addWorker(Component worker) {
        workers.add(worker);
    }

    public void boot() throws Exception {
        this.start();

        Runtime.getRuntime().addShutdownHook(new Stop());
    }

    class Stop extends Thread {
        @Override
        public void run() {
            Thread.currentThread().setName("stop");
            App.this.stop();
            Thread.getAllStackTraces().forEach((k, v) -> {
                logger.debug("剩余线程: {}, {}", k, v);
            });
        }
    }

    public interface Component {
        void start() throws Exception;
        void stop() throws Exception;
    }
}
