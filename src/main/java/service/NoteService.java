package service;

import app.Log;

public class NoteService extends Service implements Interface {
    private static NoteService me = null;
    public static NoteService init() {
        if (me == null) {
            me = new NoteService();
        }

        return me;
    }
    private NoteService() {
        this.logger = Log.newLogger(NoteService.class);
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
}
