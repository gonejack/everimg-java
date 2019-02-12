package libs;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Downloader {
    private final static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.81 Safari/537.36";
    private final ExecutorService executeService;

    public Downloader(int parallelism) {
        this.executeService = Executors.newFixedThreadPool(parallelism);
    }
    public List<DownloadResult> downloadAllToTemp(Collection<String> urls, int timeoutSecForEach, int retryTimes)  {
        Objects.requireNonNull(urls);

        List<DownloadTempTask> tasks = new LinkedList<>();
        for (String url : urls) {
            tasks.add(new DownloadTempTask(url, timeoutSecForEach, retryTimes));
        }

        List<DownloadTempTask> running = new LinkedList<>(tasks);
        while (running.size() > 0) {
            try {
                List<DownloadTempTask> done = new LinkedList<>();

                for (DownloadTempTask task : running) {
                    switch (task.getStatus()) {
                        case PENDING:
                            task.start();
                        break;
                        case STARTING:
                        break;
                        case RUNNING:
                            if (task.isTimeout()) {
                                task.interrupt();
                            }
                        break;
                        case ERROR:
                            if (task.shouldRetry()) {
                                task.retry();
                            }
                            else {
                                done.add(task);
                            }
                        break;
                        case FINISHED:
                            done.add(task);
                        break;
                    }
                }

                running.removeAll(done);

                Thread.sleep(200);
            }
            catch (InterruptedException e) {
                break;
            }
        }

        return tasks.stream().map(DownloadTempTask::getResult).collect(Collectors.toList());
    }
    public static class DownloadResult {
        private String url;
        private String file;
        private boolean suc;
        private Exception exception;

        DownloadResult(String url) {
            this.url = url;
            this.file = "none";
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public boolean isSuc() {
            return suc;
        }

        public void setSuc(boolean suc) {
            this.suc = suc;
        }

        public Exception getException() {
            return exception;
        }

        public void setException(Exception exception) {
            this.exception = exception;
        }
    }
    private enum DownloadStatus {
        PENDING("PENDING"),
        STARTING("STARTING"),
        RUNNING("RUNNING"),
        ERROR("ERROR"),
        FINISHED("FINISHED");
        String text;
        DownloadStatus(String text) {
            this.text = text;
        }
        @Override
        public String toString() {
            return this.text;
        }
    }
    private class DownloadTempTask implements Runnable {
        String source;
        String target;
        Exception exception;
        DownloadStatus status;

        ReadableByteChannel input;
        FileChannel output;
        Future future;
        long startTime;
        int timeoutSec;
        int retryTimes;

        DownloadTempTask(String url, int timeoutSec, int retryTimes) {
            this.source = url;
            this.timeoutSec = timeoutSec;
            this.retryTimes = retryTimes;
            this.status = DownloadStatus.PENDING;
        }

        @Override
        public void run() {
            File target = null;
            try {
                this.startTime = System.currentTimeMillis();
                this.status = DownloadStatus.RUNNING;

                URLConnection connection;
                connection = new URL(source).openConnection();
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setConnectTimeout(timeoutSec * 1000);
                connection.setReadTimeout(timeoutSec * 1000);

                input = Channels.newChannel(connection.getInputStream());
                output = new FileOutputStream(target = File.createTempFile("everimg", ".pic")).getChannel();

                long total = 0;
                long read = 0;
                while ((read = output.transferFrom(input, total, 100 * 1024)) > 0) {
                    total += read;

                    if (Thread.interrupted()) {
                        throw new TimeoutException(String.format("下载中断，超时配置为[%ss]", timeoutSec));
                    }
                }

                this.target = target.getAbsolutePath();
                this.status = DownloadStatus.FINISHED;
            }
            catch (Exception e) {
                if (target != null) {
                    target.delete();
                }

                this.exception = e;
                this.status = DownloadStatus.ERROR;
            }
            finally {
                try {
                    if (input != null) {
                        input.close();
                    }
                    if (output != null) {
                        output.close();
                    }
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                this.startTime = 0;
            }
        }

        void start() {
            try {
                this.status = DownloadStatus.STARTING;
                this.future = executeService.submit(this);
            }
            catch (RejectedExecutionException e) {
                this.status = DownloadStatus.ERROR;
                this.exception = e;
            }
        }
        boolean isTimeout() {
            return startTime > 0 && System.currentTimeMillis() > startTime + timeoutSec * 1000;
        }
        void interrupt() {
            future.cancel(true);
        }
        boolean shouldRetry() {
            return retryTimes > 0;
        }
        void retry() {
            retryTimes -= 1;
            start();
        }
        DownloadStatus getStatus() {
            return status;
        }
        DownloadResult getResult() {
            DownloadResult result = new DownloadResult(source);

            switch (status) {
                case ERROR:
                    result.setException(exception);
                case FINISHED:
                    result.setSuc(true);
                    result.setFile(target);
            }

            return result;
        }
    }
}
