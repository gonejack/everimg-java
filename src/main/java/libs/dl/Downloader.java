package libs.dl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.stream.Collectors;

public class Downloader {
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.81 Safari/537.36";
    private final Map<Task, Future<?>> runningTasks = new LinkedHashMap<>();
    private final ExecutorService executeSrv;
    private final Thread taskWatcher = new TaskWatcher();

    public Downloader(int parallelism) {
        this.executeSrv = Executors.newFixedThreadPool(parallelism);
        this.taskWatcher.start();
    }

    public List<Result> downloadAllToTemp(Collection<String> urls, int timeoutSecForEach, int retryTimes) {
        Objects.requireNonNull(urls);

        Phaser phaser = new Phaser();
        phaser.register();

        List<Task> tasks = new LinkedList<>();
        Task.Config config = new Task.Config(timeoutSecForEach, retryTimes, USER_AGENT);
        for (String url : urls) {
            phaser.register();

            Task task = new Task(url, null, config, phaser);

            tasks.add(task);

            runningTasks.put(task, this.executeSrv.submit(task));
        }

        phaser.arriveAndAwaitAdvance();

        return tasks.stream().map(Task::getResult).collect(Collectors.toList());
    }

    public void stop() {
        while (true) {
            synchronized (runningTasks) {
                if (runningTasks.size() == 0) {
                    taskWatcher.interrupt();

                    break;
                }
            }

            try {
                logger.debug("等待下载任务完成");

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();

                break;
            }
        }
    }

    class TaskWatcher extends Thread {
        TaskWatcher() {
            super(TaskWatcher.class.getSimpleName());
        }

        @Override
        public void run() {
            while (true) {
                synchronized (runningTasks) {
                    Iterator<Map.Entry<Task, Future<?>>> it = runningTasks.entrySet().iterator();

                    while (it.hasNext()) {
                        Map.Entry<Task, Future<?>> taskAndFuture = it.next();
                        Task task = taskAndFuture.getKey();
                        if (task.isTimeout()) {
                            taskAndFuture.getValue().cancel(true);
                        }
                        if (task.isDone()) {
                            it.remove();
                        }
                    }
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    logger.debug("退出检查线程 {}", e.getMessage());

                    break;
                }
            }
        }
    }
}