package libs;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;


public class DownloaderTest {
    final int timeoutSec = 10;

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

        List<Downloader.DownloadResult> results = new Downloader(5).downloadAllToTemp(list, timeoutSec);

        for (Downloader.DownloadResult result : results) {
            System.out.println(result.getUrl());
            System.out.println(result.getFile());
            System.out.println(result.getException());
        }
    }
}