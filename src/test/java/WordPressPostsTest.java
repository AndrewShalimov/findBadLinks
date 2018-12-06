import com.afrozaar.wordpress.wpapi.v2.Wordpress;
import com.afrozaar.wordpress.wpapi.v2.model.Post;
import com.afrozaar.wordpress.wpapi.v2.request.Request;
import com.afrozaar.wordpress.wpapi.v2.request.SearchRequest;
import com.afrozaar.wordpress.wpapi.v2.response.PagedResponse;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.junit.Before;
import org.junit.Test;
import org.posts.PostAnalyser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.posts.Utils.isEmpty;

public class WordPressPostsTest {

    private PostAnalyser analyser;
    private ClassLoader classLoader;
    private Wordpress wordPressClient;

    @Before
    public void init() throws IOException, URISyntaxException {
        analyser = new PostAnalyser();
        analyser.readConfiguration();
        analyser.initClient();
        classLoader = getClass().getClassLoader();
        wordPressClient = analyser.getWordPressClient();
    }


    @Test
    public void searchEncodedFiles() throws Exception {
        String fileName = "wrecked.209.hdtv-lol%5Bettv%5D.mkv.mp4";
        SearchRequest request = SearchRequest.Builder.aSearchRequest(Post.class)
                .withUri(Request.POSTS)
                .withParam("search", fileName)
                .withParam("context", "edit")
                .build();
        PagedResponse<Post> response = wordPressClient.search(request);
        List<Post> posts = response.getList();
        System.out.println(posts);
    }

    @Test
    public void hardCoreWP_test() throws Exception {
        String fileName = "American_Dad_S01E04_Francine's_Flashback.mp4";
        List<Post> posts = analyser.tryHardCoreWordPressSearch(fileName);
        System.out.println(posts);
    }

    @Test
    public void run_test() throws Exception {
        String line = "myCommand.exe";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CommandLine commandLine = CommandLine.parse(line);
        DefaultExecutor executor = new DefaultExecutor();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
        executor.setStreamHandler(streamHandler);
        executor.setExitValue(1);

        int exitValue = executor.execute(commandLine);

        String output = outputStream.toString();

        Runtime r = Runtime.getRuntime();

        Process p = r.exec("testPlanFileDir + File.separator + scriptName");
        p.waitFor();

    }



//
//    //@Test
//    public void searchPostTest() throws Exception {
//        String fileToUpload = "https://openload.co/f/xwqxa5ibSuk/the.last.man.on.earth.s04e04.web.x264-tbs.mkv.mp4";
//        String fileName = "the.last.man.on.earth.s04e04.web.x264-tbs.mkv.mp4";
//
//        SearchRequest request = SearchRequest.Builder.aSearchRequest(Post.class)
//                .withUri(Request.POSTS)
//                .withParam("search", fileName)
//                .withParam("context", "edit")
//                .build();
//        PagedResponse<Post> response = wordPressClient.search(request);
//        List<Post> posts = response.getList();
//        System.out.println(posts.get(0));
//        Post post = posts.get(0);
//        String newContent = post.getContent().getRaw();
//        Long postId = post.getId();
//        String oldEmbedLink = newContent.substring(newContent.indexOf("<iframe"), newContent.indexOf("</iframe>") + "</iframe>".length());
//        String newEmbedLink =  "<iframe src=\"" + "openLoadUploadStatus.url" + "/" + fileName + "\" scrolling=\"no\" frameborder=\"0\" width=\"700\" height=\"430\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\"></iframe>";
//        newContent = newContent.replace(oldEmbedLink, newEmbedLink);
//        System.out.println(newEmbedLink);
//
//    }





}
