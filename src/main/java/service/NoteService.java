package service;

import com.evernote.auth.EvernoteAuth;
import com.evernote.auth.EvernoteService;
import com.evernote.clients.ClientFactory;
import com.evernote.clients.NoteStoreClient;
import com.evernote.clients.UserStoreClient;
import com.evernote.edam.notestore.*;
import com.evernote.edam.type.Note;
import com.evernote.edam.type.NoteSortOrder;
import com.google.gson.Gson;
import libs.dl.Downloader;
import libs.dl.Result;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MAJOR;
import static com.evernote.edam.userstore.Constants.EDAM_VERSION_MINOR;

public class NoteService extends Service {
    private final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Config config;
    private final Downloader downloader;
    private final NoteModifyHelper modifyHelper;

    private LocalSyncState localSyncState;
    private UserStoreClient userStore;
    private NoteStoreClient noteStore;

    private AtomicReference<FileWriter> fileWriter = new AtomicReference<>();

    public NoteService() {
        this.config = new Config();
        this.downloader = new Downloader(config.downloadParallelism);
        this.modifyHelper = new NoteModifyHelper();
        this.localSyncState = new LocalSyncState();
    }

    private static NoteService me;
    public static synchronized NoteService init() {
        if (me == null) {
            me = new NoteService();
        }

        return me;
    }
    public static boolean Start() throws Exception {
        me = new NoteService();
        me.start();

        return true;
    }
    public static boolean Stop() throws Exception {
        me.stop();

        return true;
    }

    @Override
    public void start() throws Exception {
        logger.debug("开始启动");

        try {
            ClientFactory factory = new ClientFactory(new EvernoteAuth(config.service, config.token));

            userStore = factory.createUserStoreClient();
            noteStore = factory.createNoteStoreClient();

            if (userStore.checkVersion("everimg", EDAM_VERSION_MAJOR, EDAM_VERSION_MINOR)) {
                logger.debug("客户端构建完成");
            } else {
                throw new Exception("客户端版本不兼容");
            }
        } catch (Exception e) {
            throw new NoteServiceException("构建客户端出错:", e);
        }

        logger.debug("启动完成");
    }

    @Override
    public void stop() throws Exception {
        logger.debug("开始退出");

        downloader.stop();

        logger.debug("退出完成");
    }

    public List<Note> getRecentUpdatedNotes() throws InterruptedException {
        NotesMetadataList metaList = this.getRecentUpdatedNoteMetas();

        if (metaList == null) {
            return Collections.emptyList();
        } else {
            List<Note> noteList = new LinkedList<>();

            for (NoteMetadata meta : metaList.getNotes()) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("主动取消笔记读取");
                }

                Note note = this.getNote(meta);
                if (note == null) {
                    logger.error("读取笔记[{}]为空", meta.getTitle());
                } else {
                    logger.debug("读取笔记[{}]", meta.getTitle());

                    noteList.add(note);
                }
            }

            return noteList;
        }
    }

    private NotesMetadataList getRecentUpdatedNoteMetas() {
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
        } catch (Exception e) {
            logger.error("获取更新信息失败:", e);
        }

        return null;
    }

    public Note getNote(NoteMetadata metadata) {
        try {
            return this.noteStore.getNote(metadata.getGuid(), true, false, true, false);
        } catch (Exception e) {
            logger.error("读取笔记[{}]发生错误: {}", metadata.getTitle(), e);
        }

        return null;
    }

    public void saveNote(Note note) {
        try {
            logger.debug("保存笔记[{}]", note.getTitle());

            this.noteStore.updateNote(note);
        } catch (Exception e) {
            logger.error("保存笔记[{}]出错: {}", note.getTitle(), e.getMessage());
        }
    }

    public int modifyNote(Note note) {
        int changes = 0;

        changes += modifyHelper.modifyTitle(note);
        changes += modifyHelper.modifyContent(note);

        return changes;
    }

    private static class Config {
        final String token = app.Config.mustGet("evernote.token");
        final boolean yinxiang = app.Config.get("evernote.china", false);
        final boolean sandbox = app.Config.get("evernote.sandbox", false);
        final String syncStateFile = app.Config.get("deploy.syncStateFile", "./conf/state.json");
        final int downloadParallelism = app.Config.get("deploy.download.parallelism", 3);
        final int downloadTimeoutSec = app.Config.get("deploy.download.timeoutSeconds", 180);
        final int downloadRetryTimes = app.Config.get("deploy.download.retryTimes", 2);

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
                    String json = Files.readString(path, StandardCharsets.UTF_8);

                    logger.debug("读取状态文件[{}]: {}", config.syncStateFile, json);

                    data = new Gson().fromJson(json, Data.class);
                } else {
                    logger.debug("没有状态文件[{}]", path.toAbsolutePath());
                }
            } catch (IOException e) {
                logger.error("无法读取状态文件", e);
            }
        }

        void saveIntoFile() {
            try {
                String json = new Gson().toJson(data);

                logger.debug("保存状态文件[{}]: {}", config.syncStateFile, json);

                Files.writeString(Path.of(config.syncStateFile), json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception e) {
                logger.error("无法保存状态文件", e);
            }
        }
    }

    private class NoteModifyHelper {
        int modifyTitle(Note note) {
            int changes = 0;

            String title = note.getTitle();
            if (title.contains("[图片]")) {
                note.setTitle(title.replace("[图片]", ""));

                changes += 1;
            }

            return changes;
        }

        int modifyContent(Note note) {
            int changes = 0;

            Document doc = Jsoup.parse(note.getContent());

            Map<String, List<Element>> imageNodes = new LinkedHashMap<>();
            for (Element imageNode : doc.select("img")) {
                String src = imageNode.attr("src");

                if (src.isEmpty()) {
                    logger.warn("HTML标签{}缺失图片地址", imageNode.outerHtml());
                } else {
                    if (src.startsWith("data")) {
                        logger.debug("跳过data图片");
                    } else if (src.startsWith("blob")) {
                        logger.debug("跳过blob图片");
                    } else {
                        model.ImageURL imageURL = new model.ImageURL(src);

                        if (imageURL.hasHighQuality()) {
                            String hqSrc = imageURL.getHighQuality();

                            logger.debug("匹配更高质量图片 {} => {}", src, hqSrc);

                            src = hqSrc;
                        }

                        imageNodes.computeIfAbsent(src, s -> new LinkedList<>()).add(imageNode);
                    }
                }
            }

            List<String> sourceURLs = new ArrayList<>(imageNodes.keySet());
            List<Result> results = downloader.downloadAllToTemp(sourceURLs, config.downloadTimeoutSec, config.downloadRetryTimes);

            for (Result result : results) {
                String src = result.getUrl();
                String file = result.getFile();

                if (result.isSuc()) {
                    logger.debug("图片下载: {} => {}", src, file);

                    Optional<model.ImageResource> imageRes = getNoteImageResource(file);
                    if (imageRes.isPresent()) {
                        model.ImageTag imageTag = imageRes.get().toImageTag();

                        for (Element imageNode : imageNodes.get(src)) {
                            String search = imageNode.outerHtml();
                            String replacement = imageTag.genTag(imageNode);

                            logger.debug("图片标签替换: {} => {}", search, replacement);
                            note.setContent(note.getContent().replace(search, replacement));
                        }

                        note.addToResources(imageRes.get());

                        changes += 1;
                    }
                } else {
                    logger.error("图片下载出错[url={} => file={}]: {}", src, file, result.getException());
                }
            }

            return changes;
        }

        Optional<model.ImageResource> getNoteImageResource(String file) {
            try {
                return Optional.of(new model.ImageFile(file).toImageResource());
            } catch (Exception e) {
                logger.error("文件[{}]无法读取为图片资源: ", file, e);
            }

            return Optional.empty();
        }
    }

    private static class NoteServiceException extends Exception {
        NoteServiceException(String s, Exception e) {
            super(s, e);
        }
    }
}
