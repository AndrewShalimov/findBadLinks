package org.posts;

import com.afrozaar.wordpress.wpapi.v2.Client;
import com.afrozaar.wordpress.wpapi.v2.model.Post;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.BaseRequest;
import org.json.JSONObject;
import org.posts.exceptions.WordpressException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.posts.Utils.getStackTrace;
import static org.posts.Utils.isEmpty;

public class WordpressCustomClient extends Client {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36";
    private String wpRequestCookie = "";
    private String loginPath = "/wp/wp-login.php";

    public WordpressCustomClient(String baseUrl, String username, String password, boolean usePermalinkEndpoint, boolean debug) {
        super(baseUrl, username, password, usePermalinkEndpoint, debug);
    }

    public WordpressCustomClient(String baseUrl, String username, String password, boolean usePermalinkEndpoint, boolean debug, ClientHttpRequestFactory requestFactory) {
        super(baseUrl, username, password, usePermalinkEndpoint, debug, requestFactory);
    }

    public WordpressCustomClient(String context, String baseUrl, String username, String password, boolean usePermalinkEndpoint, boolean debug) {
        super(context, baseUrl, username, password, usePermalinkEndpoint, debug);
    }

    public WordpressCustomClient(String context, String baseUrl, String username, String password, boolean usePermalinkEndpoint, boolean debug, ClientHttpRequestFactory requestFactory) {
        super(context, baseUrl, username, password, usePermalinkEndpoint, debug, requestFactory);
    }

    public WordpressCustomClient(Client client) {
        super(null, client.baseUrl, client.username, client.password, client.permalinkEndpoint, client.debug, null);
    }

    private void loginToWordpress() throws WordpressException {
        String requestUrl = baseUrl + loginPath;

        try {
            logger.info("Login in to WordPress: {} ", baseUrl);
            Map wordPressHeaders = new HashMap() {{
                put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/67.0.3396.99 Safari/537.36");
                put("Upgrade-Insecure-Requests", "1");
                put("Accept-Encoding", "gzip, deflate, br");
                put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
                put("Cache-Control", "max-age=0");
                put("Connection", "keep-alive");
            }};

            BaseRequest postLoginRequest = Unirest.post(requestUrl)
                    .headers(wordPressHeaders)
                    .field("log", this.username)
                    .field("pwd", this.password)
                    .field("wp-submit", "Log In");
            HttpResponse<String> postLoginResponse = postLoginRequest.asString();
            if (postLoginResponse.getStatus() >= 200 && postLoginResponse.getStatus() < 300) {
                if (postLoginResponse.getHeaders().get("Set-Cookie") != null) {
                    wpRequestCookie = String.join("; ", postLoginResponse.getHeaders().get("Set-Cookie"));
                }
             }
            if (postLoginResponse.getStatus() == 302) {
                wpRequestCookie = String.join("; ", postLoginResponse.getHeaders().get("Set-Cookie"));
            }
            else {
                throw new WordpressException(String.format("Unable to log in to WordPress. Status: %d. Response: {}", postLoginResponse.getStatus(), postLoginResponse.getBody()));
            }
        } catch (Exception e) {
            logger.warn(getStackTrace(e));
            throw new WordpressException(e);
        }
        logger.info("Logged in successfully.");
    }

    public List<Post> findPosts(String searchString) {
        List<Post> posts = Lists.newArrayList();
        if (isEmpty(searchString)) {
            return posts;
        }

        try {
            if (isEmpty(wpRequestCookie)) {
                loginToWordpress();
            }
            searchString = URLEncoder.encode(searchString, "UTF-8");
            searchString = URLEncoder.encode(searchString, "UTF-8");
            String requestUrl = this.baseUrl +  "/wp/wp-admin/edit.php?s=" + searchString + "&post_status=all&post_type=post&paged=1";

            Map wordPressHeaders = new HashMap() {{
                put("user-agent", userAgentString);
                put("Cookie", wpRequestCookie);
            }};
            HttpResponse<String> response = Unirest.get(requestUrl)
                    .headers(wordPressHeaders)
                    .asString();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                String responseBody = response.getBody();
                if (!isEmpty(responseBody) && responseBody.contains("\"post_ID\"")) {
                    Pattern pattern = Pattern.compile("var postTabs = (.*?);");
                    Matcher matcher = pattern.matcher(responseBody);
                    while (matcher.find()) {
                        String postIdLine = matcher.group(1);
                        String postId = new ObjectMapper().readTree(postIdLine).get("post_ID").asText();
                        Post post = getPost(new Long(postId), "edit");
                        posts.add(post);
                    }
                } else {
                    return posts;
                }
            } else {
                return posts;
            }

        } catch (Exception e) {
            logger.warn(getStackTrace(e));
            return posts;
        }
        return posts;
    }
}
