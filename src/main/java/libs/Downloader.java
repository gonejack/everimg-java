package libs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Downloader {
    private final static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.81 Safari/537.36";
    private final ExecutorService executeService;

    public Downloader(int parallelism) {
        this.executeService = new ThreadPoolExecutor(parallelism, parallelism, 0L, TimeUnit.MILLISECONDS,
            new SynchronousQueue<>(true),
            (r, executor) -> {
                try {
                    executor.getQueue().put(r);
                }
                catch (InterruptedException e) {
                    throw new RejectedExecutionException("interrupted", e);
                }
            }
        );
    }
    public DownloadResult downloadToTemp(String url, int timeoutSec) {
        Objects.requireNonNull(url);

        Future<String> future = executeService.submit(new DownloadTempTask(url));
        DownloadResult result = new DownloadResult(url);

        try {
            String savedFile = future.get(timeoutSec, TimeUnit.SECONDS);
            result.setSuc(true);
            result.setFile(savedFile);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            result.setException(new TimeoutException(String.format("下载超时，超时限制[%ss]", timeoutSec)));
        }
        catch (ExecutionException | InterruptedException e) {
            future.cancel(true);
            result.setException(e);
        }

        return result;
    }
    public List<DownloadResult> downloadAllToTemp(List<String> urls, int timeoutSecForEach)  {
        Objects.requireNonNull(urls);

        return urls.stream().map(url -> downloadToTemp(url, timeoutSecForEach)).collect(Collectors.toList());
    }
    public static class DownloadResult {
        private String url;
        private String file;
        private boolean suc;
        private Exception exception;

        DownloadResult(String url) {
            this.url = url;
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
    private class DownloadTempTask implements Callable<String> {
        String url;

        DownloadTempTask(String url) {
            this.url = url;
        }

        @Override
        public String call() throws IOException {
            File target = File.createTempFile("everimg", ".pic");
            URLConnection connection = new URL(url).openConnection();
            connection.setRequestProperty("User-Agent", USER_AGENT);

            try (
                ReadableByteChannel input = Channels.newChannel(connection.getInputStream());
                FileChannel output = new FileOutputStream(target).getChannel()
            ) {
                long total = 0;
                long read = 0;
                while ((read = output.transferFrom(input, total, 100 * 1024)) > 0) {
                    total += read;
                }
            } catch (Exception e) {
                target.delete();
                throw e;
            }

            return target.getAbsolutePath();
        }
    }
}
