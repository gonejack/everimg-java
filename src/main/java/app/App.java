package app;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

public class App {
    private final static Logger logger = Log.newLogger(App.class);
    private final List<Callable<Boolean>> starts = new ArrayList<>();
    private final List<Callable<Boolean>> stops = new ArrayList<>();

    static {
        Log.init();
        Config.init();
    }

    private void start() throws Exception {
        logger.info("开始启动");
        for (Callable<Boolean> service : starts) {
            service.call();
        }
        logger.info("启动完成");
    }

    private void stop() {
        logger.info("开始退出");
        for (Callable<Boolean> service : stops) {
            try {
                service.call();
            } catch (Exception e) {
                logger.error("关闭出错:", e);
            }
        }
        logger.info("退出完成");
    }

    @SafeVarargs
    public final void onStart(Callable<Boolean>... fn) {
        starts.addAll(Arrays.asList(fn));
    }
    @SafeVarargs
    public final void onStop(Callable<Boolean>... fn) {
        stops.addAll(Arrays.asList(fn));
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
