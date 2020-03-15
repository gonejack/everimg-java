package worker;

import app.App;

import java.util.concurrent.Phaser;

abstract class Worker implements App.Component {
    void sleepSec(int sec) throws InterruptedException {
        Thread.sleep(sec * 1000);
    }

    private Phaser phaser;
    protected boolean on = true;
    protected void register() {
        phaser.register();
    }
    protected void arrive() {
        phaser.arrive();
    }
    protected void waitArrives() throws InterruptedException {
        this.on = false;
        phaser.wait();
    }
}
