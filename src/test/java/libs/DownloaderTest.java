package libs;

import libs.dl.Downloader;
import libs.dl.Result;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;


public class DownloaderTest {
    final int timeoutSec = 20;

    @Test
    public void downloadAllToTemp() {
        List<String> list = Arrays.asList(
//            "https://img3.appinn.com/images/201904/ms-office-doc-wechat-miniapp.jpg!o"
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif"
//            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
//            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
//            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
//            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
//            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif",
//            "https://wx4.sinaimg.cn/large/729ea5f2gy1fzr22hu2phg207s0b41l3.gif"
        );

        Downloader downloader = new Downloader(5);
        List<Result> results = downloader.downloadAllToTemp(list, timeoutSec, 1);

        for (Result result : results) {
            System.out.println(result.getUrl());
            System.out.println(result.getFile());
            System.out.println(result.getException());
        }
    }
}