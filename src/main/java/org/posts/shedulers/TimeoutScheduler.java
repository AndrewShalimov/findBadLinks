package org.posts.shedulers;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimeoutScheduler {

    public TimeoutScheduler(long interval, TimeUnit unit, ScheduledTask task) {
        ScheduledExecutorService execService = Executors.newScheduledThreadPool(5);
        execService.scheduleAtFixedRate(()->{
            task.run();
            execService.shutdownNow();
        }, interval, interval, unit);
    }
}
