open module everimg {
    requires slf4j.api;
    requires slf4j.simple;
    requires jdk.unsupported;
    requires evernote.api;
    requires gson;
    requires jsoup;
    requires jai.imageio.core;
    requires java.desktop;
    requires java.sql;

    exports app;
}