package app;

import service.NoteService;

public class Main {
    public static void main(String[] args) {
        var app = new App();
        try {
            app.onStart(
                NoteService::Start
            );
            app.onStop(
                NoteService::Stop
            );

            app.boot();
        } catch (Exception e) {
            e.printStackTrace();

            System.exit(-1);
        }
    }
}
