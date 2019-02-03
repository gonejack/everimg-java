package libs;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class DownloaderTest {
    final int timeoutSec = 3;

    @Test
    public void downloadTest() {
        Thread t = new Thread(() -> {
            try {
                File outputFile = File.createTempFile("everimg", ".pic");
                String outputPath = outputFile.getAbsolutePath();

                System.out.println(outputPath);

                URLConnection conn = new URL("https://dldir1.qq.com/qqfile/QQforMac/QQ_V6.5.2.dmg").openConnection();
                ReadableByteChannel input = Channels.newChannel(conn.getInputStream());
                FileChannel output = new FileOutputStream(outputFile).getChannel();

                long downloaded = 0;
                while(output.transferFrom(input, outputFile.length(), 10240) > 0) {
                    downloaded = outputFile.length();

                    System.out.println(downloaded);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.start();

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                t.interrupt();
            }
        }, 100000);

        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void downloadAllToTemp() {
        List<String> list = Arrays.asList(
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif"
        );

        List<Downloader.DownloadResult> results = Downloader.downloadAllToTemp(list, timeoutSec);

        for (Downloader.DownloadResult result : results) {
            System.out.println(result.getUrl());
            System.out.println(result.getFile());
            System.out.println(result.getException());
        }
    }
}