package libs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.*;

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

    public List<DownloadResult> downloadAllToTemp(Collection<String> urls, int timeoutSecForEach)  {
        Objects.requireNonNull(urls);

        List<Future<String>> futures = new LinkedList<>();
        List<DownloadResult> results = new LinkedList<>();
        for (String url : urls) {
            futures.add(executeService.submit(new DownloadTempTask(url)));
            results.add(new DownloadResult(url));
        }

        Iterator<DownloadResult> resultIterator = results.iterator();
        for (Future<String> future : futures) {
            DownloadResult result = resultIterator.next();
            try {
                String savedFile = future.get(timeoutSecForEach, TimeUnit.SECONDS);
                result.setSuc(true);
                result.setFile(savedFile);
            }
            catch (TimeoutException e) {
                future.cancel(true);
                result.setException(new TimeoutException(String.format("下载超时，超时限制[%ss]", timeoutSecForEach)));
            }
            catch (ExecutionException | InterruptedException e) {
                future.cancel(true);
                result.setException(e);
            }
        }

        return results;
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
            }
            catch (Exception e) {
                target.delete();
                throw e;
            }

            return target.getAbsolutePath();
        }
    }
}
