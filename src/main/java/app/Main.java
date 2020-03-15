package app;

import service.NoteService;
import worker.NoteUpdateWorker;

public class Main {
    public static void main(String[] args) {
        var app = new App();
        try {
            app.addService(NoteService.init());
            app.addWorker(NoteUpdateWorker.init());
            app.boot();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
