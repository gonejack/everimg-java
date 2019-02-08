package worker;

abstract class Worker implements Interface {
    void sleepSec(int sec) throws InterruptedException {
        Thread.sleep(sec * 1000);
    }
}
