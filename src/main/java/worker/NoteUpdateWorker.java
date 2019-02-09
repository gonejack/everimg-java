package worker;

import app.Conf;
import app.Log;
import com.evernote.edam.type.Note;
import org.slf4j.Logger;
import service.NoteService;

import java.util.List;

public class NoteUpdateWorker extends Worker implements Interface {
    private final static Logger logger = Log.newLogger(NoteUpdateWorker.class);
    private final static NoteService noteService = NoteService.init();
    private final static int updateIntervalSeconds = Conf.get("deploy.update.intervalSeconds", 10);

    private boolean running;
    private Thread loop;

    private static NoteUpdateWorker me;
    private NoteUpdateWorker() {
        this.loop = new Thread(() -> {
            this.running = true;

            while (true) {
                try {
                    this.sleepSec(updateIntervalSeconds);
                    this.updateNotes();
                }
                catch (InterruptedException e) {
                    logger.debug("中断循环: {}", e.getMessage());
                    break;
                }
            }

            this.running = false;
        }, "loop");
    }

    private void updateNotes() throws InterruptedException {
        List<Note> updatedNotes = noteService.getRecentUpdatedNotes();

        if (updatedNotes.isEmpty()) {
            logger.debug("待更新笔记列表为空");
        }
        else {
            for (Note note : updatedNotes) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("主动取消笔记更新");
                }

                String title = note.getTitle();
                logger.debug("更新笔记[{}]", title);

                int changes = noteService.modifyNote(note);
                if (changes > 0) {
                    logger.debug("保存笔记[{}]", title);

                    noteService.saveNote(note);
                }
                else {
                    logger.debug("笔记[{}]没有更新点", title);
                }
            }
        }
    }

    public static synchronized NoteUpdateWorker init() {
        if (me == null) {
            me = new NoteUpdateWorker();
        }

        return me;
    }

    @Override
    public void start() {
        logger.debug("开始启动");

        this.loop.start();

        logger.debug("启动完成");
    }

    @Override
    public void stop() {
        logger.debug("开始退出");

        while (this.running) {
            try {
                this.loop.interrupt();
                this.sleepSec(1);
                if (this.running) {
                    logger.debug("退出等待中...");
                }
            }
            catch (InterruptedException e) {
                logger.error("关机线程出错: {}", e.getMessage());
            }
        }

        logger.debug("退出完成");
    }
}
