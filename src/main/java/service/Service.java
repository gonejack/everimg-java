package service;

import org.slf4j.Logger;

abstract class Service implements Interface {
    Logger logger;

    void sleep(int sec) {
        try {
            Thread.sleep(sec * 1000);
        }
        catch (Exception e) {
            logger.debug("中断定时器: {}", e.getMessage());
        }
    }
}
