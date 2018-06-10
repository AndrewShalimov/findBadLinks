import org.junit.Before;
import org.junit.Test;
import org.shal.PostAnalyser;
import org.shal.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AppTest {

//    private PostAnalyser analyser;
//    private ClassLoader classLoader;
//
//    @Before
//    public void init() {
//        analyser = new PostAnalyser();
//        classLoader = getClass().getClassLoader();
//    }
//
//
//    //@Test
//    public void testUpdateConfig() throws IOException, URISyntaxException {
//        analyser.openLoadClient.updateConfig("openLoad", "cookie", "11111111111111111111");
//        analyser.openLoadClient.updateConfig("openLoad", "loginKey", "222222222222");
//    }
//
//    //@Test
//    public void testReadAbused() throws IOException {
//        analyser.openLoadClient.getAbusedFiles();
//    }
//
//    //@Test
//    public void testExtract() throws IOException {
//        String content = "[tab:Openload]\n" +
//                "<iframe src=\"https://openload.co/embed/yszuvztgKY4/Roseanne_S7_Ep08_Punch_And_Jimmy.avi.mp4\" scrolling=\"no\" frameborder=\"0\" width=\"540\" height=\"330\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\"></iframe>";
//        String link = analyser.linkExtractor(content);
//        System.out.println(link);
//    }
//
//    //@Test
//    public void readConsoleTest() throws IOException {
////        Scanner scanner = new Scanner(System.in);
////        System.out.println("please, enter: ");
////        String input = scanner.nextLine();;
////        System.out.println("input: " + input);
//
//        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//        System.out.print("Hello. Please write your name: ");
//        String name = br.readLine();
//        System.out.println("Your name is: " + name);
//    }
//
//    //@Test
//    public void testLinks() throws IOException, URISyntaxException, InterruptedException {
////        String invalidLink = "https://oload.win/embed/SNKC_nworqeo/all.night.s011e10.720p.web.h264-tbs.mkv11.mp4";
////        assert analyser.isLinkAlive(invalidLink) == false;
////
////        String validLink = "https://oload.win/embed/SNKC_nworqo/all.night.s01e10.720p.web.h264-tbs.mkv.mp4";
////        assert analyser.isLinkAlive(validLink) == true;
//        analyser.readConfiguration();
//        analyser.initClient();
//
//        File linksFile = new File(classLoader.getResource("openload_links.txt").getFile());
//        List<String> links = Utils.readStringsFromFile(linksFile);
//
//        ExecutorService executor = Executors.newFixedThreadPool(10);
//        long before = System.currentTimeMillis();
//        AtomicInteger badLinksCounter = new AtomicInteger(0);
//
////        for (String link : links) {
////            boolean result = analyser.isLinkAlive(link);
////            if (!result) {
////                badLinksCounter.getAndIncrement();
////            }
////            System.out.println(link + " : " + result);
////        }
//
//        for (String link : links) {
//            executor.submit(() -> {
//                boolean result = analyser.openLoadClient.isLinkAlive(link);
//                if (!result) {
//                    badLinksCounter.getAndIncrement();
//                }
//                System.out.println(link + " : " + result);
//            });
//        }
//        executor.shutdown();
//        while (!executor.isTerminated()) ;
//
////        for (String link : links) {
////            //analyser.isLinkAlive(link);
////            Thread worker = new Thread(() -> {
////                boolean result = analyser.isLinkAlive(link);
////                if (!result) {
////                    badLinksCounter.getAndIncrement();
////                }
////                System.out.println(link + " : " + result);
////            }, "worker_openload_" + System.identityHashCode(link));
////            executor.execute(worker);
////        }
////        executor.shutdown();
////        while (!executor.isTerminated()) ;
//
//        long after = System.currentTimeMillis();
//        System.out.println(" -------- Done. Time: " + (after - before) + ". BadLinks:" + badLinksCounter.get());
//
//    }
//
//    //@Test
//    public void test_single_link() throws IOException, URISyntaxException {
//        analyser.readConfiguration();
//        analyser.initClient();
//
//        String link = "https://openload.co/embed/SyntWJ_upwc/";
//        System.out.println(link + " : " + analyser.openLoadClient.isLinkAlive(link));
//
//    }
//
//    //@Test
//    public void sendMailTest() throws IOException, URISyntaxException {
//        analyser.readConfiguration();
//        analyser.sendMail("test", "test body");
//
//    }
//
//    //@Test
//    public void testLinkValidation() throws IOException, URISyntaxException, InterruptedException {
//        analyser.readConfiguration();
//        File linksFile = new File(classLoader.getResource("test_links.txt").getFile());
//        List<String> links = Utils.readStringsFromFile(linksFile);
//        for (String link : links) {
//            boolean isValid = analyser.isLinkValid(link);
//            System.out.println("link: " + link + " " + isValid);
//        }
//
//    }
//
//
//    //@Test
//    public void filterCatalogTest() throws IOException, URISyntaxException {
//        analyser.readConfiguration();
//        File linksFile = new File(classLoader.getResource("abused_list.txt").getFile());
//        List<String> abusedList = Utils.readStringsFromFile(linksFile);
//        analyser.fullRestoreCircle(abusedList);
//    }
//
//
//

}
