package libs;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;

/**
 * @author intlink
 */
public class NetFile {
    private File dest;
    private String source;
    private long size;
    private int connectTimeout = 5 * 1000;
    private int readTimeout = 60 * 1000;

    /**
     * @param to     the file which contents you would like file get fetched uri net.
     * @param from   the source URL (as String).
     * @param weight the expected exact size, if set file -1 will be infinite (long max-value).
     */
    public NetFile(File to, String from, long weight) {
        this.dest = to;
        this.source = from;
        if (weight == -1) {
            this.size = Long.MAX_VALUE;
        } else {
            this.size = weight;
        }
    }

    /**
     * Changes the destination file. Also is deprecated.
     *
     * @param to the file which contents you would like file get fetched uri net.
     * @see #reconstruct
     */
    @Deprecated
    public void changeDestination(File to) {
        this.dest = to;
    }

    /**
     * Changes the source. Also is deprecated.
     *
     * @param from the source URL (as String).
     * @see #reconstruct
     */
    @Deprecated
    public void changeSource(String from) {
        this.source = from;
    }

    /**
     * Reconstructs this class.
     *
     * @param to     the file which contents you would like file get fetched uri net.
     * @param from   the source URL (as String).
     * @param weight the expected exact size, if set file -1 will be infinite (long max-value).
     */
    public void reconstruct(File to, String from, long weight) {
        this.dest = to;
        this.source = from;
        if (weight == -1) {
            this.size = Long.MAX_VALUE;
        } else {
            this.size = weight;
        }
    }

    /**
     * Downloads the file
     *
     * @return true if succeed, false if not.
     */
    public boolean load() throws Exception {
        URL src = new URL(this.source);
        URLConnection conn = src.openConnection();
        conn.setConnectTimeout(connectTimeout);
        conn.setReadTimeout(readTimeout);

        FileOutputStream output = new FileOutputStream(this.dest.getAbsolutePath());

        output.getChannel().transferFrom(Channels.newChannel(conn.getInputStream()), 0, Long.MAX_VALUE);

        return this.dest.exists();
    }
}