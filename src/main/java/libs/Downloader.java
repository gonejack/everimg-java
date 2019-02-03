package libs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public class Downloader {
    private final static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.81 Safari/537.36";
    private final static ExecutorService execSrv = Executors.newFixedThreadPool(3);

    public static List<DownloadResult> downloadAllToTemp(List<String> urls, int timeoutSecForEach)  {
        Objects.requireNonNull(urls);

        LinkedList<DownloadResult> results = new LinkedList<>();
        LinkedList<Future<String>> futures = new LinkedList<>();
        for (String url : urls) {
            results.add(new DownloadResult(url));
            futures.add(execSrv.submit(new DownloadTask(url)));
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

    private static class DownloadTask implements Callable<String> {
        String url;

        DownloadTask(String url) {
            this.url = url;
        }

        @Override
        public String call() throws IOException {
            File target = File.createTempFile("everimg", ".pic");

            URLConnection conn = new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            ReadableByteChannel input = Channels.newChannel(conn.getInputStream());
            FileChannel output = new FileOutputStream(target).getChannel();

            try {
                long total = 0;
                long trans = 0;
                while((trans = output.transferFrom(input, total, 100 * 1024)) > 0) {
                    total += trans;
                }
            }
            catch (Exception e) {
                input.close();
                output.close();
                target.delete();
                throw e;
            }

            return target.getAbsolutePath();
        }
    }
}
