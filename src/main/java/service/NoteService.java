package service;

import app.Conf;
import app.Log;
import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.notestore.*;
import com.evernote.edam.type.*;
import com.google.gson.Gson;
import libs.Downloader;
import libs.ImageFile;
import libs.ImageURL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;

public class NoteService extends Service implements Interface {
    private final static Logger logger = Log.newLogger(NoteService.class);
    private final String token = Conf.mustGet("evernote.token");
    private final boolean yinxiang = Conf.get("evernote.china", false);
    private final boolean sandbox = Conf.get("evernote.sandbox", false);
    private final String syncStateFile = Conf.get("deploy.syncStateFile", "./conf/state.json");
    private final static int downloadParallelism = Conf.get("deploy.download.parallelism", 3);
    private final static int downloadTimeoutSec = Conf.get("deploy.download.timeout", 30);

    private final NoteFilter noteFilter;
    private final NotesMetadataResultSpec noteSpec;
    private final Downloader downloader;

    private EvernoteService service;
    private LocalSyncState localSyncState;
    private UserStoreClient userStore;
    private NoteStoreClient noteStore;

    private static NoteService me = null;

    public static synchronized NoteService init() {
        if (me == null) {
            me = new NoteService();
        }

        return me;
    }

    private NoteService() {
        this.noteFilter = new NoteFilter();
        this.noteFilter.setOrder(NoteSortOrder.UPDATED.getValue());

        this.noteSpec = new NotesMetadataResultSpec();
        this.noteSpec.setIncludeTitle(true);
        this.noteSpec.setIncludeUpdated(true);
        this.noteSpec.setIncludeUpdateSequenceNum(true);

        this.downloader = new Downloader(downloadParallelism);

        this.readLocalSyncState();
    }

    @Override
    public void start() {
        logger.debug("开始启动");

        try {
            service = EvernoteService.PRODUCTION;
            if (yinxiang) {
                service = EvernoteService.YINXIANG;
            }
            if (sandbox) {
                service = EvernoteService.SANDBOX;
            }

            ClientFactory factory = new ClientFactory(new EvernoteAuth(service, token));

            userStore = factory.createUserStoreClient();
            noteStore = factory.createNoteStoreClient();

            boolean versionOk = userStore.checkVersion("everimg",
                com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
                com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);

            if (versionOk) {
                logger.debug("客户端构建完成");
            } else {
                logger.error("客户端版本不兼容");
                System.exit(-1);
            }
        }
        catch (Exception e) {
            logger.error("构建客户端出错 ", e);
            System.exit(-1);
        }

        logger.debug("启动完成");
    }

    @Override
    public void stop() {
        logger.debug("开始退出");

        logger.debug("退出完成");
    }

    public NotesMetadataList getRecentUpdatedNoteMetas() {
        try {
            logger.debug("读取更新信息");

            SyncState syncState = noteStore.getSyncState();
            int updateCount = syncState.getUpdateCount();
            if (updateCount > this.localSyncState.updateCount) {
                NotesMetadataList metadataList = noteStore.findNotesMetadata(this.noteFilter, 0, 100, this.noteSpec);

                List<NoteMetadata> notes = new LinkedList<>();
                for (NoteMetadata note : metadataList.getNotes()) {
                    if (note.getUpdated() > this.localSyncState.updateTimeStamp) {
                        notes.add(note);
                    }
                }
                metadataList.setNotes(notes);

                this.localSyncState.updateCount = updateCount;
                this.localSyncState.updateTimeStamp = syncState.getCurrentTime();
                this.saveLocalSyncState();

                return metadataList;
            }
        }
        catch (Exception e) {
            logger.error("获取更新信息失败", e);
        }

        return null;
    }

    public List<Note> getRecentUpdatedNotes() throws InterruptedException {
        NotesMetadataList metaList = this.getRecentUpdatedNoteMetas();

        if (metaList == null) {
            return Collections.emptyList();
        }
        else {
            List<Note> noteList = new LinkedList<>();

            for (NoteMetadata meta : metaList.getNotes()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("主动取消笔记读取");
                }

                Note note = this.getNote(meta);
                if (note == null) {
                    logger.error("读取笔记[{}]为空", meta.getTitle());
                }
                else {
                    logger.debug("读取笔记[{}]", meta.getTitle());

                    noteList.add(note);
                }
            }

            return noteList;
        }
    }

    public Note getNote(NoteMetadata metadata) {
        try {
            return this.noteStore.getNote(metadata.getGuid(), true, false, true, false);
        }
        catch (Exception e) {
            logger.error("读取笔记[{}]发生错误: {}", metadata.getTitle(), e);
        }

        return null;
    }

    public void saveNote(Note note) {
        try {
            this.noteStore.updateNote(note);
        } catch (Exception e) {
            logger.error("保存笔记[{}]出错", e);
        }
    }

    public int modifyNote(Note note) {
        int changes = 0;

        changes += this.modifyNoteTitle(note);
        changes += this.modifyNoteContent(note);

        return changes;
    }

    private int modifyNoteTitle(Note note) {
        int changes = 0;

        String title = note.getTitle();
        if (title.contains("[图片]")) {
            note.setTitle(title.replace("[图片]", ""));

            changes += 1;
        }

        return changes;
    }

    private int modifyNoteContent(Note note) {
        int changes = 0;

        Document doc = Jsoup.parse(note.getContent());

        Map<String, Set<String>> htmlImageTags = new HashMap<>();
        for (Element imageNode : doc.select("img")) {
            String src = imageNode.attr("src");

            if (src.isEmpty()) {
                logger.warn("HTML标签{}缺失图片地址", imageNode.outerHtml());
            }
            else {
                if (src.startsWith("data")) {
                    logger.debug("跳过data图片");
                }
                else {
                    String hqSrc = ImageURL.getHighQuality(src);

                    if (!src.equals(hqSrc)) {
                        logger.debug("使用更高质量图片 {} => {}", hqSrc, src);

                        src = hqSrc;
                    }

                    htmlImageTags.computeIfAbsent(src, k -> new HashSet<>()).add(imageNode.outerHtml());
                }
            }
        }

        List<Downloader.DownloadResult> results = downloader.downloadAllToTemp(new ArrayList<>(htmlImageTags.keySet()), downloadTimeoutSec);
        for (Downloader.DownloadResult result : results) {
            if (result.isSuc()) {
                logger.debug("图片下载: {} => {}", result.getUrl(), result.getFile());

                Optional<Resource> resource = this.getImageResource(result.getFile());
                if (resource.isPresent()) {
                    note.addToResources(resource.get());

                    String noteImageTag = this.getNoteImageTag(resource.get());
                    for (String htmlImageTag : htmlImageTags.get(result.getUrl())) {
                        logger.debug("图片标签替换: {} => {}", htmlImageTag, noteImageTag);

                        note.setContent(note.getContent().replace(htmlImageTag, noteImageTag));
                    }

                    changes += 1;
                }
                else {
                    logger.error("无效的图片文件[{}]", result.getFile());
                }
            }
            else {
                logger.error("图片下载出错[url={} => file={}]: {}", result.getUrl(), result.getFile(), result.getException());
            }
        }

        return changes;
    }

    private Optional<Resource> getImageResource(String file) {
        try {
            ImageFile imageFile = new ImageFile(file);

            String mime = imageFile.getMIME();
            if (mime != null) {
                Resource resource = new Resource();

                resource.setMime(mime);
                resource.setHeight((short) imageFile.getHeight());
                resource.setWidth((short) imageFile.getWidth());
                resource.setAttributes(new ResourceAttributes());

                byte[] content = imageFile.getContent();
                Data data = new Data();
                data.setBody(content);
                data.setSize(content.length);
                data.setBodyHash(MessageDigest.getInstance("MD5").digest(content));
                resource.setData(data);

                return Optional.of(resource);
            }
        } catch (Exception e) {
            logger.error("文件[{}]无法读取为图片资源: {}", file, e);
        }

        return Optional.empty();
    }

    private String getNoteImageTag(Resource resource) {
        String tagTpl = "<en-media %s />";

        List<String> attrs = new ArrayList<>();

        attrs.add(String.format("type=\"%s\"", resource.getMime()));
        attrs.add(String.format("hash=\"%s\"", helper.bytesToHex(resource.getData().getBodyHash())));

        if (resource.getWidth() > 650) {
            attrs.add(String.format("width=\"%s\"", 650));
        } else {
            attrs.add(String.format("height=\"%s\"", resource.getHeight()));
            attrs.add(String.format("width=\"%s\"", resource.getWidth()));
        }

        return String.format(tagTpl, String.join(" ", attrs));
    }

    private void saveLocalSyncState() {
        try {
            String json = new Gson().toJson(this.localSyncState);

            logger.debug("保存状态文件[{}]: {}", syncStateFile, json);

            Files.writeString(Path.of(syncStateFile), json, Charset.forName("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
        catch (Exception e) {
            logger.error("无法保存状态文件[{}]", e);
        }
    }

    private void readLocalSyncState() {
        this.localSyncState = new LocalSyncState();

        try {
            Path path = Path.of(syncStateFile);

            if (Files.exists(path)) {
                String json = Files.readString(path, Charset.forName("utf-8"));

                logger.debug("读取状态文件[{}]: {}", syncStateFile, json);

                this.localSyncState = new Gson().fromJson(json, LocalSyncState.class);
            } else {
                logger.debug("没有状态文件[{}]", path.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("无法读取状态文件[{}]", e);
        }
    }

    class LocalSyncState {
        int updateCount;
        long updateTimeStamp;
    }

    static class helper {
        static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte hashByte : bytes) {
                int intVal = 0xff & hashByte;
                if (intVal < 0x10) {
                    sb.append('0');
                }
                sb.append(Integer.toHexString(intVal));
            }
            return sb.toString();
        }
    }
}
