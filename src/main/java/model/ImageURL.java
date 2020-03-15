package model;

import java.util.Objects;

public class ImageURL {
    private String origin;
    private String hq;

    public ImageURL(String origin) {
        Objects.requireNonNull(origin);

        this.origin = origin;
        this.hq = getHighQuality();
    }

    public String getHighQuality() {
        String url = commonFix(origin);

        if (url.contains(".media.tumblr.com")) {
            return getTumblrHQ(url);
        } else if (url.contains(".126.net")) {
            return getLofterHQ(url);
        } else if (url.contains("sinaimg.cn") || url.contains("sinaimg.com")) {
            return getWeiboHQ(url);
        } else if (url.contains("photo.tuchong.com")) {
            return getTuChongHQ(url);
        }

        return url;
    }

    public boolean hasHighQuality() {
        return !origin.equals(hq);
    }

    private static String commonFix(String url) {
        if (url.startsWith("//")) {
            url = "http:" + url;
        }

        return url.trim().replace(" ", "");
    }

    private static String getTumblrHQ(String url) {
        return url.replaceFirst("_(640|540|500|400|250)\\.", "_1280.");
    }

    private static String getLofterHQ(String url) {
        if (url.contains("?")) {
            url = url.substring(0, url.indexOf("?")) + "?type=jpg";
        }

        return url;
    }

    private static String getWeiboHQ(String url) {
        if (url.matches("^https?:/\\w")) {
            url = url.replaceFirst(":/", "://");
        }

        if (url.contains("sinaimg.cn/woriginal")) {
            url = url.replace("sinaimg.cn/woriginal", "sinaimg.cn/large");
        } else if (url.contains("sinaimg.com/woriginal")) {
            url = url.replace("sinaimg.com/woriginal", "sinaimg.com/large");
        }

        return url;
    }

    private static String getTuChongHQ(String url) {
        if (url.contains("/l/")) {
            url = url.replaceFirst("/l/", "/f/");
        }

        return url;
    }
}
