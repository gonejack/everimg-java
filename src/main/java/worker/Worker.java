package worker;

import org.slf4j.Logger;

abstract class Worker implements Interface {
    Logger logger;

    void sleep(int sec) {
        try {
            Thread.sleep(sec * 1000);
        }
        catch (InterruptedException e) {
            logger.debug("中断定时器: {}", e.getMessage());
        }
    }
}
