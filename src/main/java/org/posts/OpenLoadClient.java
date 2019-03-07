package org.posts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.BaseRequest;
import com.mashape.unirest.request.body.MultipartBody;
import org.apache.commons.lang3.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.posts.model.OpenLoadUploadStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.posts.Utils.isEmpty;

public class OpenLoadClient {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String confFileName = "app_config.json";
    private String apiLogin = "";
    private String apiKey = "";
    private String loginVerifyCode = "";
    private String cookie = "";
    private String loginUrl = "https://openload.co/login";
    private String takedownsUrl = "https://openload.co/filemanager/takedowns";
    private String clearAbusesUrl = "https://openload.co/filemanager/cleardmca";
    private String openLoadCredentials = "";
    private final String openLoadApiUrl = "https://api.openload.co/1/";
    private ObjectMapper objectMapper;
    private String identity = "";
    private String csrfToken = "";

    private Set<AbusedFile> abusedFiles = new HashSet();
    private Map openLoadHeaders = new HashMap() {{
        put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36");
        put("Accept-Encoding", "gzip, deflate, br");
        put("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8");
        put("content-type", "application/x-www-form-urlencoded");
        put("origin", "https://openload.co");
        put("pragma", "no-cache");
    }};

    public OpenLoadClient(String apiLogin, String apiKey, String loginVerifyCode, String cookie) {
        this.apiLogin = apiLogin;
        this.apiKey = apiKey;
        this.loginVerifyCode = loginVerifyCode;
        this.cookie = cookie;
        this.openLoadCredentials = String.format("&login=%s&key=%s", apiLogin, apiKey);
        objectMapper = new ObjectMapper();
    }

    public List<String> getAbusedFiles() throws OpenLoadException {
        abusedFiles = new HashSet<>();
        readAbusedFiles();
        List<String> resultFiles = Lists.newArrayList();
        for (AbusedFile abusedFile : abusedFiles) {
            resultFiles.add(abusedFile.getRealFileName());
        }
        return resultFiles;
    }

    public OpenLoadResponse createTestResponse() {
        return new OpenLoadResponse();
    }


    public void clearAbusedFiles() throws OpenLoadException {
        try {
            logger.info("-------- Clear Abuses");
            logger.info("-------- POST to {}", clearAbusesUrl);
            HttpResponse<String> response = Unirest.post(clearAbusesUrl)
                    .headers(openLoadHeaders)
                    .header("referer", "https://openload.co/account")
                    .header("authority", "openload.co")
                    .header("Set-Cookie", identity)
                    .header("x-csrf-token", csrfToken)
                    .header("yii_csrf_token", csrfToken)
                    .header("x-requested-with", "XMLHttpRequest")
                    .field("_csrf", csrfToken)
                    .asString();

            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                logger.info("-------- Abuses cleared OK.");
                String responseBody = response.getBody();
            } else {
                String errorMessage = String.format("Error clear Abused files. Response is: '%s', status: %d", response.getBody(), response.getStatus());
                logger.error(errorMessage);
                throw new OpenLoadException(errorMessage);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new OpenLoadException(e);
        }
    }

    private void readAbusedFiles() throws OpenLoadException {
        try {
            logger.info("-------- GET to {}", loginUrl);
            HttpResponse<String> response = Unirest.get(loginUrl)
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .asString();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                String responseHtml = response.getBody();
                Document doc = Jsoup.parse(responseHtml);
                String csrfToken = doc.select("meta[name=csrf-token]").attr("content");
                cookie = String.join("; ", response.getHeaders().get("Set-Cookie"));
                updateConfig("openLoad", "cookie", cookie);
                OpenLoadResponse loginResult = postLogin(csrfToken, "");
                if (!isEmpty(loginResult.htmlBody) && loginResult.htmlBody.indexOf("id=\"loginform-loginkey\"") > 0) {
                    loginResult = postLogin(csrfToken, loginVerifyCode);
                    if (!isEmpty(loginResult.htmlBody) && loginResult.htmlBody.indexOf("id=\"loginform-loginkey\"") > 0) {
                        System.out.print("-------- Elena, please, enter the code you've received from OpenLoad: ");
                        try {
                            loginVerifyCode = System.console().readLine();
                            updateConfig("openLoad", "loginVerifyCode", loginVerifyCode);
                        } catch (Exception e) {
                            logger.error(Utils.getStackTrace(e));
                        }
                        loginResult = postLogin(csrfToken, loginVerifyCode);
                        processTakedowns(loginResult);
                    } else {
                        processTakedowns(loginResult);
                    }
                } else {
                    processTakedowns(loginResult);
                }
            } else {
                String errorMessage = String.format("Error login to OpenLoad. Response is: '%s', status: %d", response.getBody(), response.getStatus());
                logger.error(errorMessage);
                throw new OpenLoadException(errorMessage);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new OpenLoadException(e);
        }
    }

    private void processTakedowns(OpenLoadResponse openLoadResponse) throws OpenLoadException {
        identity = openLoadResponse.identity;
        try {
            logger.info("-------- GET to {}", takedownsUrl);
            HttpResponse<String> response = Unirest.get(takedownsUrl)
                    .headers(openLoadHeaders)
                    .header("Set-Cookie", openLoadResponse.identity)
                    .asString();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                String responseBody = response.getBody();
                responseBody = StringEscapeUtils.unescapeEcmaScript(responseBody.substring(1, responseBody.length() - 1));
                JSONObject responseJson = new JSONObject(responseBody);
                for (Object abusedFileObject : (JSONArray) responseJson.get("data")) {
                    JSONObject obj = (JSONObject) abusedFileObject;
                    abusedFiles.add(new AbusedFile(
                            (String) obj.get("link"),
                            (String) obj.get("linkextid"),
                            (String) obj.get("name"))
                    );
                }
            } else {
                String errorMessage = String.format("Error reading Abused files. Response is: '%s', status: %d", response.getBody(), response.getStatus());
                logger.error(errorMessage);
                throw new OpenLoadException(errorMessage);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new OpenLoadException(e);
        }
    }

    private OpenLoadResponse postLogin(String csrfToken, String loginVerifyCode) throws OpenLoadException {
        OpenLoadResponse result = new OpenLoadResponse("", null);
        this.csrfToken = csrfToken;
        try {
            logger.info("-------- POST to {}", loginUrl);
            BaseRequest postLoginRequest = Unirest.post(loginUrl)
                    .headers(openLoadHeaders)
                    .header("Set-Cookie", cookie)
                    .field("_csrf", csrfToken)
                    .field("LoginForm[email]", "sergeeva.dmca@gmail.com")
                    .field("LoginForm[password]", "HZagzFp6fRkSl0q#?Y");
            if (!isEmpty(loginVerifyCode)) {
                ((MultipartBody) postLoginRequest).field("LoginForm[loginkey]", loginVerifyCode);
            }

            HttpResponse<String> postLoginResponse = postLoginRequest.asString();
            if (postLoginResponse.getStatus() >= 200 && postLoginResponse.getStatus() < 300) {
                String postLoginResponseHtml = postLoginResponse.getBody();
                result.htmlBody = postLoginResponseHtml;
                if (postLoginResponse.getHeaders().get("Set-Cookie") != null) {
                    cookie = String.join("; ", postLoginResponse.getHeaders().get("Set-Cookie"));
                }
            }
            if (postLoginResponse.getStatus() == 302) {
                String location = postLoginResponse.getHeaders().get("Location").get(0);
                logger.info("-------- GET to {}", location);
                HttpResponse<String> getResponse = Unirest.get(location)
                        .headers(openLoadHeaders)
                        .header("Set-Cookie", cookie)
                        .asString();
                if (getResponse.getStatus() >= 200 && getResponse.getStatus() < 300) {
                    result.htmlBody = getResponse.getBody();
                    result.identity = String.join("; ", postLoginResponse.getHeaders().get("Set-Cookie"));
                }
            }
            if (postLoginResponse.getStatus() >= 500) {
                String errorMessage = String.format("Error login to OpenLoad. Response is: '%s', status: %d", postLoginResponse.getBody(), postLoginResponse.getStatus());
                logger.error(errorMessage);
                throw new OpenLoadException(errorMessage);
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new OpenLoadException(e);
        }
        return result;
    }

    public void updateConfig(String category, String key, String value) throws IOException, URISyntaxException {
        File jarFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        List<String> lines = Utils.readStringsFromFile(jarFile.getParentFile() + File.separator + confFileName);
        String updatedLine = "";
        int index = 0;
        for (String line : lines) {
            if (line.indexOf(key) > 0) {
                updatedLine = line;
                index = lines.indexOf(line);
            }
        }
        Pattern pattern = Pattern.compile("(?<=\"" + key + "\" : \")(.*)(?=\")");
        Matcher matcher = pattern.matcher(updatedLine);
        updatedLine = matcher.replaceFirst(value);
        lines.set(index, updatedLine);
        String dataString = String.join("\n", lines);
        Utils.writeStringToFile(dataString.trim(), new File(jarFile.getParentFile() + File.separator + confFileName));
    }

    public boolean isLinkAlive(String link) {
        if (isEmpty(link)) {
            return false;
        }
        String openLoadId = "";
        try {
            Pattern pattern = Pattern.compile("/.*/(.*?)/");
            Matcher matcher = pattern.matcher(link);
            matcher.find();
            openLoadId = matcher.group(1);
            if (isEmpty(openLoadId)) {
                return false;
            }
        } catch (Exception e) {
            logger.debug(e.getMessage());
            return false;
        }

        try {
            String openLoadLink = String.format(openLoadApiUrl + "file/info?file=%s" + openLoadCredentials, openLoadId, apiLogin, apiKey);
            //Unirest.setTimeouts();
            HttpResponse<JsonNode> response = Unirest.get(openLoadLink)
                    .headers(openLoadHeaders)
                    .asJson();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                JSONObject responseJson = response.getBody().getObject();
                int fileStatus = (int) ((JSONObject) ((JSONObject) responseJson.get("result")).get(openLoadId)).get("status");
                return fileStatus == 200;
            } else {
                return false;
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            return false;
        }
    }

    public OpenLoadResponse uploadFile(String fileUrl, String folder) throws OpenLoadException {
        String uploadUrl = openLoadApiUrl
                + "remotedl/add?"
                + "url=" + fileUrl
                + (isEmpty(folder) ? "" : "&folder=" + folder)
                + openLoadCredentials;
        OpenLoadResponse openLoadResponse = null;
        try {
            logger.info("-------- Uploading '{}' to OpenLoad {}", fileUrl, isEmpty(folder) ? "" : folder);
            HttpResponse<JsonNode> response = Unirest.get(uploadUrl)
                    .headers(openLoadHeaders)
                    .asJson();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                JSONObject responseJson = response.getBody().getObject();
                int responseStatus = (int) responseJson.get("status");
                if (responseStatus != 200) {
                    String error = "Error: OpenLoad response status is: " + responseStatus + ", response is: " + responseJson.get("msg");
                    logger.error(error);
                    throw new OpenLoadException(error);
                }
                JSONObject openLoadResult = (JSONObject) responseJson.get("result");
                OpenLoadUploadStatus uploadStatus = checkUploadStatus(openLoadResult.getString("id"));

                openLoadResponse = new OpenLoadResponse(uploadStatus);
                logger.info("-------- Upload is complete. OpenLoadId: '{}'", openLoadResponse.openLoadId);
            } else {
                String error = "Error: OpenLoad response status is: " + response.getStatus() + ", response is: " + response.getBody();
                logger.error(error);
                throw new OpenLoadException(error);
            }
        } catch (OpenLoadException e) {
            throw new OpenLoadException(e);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new OpenLoadException(e);
        }
        return openLoadResponse;
    }

    public OpenLoadUploadStatus checkUploadStatus(String remoteUploadId) throws OpenLoadException {
        String checkStatusUrl = openLoadApiUrl
                + "remotedl/status?"
                + "id=" + remoteUploadId
                + openLoadCredentials;
        try {
            HttpResponse<JsonNode> response = Unirest.get(checkStatusUrl)
                    .headers(openLoadHeaders)
                    .asJson();
            if (response.getStatus() >= 200 && response.getStatus() < 300) {
                JSONObject responseJson = response.getBody().getObject();
                int responseStatus = (int) responseJson.get("status");
                if (responseStatus != 200) {
                    String error = "Error: OpenLoad response status is: " + responseStatus + ", response is: " + responseJson.get("msg");
                    logger.error(error);
                    throw new OpenLoadException(error);
                }
                JSONObject openLoadResult = (JSONObject) responseJson.get("result");
                JSONObject resultJson = openLoadResult.getJSONObject(remoteUploadId);
                OpenLoadUploadStatus openLoadUploadStatus = objectMapper.convertValue(resultJson.toMap(), OpenLoadUploadStatus.class);
                return openLoadUploadStatus;
            } else {
                String error = "Error: OpenLoad response status is: " + response.getStatus() + ", response is: " + response.getBody();
                logger.error(error);
                throw new OpenLoadException(error);
            }
        } catch (OpenLoadException e) {
            throw new OpenLoadException(e);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new OpenLoadException(e);
        }
    }


    public class OpenLoadResponse {
        String htmlBody;
        String identity;
        String openLoadId;
        String folderId;
        String embedLink;
        String fileName;
        String openLoadLink;
        private Pattern patternFileName = Pattern.compile("([^/]+$)");

        public OpenLoadResponse(String htmlBody, String identity) {
            this.htmlBody = htmlBody;
            this.identity = identity;
        }

        public OpenLoadResponse(OpenLoadUploadStatus openLoadUploadStatus) {
            Matcher matcher = patternFileName.matcher(openLoadUploadStatus.remoteurl);
            matcher.find();
            this.fileName = matcher.group(1);
            this.openLoadId = openLoadUploadStatus.extid;
            this.embedLink = "<iframe src=\"https://openload.co/embed/" + this.openLoadId + "/" + fileName + "\" scrolling=\"no\" frameborder=\"0\" width=\"700\" height=\"430\" allowfullscreen=\"true\" webkitallowfullscreen=\"true\" mozallowfullscreen=\"true\"></iframe>";
            this.openLoadLink = "https://openload.co/embed/" + this.openLoadId + "/" + fileName;
        }

        public OpenLoadResponse() {
        }
    }

    class AbusedFile {
        String link;
        String openLoadId;
        String fileName;

        public AbusedFile(String link, String openLoadId, String fileNme) {
            this.link = link;
            this.openLoadId = openLoadId;
            this.fileName = fileNme;
        }

        public String getRealFileName() {
            String pattern = openLoadId + "/";
            String realFileName = link.substring(link.indexOf(pattern) + pattern.length(), link.length());
            return realFileName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AbusedFile that = (AbusedFile) o;
            return Objects.equals(link, that.link) &&
                    Objects.equals(openLoadId, that.openLoadId) &&
                    Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(link, openLoadId, fileName);
        }

        @Override
        public String toString() {
            return "AbusedFile {" +
                    "link='" + link + '\'' +
                    ", openLoadId='" + openLoadId + '\'' +
                    ", fileName='" + fileName + '\'' +
                    '}';
        }
    }

}
