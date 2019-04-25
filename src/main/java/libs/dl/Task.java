package libs.dl;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeoutException;

public class Task implements Runnable {
    static class Config {
        int timeoutSec;
        int retryTimes;
        String userAgent;

        Config(int timeoutSec, int retryTimes, String userAgent) {
            this.timeoutSec = timeoutSec;
            this.retryTimes = retryTimes;
            this.userAgent = userAgent;
        }
    }
    private String source;
    private String target;
    private Config config;
    private Phaser phaser;

    private boolean suc = false;
    private boolean running = false;
    private long startTime = 0;
    private int retryTimes = 0;
    private Result result = null;

    Task(String source, String target, Config config, Phaser phaser) {
        this.source = source;
        this.target = target;
        this.config = config;
        this.phaser = phaser;
    }

    @Override
    public void run() {
        Exception exception = null;

        while (retryTimes++ < config.retryTimes) {
            synchronized (this) {
                running = true;
                startTime = System.currentTimeMillis();
            }

            File file = null;
            try {
                URLConnection connection = new URL(source).openConnection();
                connection.setRequestProperty("User-Agent", config.userAgent);
                connection.setConnectTimeout(config.timeoutSec * 1000);
                connection.setReadTimeout(config.timeoutSec * 1000);

                file = target == null ? File.createTempFile("everimg", ".pic") : new File(target);

                try (
                    ReadableByteChannel input = Channels.newChannel(connection.getInputStream());
                    FileChannel output = new FileOutputStream(file).getChannel();
                ) {
                    long total = 0;
                    long read = 0;
                    while ((read = output.transferFrom(input, total, 100 * 1024)) > 0) {
                        total += read;

                        if (Thread.interrupted()) {
                            throw new TimeoutException(String.format("下载中断，超时配置为[%ss]", config.timeoutSec));
                        }
                    }

                    this.target = file.getAbsolutePath();
                    this.suc = true;

                    break;
                }
            }
            catch (Exception e) {
                if (file != null) {
                    file.delete();
                }
                exception = e;
            }
            finally {
                synchronized (this) {
                    running = false;
                }
            }
        }

        result = new Result(source, target, suc, exception);
        phaser.arrive();
    }

    boolean isTimeout() {
        synchronized (this) {
            return running && startTime > 0 && config.timeoutSec > 0 && System.currentTimeMillis() - startTime > config.timeoutSec * 1000;
        }
    }
    boolean isDone() {
        return !running && result != null;
    }
    Result getResult() {
        return result;
    }
}

