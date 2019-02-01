package libs;

import java.io.File;
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
    public static DownloadResult downloadToTemp(String uri) throws Exception {
        Objects.requireNonNull(uri);

        return downloadAllToTemp(Collections.singletonList(uri)).get(0);
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

            new NetFile(target, url, -1).load();

            return new DownloadResult(url, target.getAbsolutePath());
        }
    }
}
