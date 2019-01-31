package worker;

import app.Log;
import service.NoteService;

public class NoteUpdateWorker extends Worker implements Interface {
    private static NoteUpdateWorker me;
    public static NoteUpdateWorker init() {
        if (me == null) {
            me = new NoteUpdateWorker();
        }
        return me;
    }

    private boolean running = false;
    private boolean keep = true;
    private Thread loop;
    private NoteUpdateWorker() {
        logger = Log.newLogger(NoteUpdateWorker.class);
    }
    private void checkNote() {

    }

    @Override
    public void start() {
        logger.debug("开始启动");
        this.loop = new Thread(() -> {
            this.keep = true;
            this.running = true;

            while (this.keep) {
                this.sleep(10);
                this.checkNote();
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
