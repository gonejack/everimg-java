package libs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Downloader {
    private static ExecutorService downloadService = Executors.newFixedThreadPool(2);

    private static boolean download(String from, File to, int timeoutSec, long size) throws IOException {
        URLConnection conn = new URL(from).openConnection();
        int timeoutMilli = timeoutSec * 1000;
        conn.setConnectTimeout(timeoutMilli / 10);
        conn.setReadTimeout(timeoutMilli);

        ReadableByteChannel input = Channels.newChannel(conn.getInputStream());
        FileChannel output = new FileOutputStream(to.getAbsolutePath()).getChannel();

        output.transferFrom(input, 0, size > 0 ? size : Long.MAX_VALUE);

        return to.exists();
    }
    private static boolean download(String from, File to) throws IOException {
        return download(from, to, 120, 0);
    }

    public static DownloadResult downloadToTemp(String uri) throws Exception {
        Objects.requireNonNull(uri);

        return downloadAllToTemp(Collections.singletonList(uri)).get(0);
    }
    public static List<DownloadResult> downloadAllToTemp(List<String> uris) throws Exception {
        Objects.requireNonNull(uris);

        List<DownloadTask> tasks = new LinkedList<>();
        List<DownloadResult> results = new LinkedList<>();

        for (String src : uris) {
            tasks.add(new DownloadTask(src));
        }

        for (Future<DownloadResult> future : downloadService.invokeAll(tasks)) {
            results.add(future.get());
        }

        return results;
    }

    public static class DownloadResult {
        public final String uri;
        public final String file;

        DownloadResult(String uri, String file) {
            this.uri = uri;
            this.file = file;
        }
    }
    private static class DownloadTask implements Callable<DownloadResult> {
        String url;

        DownloadTask(String url) {
            this.url = url;
        }

        @Override
        public DownloadResult call() throws Exception {
            File target = File.createTempFile("everimg", "");

            download(url, target);

            return new DownloadResult(url, target.getAbsolutePath());
        }
    }
}
