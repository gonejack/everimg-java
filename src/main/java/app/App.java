package app;

import org.slf4j.Logger;
import service.NoteService;
import worker.NoteUpdateWorker;

import java.util.ArrayList;
import java.util.List;

public class App {
    private final static Logger logger = Log.newLogger(App.class);
    private final static List<service.Interface> services = new ArrayList<>();
    private final static List<worker.Interface> workers = new ArrayList<>();

    static {
        Log.init();
        Conf.init();
        App.init();
        Stop.init();
    }
    public static void main(String[] args) {
        App.start();
    }

    private static void init() {
        services.add(NoteService.init());
        workers.add(NoteUpdateWorker.init());
    }
    private static void start() {
        logger.info("开始启动");

        services.forEach(service.Interface::start);
        workers.forEach(worker.Interface::start);

        logger.info("启动完成");
    }
    private static void stop() {
        logger.info("开始退出");

        workers.forEach(worker.Interface::stop);
        services.forEach(service.Interface::stop);

        logger.info("退出完成");
    }

    static class Stop extends Thread {
        static void init() {
            Runtime.getRuntime().addShutdownHook(new Stop());
        }

        @Override
        public void run() {
            Thread.currentThread().setName("stop");

            App.stop();

            Thread.getAllStackTraces().forEach((k, v) -> {logger.debug("剩余线程: {}, {}", k, v);});
        }
    }
}
