package service;

import app.Conf;
import app.Log;
import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.notestore.*;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.google.gson.Gson;
import libs.Downloader;
import libs.ImageURL;
import model.ImageFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR;
import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR;

public class NoteService extends Service implements Interface {
    private final static Logger logger = Log.newLogger(NoteService.class);
    private final Config config;
    private final Downloader downloader;
    private final ModifyHelper modifyHelper;

    private LocalSyncState localSyncState;
    private UserStoreClient userStore;
    private NoteStoreClient noteStore;
    private static NoteService me;

    private NoteService() {
        this.config = new Config();
        this.downloader = new Downloader(config.downloadParallelism);
        this.modifyHelper = new ModifyHelper();
        this.localSyncState = new LocalSyncState();

        try {
            ClientFactory factory = new ClientFactory(new EvernoteAuth(config.service, config.token));

            userStore = factory.createUserStoreClient();
            noteStore = factory.createNoteStoreClient();

            if (userStore.checkVersion("everimg", EDAM_VERSION_MAJOR, EDAM_VERSION_MINOR)) {
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
    }
    public static synchronized NoteService init() {
        if (me == null) {
            me = new NoteService();
        }

        return me;
    }

    @Override
    public void start() {
        logger.debug("开始启动");
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
            if (syncState.getUpdateCount() > localSyncState.getUpdateCount()) {
                NotesMetadataList metadataList = noteStore.findNotesMetadata(config.noteFilter, 0, 100, config.noteSpec);

                List<NoteMetadata> notes = new LinkedList<>();
                for (NoteMetadata note : metadataList.getNotes()) {
                    if (note.getUpdated() > localSyncState.getUpdateTimeStamp()) {
                        notes.add(note);
                    }
                }
                metadataList.setNotes(notes);

                localSyncState.setState(syncState);
                localSyncState.saveIntoFile();

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

        changes += modifyHelper.modifyNoteTitle(note);
        changes += modifyHelper.modifyNoteContent(note);

        return changes;
    }

    private class Config {
        final String token = Conf.mustGet("evernote.token");
        final boolean yinxiang = Conf.get("evernote.china", false);
        final boolean sandbox = Conf.get("evernote.sandbox", false);
        final String syncStateFile = Conf.get("deploy.syncStateFile", "./conf/state.json");
        final int downloadParallelism = Conf.get("deploy.download.parallelism", 3);
        final int downloadTimeoutSec = Conf.get("deploy.download.timeout", 30);

        final EvernoteService service;
        final NoteFilter noteFilter;
        final NotesMetadataResultSpec noteSpec;

        Config() {
            this.noteFilter = new NoteFilter();
            this.noteFilter.setOrder(NoteSortOrder.UPDATED.getValue());

            this.noteSpec = new NotesMetadataResultSpec();
            this.noteSpec.setIncludeTitle(true);
            this.noteSpec.setIncludeUpdated(true);
            this.noteSpec.setIncludeUpdateSequenceNum(true);

            EvernoteService service = EvernoteService.PRODUCTION;
            if (yinxiang) {
                service = EvernoteService.YINXIANG;
            }
            if (sandbox) {
                service = EvernoteService.SANDBOX;
            }
            this.service = service;
        }
    }
    private class LocalSyncState {
        class Data {
            int updateCount;
            long updateTimeStamp;
        }

        Data data = new Data();

        LocalSyncState() {
            this.readFromFile();
        }
        int getUpdateCount() {
            return data.updateCount;
        }
        long getUpdateTimeStamp() {
            return data.updateTimeStamp;
        }
        void setState(SyncState state) {
            data.updateCount = state.getUpdateCount();
            data.updateTimeStamp = state.getCurrentTime();
        }
        void readFromFile() {
            try {
                Path path = Path.of(config.syncStateFile);

                if (Files.exists(path)) {
                    String json = Files.readString(path, Charset.forName("utf-8"));

                    logger.debug("读取状态文件[{}]: {}", config.syncStateFile, json);

                    data = new Gson().fromJson(json, Data.class);
                }
                else {
                    logger.debug("没有状态文件[{}]", path.toAbsolutePath());
                }
            }
            catch (IOException e) {
                logger.error("无法读取状态文件[{}]", e);
            }
        }
        void saveIntoFile() {
            try {
                String json = new Gson().toJson(data);

                logger.debug("保存状态文件[{}]: {}", config.syncStateFile, json);

                Files.writeString(Path.of(config.syncStateFile), json, Charset.forName("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            }
            catch (Exception e) {
                logger.error("无法保存状态文件[{}]", e);
            }
        }
    }
    private class ModifyHelper {
        int modifyNoteTitle(Note note) {
            int changes = 0;

            String title = note.getTitle();
            if (title.contains("[图片]")) {
                note.setTitle(title.replace("[图片]", ""));

                changes += 1;
            }

            return changes;
        }
        int modifyNoteContent(Note note) {
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

            List<Downloader.DownloadResult> results = downloader.downloadAllToTemp(new ArrayList<>(htmlImageTags.keySet()), config.downloadTimeoutSec);
            for (Downloader.DownloadResult result : results) {
                if (result.isSuc()) {
                    logger.debug("图片下载: {} => {}", result.getUrl(), result.getFile());

                    Optional<String> noteImageTagOpt = getImageTag(result.getFile());
                    if (noteImageTagOpt.isPresent()) {
                        String noteImageTag = noteImageTagOpt.get();

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

        Optional<String> getImageTag(String file) {
            try {
                String tag = new ImageFile(file).toImageResource().toImageTag().toString();

                return Optional.of(tag);
            }
            catch (Exception e) {
                logger.error("文件[{}]无法读取为图片标签: ", e);
            }

            return Optional.empty();
        }
    }
}
