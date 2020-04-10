package worker;

import app.Config;
import app.Log;
import org.slf4j.Logger;
import service.NoteService;

public class NoteUpdateWorker extends Worker {
    private final static Logger logger = Log.newLogger(NoteUpdateWorker.class);
    private final static NoteService noteService = NoteService.init();
    private final static int updateIntervalSeconds = Config.get("deploy.update.intervalSeconds", 10);

    private static NoteUpdateWorker me;

    @Override
    public void start() {
        logger.debug("开始启动");

        new Thread(() -> {
            this.register();
            this.mainLoop();
            this.arrive();
        }, "main").start();

        logger.debug("启动完成");
    }

    @Override
    public void stop() throws InterruptedException {
        logger.debug("开始退出");

        this.waitArrives();

        logger.debug("退出完成");
    }

    private void mainLoop() {
        while (this.on) {
            try {
                this.sleepSec(updateIntervalSeconds);
                this.updateNotes();
            } catch (InterruptedException e) {
                logger.debug("中断循环: {}", e.getMessage());
                break;
            }
        }
    }

    private void updateNotes() throws InterruptedException {
        var updatedNotes = noteService.getRecentUpdatedNotes();

        if (updatedNotes.isEmpty()) {
            logger.debug("待更新列表为空");
        } else {
            for (var note : updatedNotes) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("主动取消笔记更新");
                }

                var title = note.getTitle();
                logger.debug("准备更新[{}]", title);

                int changes = noteService.modifyNote(note);
                if (changes > 0) {
                    noteService.saveNote(note);

                    logger.info("已更新[{}]", title);
                } else {
                    logger.debug("笔记[{}]没有更新点", title);
                }
            }
        }
    }
}
