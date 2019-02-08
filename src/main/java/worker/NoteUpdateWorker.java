package worker;

import app.Log;
import com.evernote.edam.type.Note;
import org.slf4j.Logger;
import service.NoteService;

import java.util.List;

public class NoteUpdateWorker extends Worker implements Interface {
    private final static Logger logger = Log.newLogger(NoteUpdateWorker.class);
    private boolean running = false;
    private boolean keep = false;
    private Thread loop;
    private NoteService noteService;

    private static NoteUpdateWorker me;
    public static synchronized NoteUpdateWorker init() {
        if (me == null) {
            me = new NoteUpdateWorker();
        }

        return me;
    }
    private NoteUpdateWorker() {
        noteService = NoteService.init();
    }

    private void updateNotes() throws InterruptedException {
        List<Note> updatedNotes = noteService.getRecentUpdatedNotes();

        if (updatedNotes.isEmpty()) {
            logger.debug("获取更新列表为空");
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
                    logger.debug("笔记[{}]没有变动", title);
                }
            }
        }
    }

    @Override
    public void start() {
        logger.debug("开始启动");

        this.loop = new Thread(() -> {
            this.keep = true;
            this.running = true;

            while (this.keep) {
                try {
                    this.sleepSec(10);
                    this.updateNotes();
                }
                catch (InterruptedException e) {
                    logger.debug("退出工作循环: {}", e.getMessage());
                    break;
                }
            }

            this.running = false;
        });
        this.loop.start();

        logger.debug("启动完成");
    }

    @Override
    public void stop() {
        logger.debug("开始退出");

        this.keep = false;
        while (this.running) {
            try {
                this.loop.interrupt();
                logger.debug("退出中");
                this.sleepSec(1);
            }
            catch (InterruptedException e) {
                logger.error("关机线程出错: {}", e.getMessage());
                break;
            }
        }

        logger.debug("退出完成");
    }
}
