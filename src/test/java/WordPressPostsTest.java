import com.afrozaar.wordpress.wpapi.v2.Wordpress;
import com.afrozaar.wordpress.wpapi.v2.model.Post;
import com.afrozaar.wordpress.wpapi.v2.request.Request;
import com.afrozaar.wordpress.wpapi.v2.request.SearchRequest;
import com.afrozaar.wordpress.wpapi.v2.response.PagedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.shal.OpenLoadClient;
import org.shal.PostAnalyser;
import org.shal.model.OpenLoadUploadStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class WordPressPostsTest {

    private PostAnalyser analyser;
    private ClassLoader classLoader;
    private Wordpress wordPressClient;

//    @Before
//    public void init() throws IOException, URISyntaxException {
//        analyser = new PostAnalyser();
//        analyser.readConfiguration();
//        analyser.initClient();
//        classLoader = getClass().getClassLoader();
//        wordPressClient = analyser.getWordPressClient();
//    }
//
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
