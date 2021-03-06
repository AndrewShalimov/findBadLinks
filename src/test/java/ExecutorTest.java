import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.posts.Utils;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class ExecutorTest {

    private ClassLoader classLoader;
    private boolean timeToStop = false;
    Toolkit toolkit = Toolkit.getDefaultToolkit();

    @Before
    public void init() {
        classLoader = getClass().getClassLoader();
    }


    //@Test
    public void executorTest() throws ExecutionException, IOException, TimeoutException {


        File linksFile = new File(classLoader.getResource("abused_list.txt").getFile());
        List<String> abusedList = Utils.readStringsFromFile(linksFile);

        abusedList = abusedList.subList(0, 100);
        Map<String, Long> mapToRun = new HashMap<>();
        abusedList.stream().forEach(
                file -> mapToRun.put(file, null)
        );
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future> tasks = Lists.newArrayList();
        mapToRun.computeIfPresent("05.The.Wire.2x05.Undertow.DvDRip.XviD.AC3.Thomilla.avi.mp4", (k, v) -> 5L);
        for (Map.Entry abuse : mapToRun.entrySet()) {
            Future future;
            if (abuse.getValue() == null) {
                future = executor.submit(new Task(
                        new Thread(() -> {
                            System.out.println(abuse.getKey());
                        })
                ));
            } else {
                future = executor.submit(new Task(
                        new Thread(() -> {
                            System.out.println(abuse.getKey());
                        })
                ));
            }
            tasks.add(future);
        }

        for (Future future : tasks) {
            try {
                future.get(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                future.cancel(true);
            }
        }

//        try {
//            System.out.println("Started..");
//            System.out.println(future.get(3, TimeUnit.SECONDS));
//            System.out.println("Finished!");
//        } catch (TimeoutException e) {
//            future.cancel(true);
//            System.out.println("Terminated!");
//        }

        executor.shutdownNow();
    }

    class Task implements Callable<Void> {
        private long timeToLive = 0;
        private TimeUnit unit;
        private Thread threadToRun;

        public Task() {
        }

        public Task(Thread threadToRun) {
            this.threadToRun = threadToRun;
        }

        public Task(Thread threadToRun, long timeToLive, TimeUnit unit) {
            this.timeToLive = timeToLive;
            this.unit = unit;
            this.threadToRun = threadToRun;
        }

        //
//        public Task(long timeToLive, TimeUnit unit) {
//            this.timeToLive = timeToLive;
//            this.unit = unit;
//        }

        @Override
        public Void call() throws Exception {
            // Thread.sleep(2000); // Just to demo a long running task of 4 seconds.
            if (timeToLive != 0) {
                Thread.sleep(unit.toMillis(timeToLive));
            }
            threadToRun.start();
            return null;
        }
    }



    @Test
    public void executorTerminationTest() throws ExecutionException, IOException, TimeoutException {

        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10000; i++) {
            int finalI = i;
            Thread worker = new Thread(() -> {
                doStaff(finalI);
            }, "worker__" + System.identityHashCode(i));
            executor.execute(worker);
        }

        Timer timer = new Timer();
        timer.schedule(new RemindTask(), TimeUnit.SECONDS.toMillis(10));
        while (!executor.isTerminated() && !timeToStop)  {
            //process
        };
        executor.shutdownNow();


    }

    private void doStaff(int i)  {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("------- did staff: " + i);
    }

    class RemindTask extends TimerTask {
        public void run() {
            System.out.println("Time's up!");
            toolkit.beep();
            timeToStop = true;
        }
    }


}
