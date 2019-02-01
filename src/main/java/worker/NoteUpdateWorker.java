package worker;

import app.Log;
import com.evernote.edam.type.Note;
import service.NoteService;

public class NoteUpdateWorker extends Worker implements Interface {
    private NoteService noteService;
    private boolean running = false;
    private boolean keep = true;
    private Thread loop;

    private static NoteUpdateWorker me;
    public static synchronized NoteUpdateWorker init() {
        if (me == null) {
            me = new NoteUpdateWorker();
        }

        return me;
    }
    private NoteUpdateWorker() {
        logger = Log.newLogger(NoteUpdateWorker.class);
        noteService = NoteService.init();
    }

    private void updateNotes() {
        for (Note note : noteService.getRecentUpdatedNotes()) {
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

    @Override
    public void start() {
        logger.debug("开始启动");
        this.loop = new Thread(() -> {
            this.keep = true;
            this.running = true;

            while (this.keep) {
                this.sleep(10);
                this.updateNotes();
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
        this.loop.interrupt();
        while (this.running) {
            logger.debug("等待退出");

            this.sleep(1);
        }

        logger.debug("退出完成");
    }
}
