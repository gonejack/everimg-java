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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.*;


public class NoteService extends Service implements Interface {
    private final String token = Conf.mustGet("evernote.token");
    private final boolean yinxiang = Conf.get("evernote.china", false);
    private final boolean sandbox = Conf.get("evernote.sandbox", false);
    private final String noteStateFile = Conf.get("note.service.state.file", "conf/localSyncState.json");

    private LocalSyncState localSyncState;

    private UserStoreClient userStore;
    private NoteStoreClient noteStore;
    private EvernoteService service;
    private NoteFilter noteFilter;
    private NotesMetadataResultSpec noteSpec;

    private static NoteService me = null;
    public static synchronized NoteService init() {
        if (me == null) {
            me = new NoteService();
        }

        return me;
    }
    private NoteService() {
        this.logger = Log.newLogger(NoteService.class);
        this.service = EvernoteService.PRODUCTION;
        if (yinxiang) {
            this.service = EvernoteService.YINXIANG;
        }
        if (sandbox) {
            this.service = EvernoteService.SANDBOX;
        }

        this.noteFilter = new NoteFilter();
        this.noteFilter.setOrder(NoteSortOrder.UPDATED.getValue());

        this.noteSpec = new NotesMetadataResultSpec();
        noteSpec.setIncludeTitle(true);
        noteSpec.setIncludeUpdated(true);
        noteSpec.setIncludeUpdateSequenceNum(true);

        readLocalSyncState();
    }

    @Override
    public void start() {
        logger.debug("开始启动");

        try {
            ClientFactory factory = new ClientFactory(new EvernoteAuth(service, token));

            userStore = factory.createUserStoreClient();
            noteStore = factory.createNoteStoreClient();

            boolean versionOk = userStore.checkVersion("everimg",
                com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR,
                com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR);

            if (versionOk) {
                logger.debug("客户端构建完成");
            }
            else {
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

    /**
     *
     * @return NotesMetadataList|null
     */
    public NotesMetadataList getRecentUpdatedNoteMetas() {
        try {
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
                this.localSyncState.updateTimeStamp = System.currentTimeMillis();
                this.saveLocalSyncState();

                return metadataList;
            }
        }
        catch (Exception e) {
            logger.error("获取更新列表失败", e);
        }

        return null;
    }

    public List<Note> getRecentUpdatedNotes() {
        NotesMetadataList metaList = this.getRecentUpdatedNoteMetas();


        if (metaList == null) {
            logger.info("获取列表为空");

            return Collections.emptyList();
        }
        else {
            List<Note> noteList = new LinkedList<>();

            for (NoteMetadata meta : metaList.getNotes()) {
                Note note = this.getNote(meta);

                if (note == null) {
                    logger.error("无法更新笔记[{}]：获取笔记为空", meta.getTitle());
                }
                else {
                    noteList.add(note);
                }
            }

            return noteList;
        }
    }

    /**
     *
     * @param metadata note
     * @return Note | null
     */
    public Note getNote(NoteMetadata metadata) {
        try {
            return noteStore.getNote(metadata.getGuid(), true, false, true, false);
        }
        catch (Exception e) {
            logger.error("获取笔记[{}]失败: {}", metadata.getTitle(), e);
        }

        return null;
    }

    public void saveNote(Note note) {
        try {
            noteStore.updateNote(note);
        }
        catch (Exception e) {
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
        };

        return changes;
    }
    private int modifyNoteContent(Note note) {
        int changes = 0;

        String content = note.getContent();
        Document doc = Jsoup.parse(content);
        Map<String, Set<String>> toReplace = new HashMap<>();
        for (Element img : doc.select("img")) {
            String src = img.attr("src");

            if (src.startsWith("data")) {
                logger.debug("跳过data图片");
            }
            else {
                toReplace.computeIfAbsent(src, k -> new HashSet<>()).add(img.outerHtml());
            }
        }

        try {
            List<Downloader.DownloadResult> results = Downloader.downloadAllToTemp(new ArrayList<>(toReplace.keySet()));

            for (Downloader.DownloadResult result : results) {
                logger.debug("图片下载结果: {} => {}", result.uri, result.file);

                Optional<Resource> resource = getImageResource(result.file);

                if (resource.isPresent()) {
                    note.addToResources(resource.get());

                    String resourceTag = this.getImageResourceTag(resource.get());

                    for (String imageTag : toReplace.get(result.uri)) {
                        logger.debug("图片替换: {} => {}", imageTag, resourceTag);

                        note.setContent(note.getContent().replace(imageTag, resourceTag));
                    }

                    changes += 1;
                }
                else {
                    logger.error("无法添加图片文件[{}]", result.file);
                }
            }
        }
        catch (Exception e) {
            logger.error("下载出错", e);
        }

        return changes;
    }
    private Optional<Resource> getImageResource(String file) {
        try {
            byte[] body = Files.readAllBytes(Path.of(file));

            BufferedInputStream is = new BufferedInputStream(new ByteArrayInputStream(body));
            String mime = URLConnection.guessContentTypeFromStream(is);

            if (mime != null) {
                BufferedImage readImage = ImageIO.read(new ByteArrayInputStream(body));
                if (readImage != null) {
                    Resource resource = new Resource();

                    Data data = new Data();
                    data.setBody(body);
                    data.setSize(body.length);
                    data.setBodyHash(MessageDigest.getInstance("MD5").digest(body));
                    resource.setData(data);

                    resource.setMime(mime);
                    resource.setHeight((short) readImage.getHeight());
                    resource.setWidth((short) readImage.getWidth());
                    resource.setAttributes(new ResourceAttributes());

                    return Optional.of(resource);
                }
            }
        }
        catch (Exception e) {
            logger.error("文件[{}]无法读取为图片资源: {}", file, e);
        }

        return Optional.empty();
    }
    private String getImageResourceTag(Resource resource) {
        String tagTpl = "<en-media %s />";

        List<String> attrs = new ArrayList<>();

        attrs.add(String.format("type=\"%s\"", resource.getMime()));
        attrs.add(String.format("hash=\"%s\"", helper.bytesToHex(resource.getData().getBodyHash())));

        if (resource.getWidth() > 650) {
            attrs.add(String.format("width=\"%s\"", 650));
        }
        else {
            attrs.add(String.format("height=\"%s\"", resource.getHeight()));
            attrs.add(String.format("width=\"%s\"", resource.getWidth()));
        }

        return String.format(tagTpl, String.join(" ", attrs));
    }

    class LocalSyncState {
        int updateCount;
        long updateTimeStamp;
    }

    private void saveLocalSyncState() {
        try {
            String json = new Gson().toJson(localSyncState);

            logger.debug("保存状态文件[{}]: {}", noteStateFile, json);

            Files.writeString(Path.of(noteStateFile), json, Charset.forName("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        }
        catch (Exception e) {
            logger.error("无法保存状态文件[{}]", e);
        }
    }
    private LocalSyncState readLocalSyncState() {
        try {
            Path path = Path.of(noteStateFile);

            if (Files.exists(path)) {
                String json = Files.readString(path, Charset.forName("utf-8"));

                logger.debug("读取状态文件[{}]: {}", noteStateFile, json);

                return localSyncState = new Gson().fromJson(json, LocalSyncState.class);
            }
            else {
                logger.debug("没有状态文件[{}]", path.toAbsolutePath());
            }
        }
        catch (IOException e) {
            logger.error("无法读取状态文件[{}]", e);
        }

        return localSyncState = new LocalSyncState();
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
