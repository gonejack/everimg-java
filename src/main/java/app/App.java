package app;

import org.slf4j.Logger;
import service.NoteService;
import sun.misc.Signal;
import sun.misc.SignalHandler;
import worker.NoteUpdateWorker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class App {
    private static Logger logger = Log.newLogger(App.class);
    private static List<service.Interface> services = new ArrayList<>();
    private static List<worker.Interface> workers = new ArrayList<>();

    public static void main(String[] args) {
        Conf.init();
        App.init();
        App.start();
    }

    private static void init() {
        services.add(NoteService.init());
        workers.add(NoteUpdateWorker.init());

        Stopper.init();
    }
    private static void start() {
        logger.debug("开始启动");

        services.forEach(service.Interface::start);
        workers.forEach(worker.Interface::start);

        logger.info("启动完成");
    }
    private static void stop() {
        logger.debug("开始退出");

        workers.forEach(worker.Interface::stop);
        services.forEach(service.Interface::stop);

        Map<String, String> m = Map.ofEntries(
            Map.entry("abc", "def"),
            Map.entry("def", "qq"),
            Map.entry("qzone", "def")
        );

        logger.info("退出完成");
    }

    static class Stopper extends Thread implements SignalHandler {
        static void init() {
            Stopper stopper = new Stopper();

            Signal.handle(new Signal("INT"), stopper);
            Signal.handle(new Signal("TERM"), stopper);
        }

        @Override
        public void handle(Signal signal) {
            logger.info("收到信号: {}", signal.toString());

            Runtime.getRuntime().addShutdownHook(this);

            System.exit(0);
        }

        @Override
        public void run() {
            App.stop();

//            Thread.getAllStackTraces().forEach((k, v) -> {logger.debug("执行线程: {}, {}", k, v);});
        }
    }
}
