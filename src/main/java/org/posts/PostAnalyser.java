package org.posts;

import com.afrozaar.wordpress.wpapi.v2.Client;
import com.afrozaar.wordpress.wpapi.v2.Wordpress;
import com.afrozaar.wordpress.wpapi.v2.config.ClientConfig;
import com.afrozaar.wordpress.wpapi.v2.config.ClientFactory;
import com.afrozaar.wordpress.wpapi.v2.model.Post;
import com.afrozaar.wordpress.wpapi.v2.model.builder.ContentBuilder;
import com.afrozaar.wordpress.wpapi.v2.request.Request;
import com.afrozaar.wordpress.wpapi.v2.request.SearchRequest;
import com.afrozaar.wordpress.wpapi.v2.response.PagedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.net.ssl.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.posts.Utils.getStackTrace;
import static org.posts.Utils.isEmpty;

public class PostAnalyser {

    private final static Logger logger = LoggerFactory.getLogger(PostAnalyser.class);
    private static final String confFileName = "app_config.json";
    private JSONObject config;
    private WordpressCustomClient wordPressClient;
    public OpenLoadClient openLoadClient;
    private List<Post> allPosts = Collections.synchronizedList(new ArrayList<>());
    private List<ResultPostAnalyse> results = Collections.synchronizedList(new ArrayList<>());
    private Integer totalFoundPages = null;
    private int perPage = 100;
    private int openLoadWorkersCount;
    private int wpWorkersCount = 15;
    private ExecutorService executor;
    private String searchParameter = "[tab:Openload]";
    private Toolkit toolkit;
    private Timer timer;
    private boolean timeToStop = false;
    private int timeoutHours;
    private List<String> failedFileNames = Collections.synchronizedList(new ArrayList<>());
    private List<String> successFileNames = Collections.synchronizedList(new ArrayList<>());
    private final static String ABUSED_BACKUP_FOLDER = "abused_backup/";
    private boolean stopMe = false;


    public static void main(String[] args) {
        PostAnalyser analyzer = new PostAnalyser();
        try {
            analyzer.readConfiguration();
        } catch (Exception e) {
            logger.error("Error with reading configuration file '" + confFileName + "' : " + e.getMessage());
        }
        analyzer.initClient();
        String emailBody = "";
        String parameter = (args.length >= 1) ? args[0] : "";
        if ("full_restore_circle".equals(parameter)) {
            analyzer.fullRestoreCircle(null);
        }
        if ("read_abused_files".equals(parameter)) {
            List<String> abusedFiles = Lists.newArrayList();
            try {
                abusedFiles = analyzer.openLoadClient.getAbusedFiles();
            } catch (OpenLoadException e) {
                emailBody = "Error reading Abused files from OpenLoad: \n" + e.getMessage();
                logger.info(emailBody);
                analyzer.sendMail("Abuses report", emailBody);
                return;
            }
            if (isEmpty(abusedFiles)) {
                emailBody = "No abused files found.";
            } else {
                StringBuilder abused = new StringBuilder("Total abused files: " + abusedFiles.size() + "\n");
                for (String file : abusedFiles) {
                    logger.info(file);
                    abused.append("\n" + file);
                }
                emailBody = abused.toString();
            }
            analyzer.sendMail("Abuses report", emailBody);
        }
        if ("analyse_wp_links".equals(parameter)) {
            try {
                analyzer.readPosts();
                analyzer.filterPosts();
                analyzer.analyzeLinks();
            } catch (InterruptedException e) {
                logger.warn("Interrupted by timeout: " + e.getMessage());
            }
            List<ResultPostAnalyse> badPosts = analyzer.results.stream().filter(post -> !post.alive).collect(toList());

            if (badPosts.isEmpty()) {
                emailBody = "No bad links were found.";
            } else {
                emailBody = "Bad links list:\n";
                emailBody = emailBody + badPosts.stream().map(ResultPostAnalyse::getReport).collect(Collectors.joining("\n"));
            }
            analyzer.sendMail("Posts analyzer report", emailBody);
        }
    }

    private void saveAbusedLocally(List<String> abusedFiles) {
        String abusedToSave = String.join("\n", abusedFiles);
        File jarFile;
        try {
            jarFile = new File(PostAnalyser.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File file = new File(jarFile.getParentFile() + File.separator + ABUSED_BACKUP_FOLDER + new SimpleDateFormat("dd-MM-yyyy_HH.mm.ss").format(new Date()) + "___" + abusedFiles.size() + ".txt");
            Utils.writeStringToFile(abusedToSave, file);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            System.exit(0);
        }
    }

    public Wordpress getWordPressClient() {
        return wordPressClient;
    }

    public void fullRestoreCircle(List<String> abusedFiles) {
        if (isEmpty(abusedFiles)) {
            try {
                abusedFiles = openLoadClient.getAbusedFiles();
            } catch (OpenLoadException e) {
                String emailBody = "Error reading Abused files from OpenLoad: \n" + e.getMessage();
                logger.info(emailBody);
                sendMail("Restore Abused report", emailBody);
                return;
            }
        }
        if (isEmpty(abusedFiles)) {
            String emailBody = "No abused files found.";
            logger.info(emailBody);
            sendMail("Restore Abused report", emailBody);
            return;
        }

        try {
            saveAbusedLocally(abusedFiles);
            openLoadClient.clearAbusedFiles();
        } catch (OpenLoadException e) {
            logger.error(getStackTrace(e));
            return;
        }

        String sheetId = (String) ((JSONObject) config.get("catalog")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("catalog")).get("sheetName");
        String range = (String) ((JSONObject) config.get("catalog")).get("range");
        GoogleSheet sheet = new GoogleSheet();
        List<String> catalog = Lists.newArrayList();
        try {
            catalog = sheet.getFilmCatalog(sheetId, sheetName, range);
        } catch (IOException e) {
            logger.error(getStackTrace(e));
            String emailBody = "Error reading Catalog Google sheet: \n" + getStackTrace(e);
            sendMail("Restore Abused report", emailBody);
            return;
        }

        Pattern patternFileName = Pattern.compile("([^/]+$)");
        Pattern patternPrefix = Pattern.compile("(.*[\\/])");
        Map<String, String> catalogFiles = Collections.synchronizedMap(new HashMap<>());
        catalog.stream().parallel()
                .forEach(catalogFile -> {
                    Matcher matcher = patternPrefix.matcher(catalogFile);
                    matcher.find();
                    String prefix = matcher.group(1);
                    matcher = patternFileName.matcher(catalogFile);
                    matcher.find();
                    String filename = matcher.group(1);
                    catalogFiles.put(filename, prefix);
                });

        List<String> abusedFilesNotInCatalog = Collections.synchronizedList(new ArrayList<>());
        List<String> abusedFilesInCatalog = Collections.synchronizedList(new ArrayList<>());
        abusedFiles.stream()
                .parallel()
                .forEach(abusedFile -> {
                    if (catalogFiles.keySet().contains(abusedFile)) {
                        abusedFilesInCatalog.add(abusedFile);
                    } else {
                        abusedFilesNotInCatalog.add(abusedFile);
                    }
                });
        if (!isEmpty(abusedFilesNotInCatalog)) {
            StringBuilder abused = new StringBuilder("Total missed files: " + abusedFilesNotInCatalog.size() + "\n");
            for (String file : abusedFilesNotInCatalog) {
                abused.append("\n" + file);
            }
            String emailBody = abused.toString();
            sendMail("Abused files not found in Catalog", emailBody);
        }

        List<String> filesToRestore = Collections.synchronizedList(new ArrayList<>());
        abusedFilesInCatalog.stream().parallel()
                .forEach(abusedFileInCatalog -> {
                    String fileToUpload = catalogFiles.get(abusedFileInCatalog) + abusedFileInCatalog;
                    filesToRestore.add(fileToUpload);
                });

//        for (String fileToRestore: filesToRestore) {
//            uploadFileAndUpdatePost(fileToRestore);
//        }
        //uploadAndUpdatePost(filesToRestore.get(0));

//        executor = Executors.newScheduledThreadPool(wpWorkersCount);
//        List<Callable> tasks = Lists.newArrayList();
//        for (String fileToRestore: filesToRestore) {
//            Callable<Void> worker = () -> {
//                uploadFileAndUpdatePost(fileToRestore);
//                return null;
//            };
//            //executor.execute(worker);
//            //Future future = executor.schedule(worker, 2, TimeUnit.MILLISECONDS);
//            tasks.add(worker);
//        }
//        executor.invokeAll(Arrays.tasks, 10, TimeUnit.MINUTES);
//        executor.shutdown();
//        while (!executor.isTerminated()) ;

        logger.info("Found {} files to restore. Restore process started.", filesToRestore.size());
//        for (String fileToRestore: filesToRestore) {
//            uploadFileAndUpdatePost(fileToRestore);
//        }

        executor = Executors.newFixedThreadPool(openLoadWorkersCount);
        for (String fileToRestore: filesToRestore) {
            Thread worker = new Thread(() -> {
                uploadFileAndUpdatePost(fileToRestore);
            }, "worker_post_" + System.identityHashCode(fileToRestore));
            executor.execute(worker);
        }
        executor.shutdown();
        toolkit = Toolkit.getDefaultToolkit();
        timer = new Timer();
        timer.schedule(new RemindTask(), TimeUnit.HOURS.toMillis(2));
        while (!executor.isTerminated() && !timeToStop) {
            //do process
        };
        executor.shutdownNow();

        logger.info("Restore process completed. Successfully restored {} files. Failed to restore {} files.", successFileNames.size(), failedFileNames.size());
        String emailBody = "";
        if (!isEmpty(successFileNames)) {
            emailBody = "Files restored successfully " + successFileNames.size() + " of total abused " + abusedFiles.size() + " :\n\n" + String.join("\n", successFileNames);
            sendMail("Files restored successfully", emailBody);
        }
        if (!isEmpty(failedFileNames)) {
            emailBody = "Files failed to restore " + failedFileNames.size() + " of total abused " + abusedFiles.size() + " :\n\n" + String.join("\n", failedFileNames);
            sendMail("Files failed to restore", emailBody);
        }
        System.exit(0);
    }

    private void uploadFileAndUpdatePost(String fileToUpload) {
        long postId = 0L;
        String fileName = "";
        try {
            OpenLoadClient.OpenLoadResponse uploadResult = null;
            try {
                String uploadFolder = (String) ((JSONObject) config.get("openLoad")).get("uploadFolder");
                uploadResult = openLoadClient.uploadFile(fileToUpload, uploadFolder);
            } catch (OpenLoadException e) {
                String infoLine = String.format("Error uploading file '%s' to OpenLoad: " + e.getMessage(), fileToUpload);
                logger.info(infoLine);
                failedFileNames.add(infoLine);
                return;
            }

            fileName = uploadResult.fileName;
            List<Post> posts = tryOrdinaryWordPressSearch(fileName);
            if (isEmpty(posts)) {
                posts = tryEncodedWordPressSearch(fileName);
            }
            if (isEmpty(posts)) {
                posts = tryHardCoreWordPressSearch(fileName);
            }

            if (isEmpty(posts)) {
                String infoLine = "Post with key '" + fileName + "' was NOT found in WordPress.";
                logger.info(infoLine);
                failedFileNames.add(infoLine);
                return;
            }
            if (posts.size() > 1) {
                String infoLine = "Search key '" + fileName + "' was found with more then 1 post. Update it manually.";
                logger.info(infoLine);
                failedFileNames.add(infoLine);
                return;
            }
            Post post = posts.get(0);
            String newContent = post.getContent().getRaw();
            postId = post.getId();
            String oldEmbedLink = newContent.substring(newContent.indexOf("<iframe"), newContent.indexOf("</iframe>") + "</iframe>".length());
            newContent = newContent.replace(oldEmbedLink, uploadResult.embedLink);
            post.setContent(ContentBuilder.aContent().withRendered(newContent).build());
            wordPressClient.updatePost(post);
            String successLine = String.format("File '%s' restored OK. PostID: %d", fileName, postId);
            logger.info(successLine);
            successFileNames.add(successLine);
        } catch (Exception e) {
            String infoLine = String.format("Failed to update WordPress post. PostID: %d, file name: '%s'. %s", postId, fileName, e.getMessage());
            logger.info(infoLine);
            failedFileNames.add(infoLine);
            return;
        }
    }

    public List<Post> tryHardCoreWordPressSearch(String fileName)  {
        List<Post> result = wordPressClient.findPosts(fileName);
        return result;
    }

    private List<Post> tryEncodedWordPressSearch(String fileName) throws UnsupportedEncodingException {
        fileName = URLEncoder.encode(fileName, "UTF-8");
        return tryOrdinaryWordPressSearch(fileName);
    }

    private List<Post> tryOrdinaryWordPressSearch(String fileName) {
        SearchRequest request = SearchRequest.Builder.aSearchRequest(Post.class)
                .withUri(Request.POSTS)
                .withParam("search", fileName)
                .withParam("context", "edit")
                .build();
        PagedResponse<Post> response = wordPressClient.search(request);
        List<Post> posts = response.getList();
        return posts;
    }

    public void sendMail(String subject, String emailBody) {
        String smtpHost = (String) ((JSONObject) ((JSONObject) config.get("mailServer")).get("smtp")).get("address");
        String smtpPort = ((Integer) ((JSONObject) ((JSONObject) config.get("mailServer")).get("smtp")).get("port")).toString();
        String user = (String) ((JSONObject) config.get("mailServer")).get("user");
        String password = (String) ((JSONObject) config.get("mailServer")).get("password");
        JSONArray adminRecipients = (JSONArray) ((JSONObject) config.get("mailServer")).get("adminRecipients");
        adminRecipients.toList();
        List<String> adminsList = adminRecipients.toList().stream().map(l -> l.toString()).collect(toList());
        String toEmail = adminsList.stream().collect(Collectors.joining(", "));
        Properties props = System.getProperties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.socketFactory.port", smtpPort);
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", smtpPort);
        Session session = Session.getDefaultInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, password);
                    }
                });

        String fromEmail = "\"Posts analyzer script\" <" + user + ">";
        try {
            EmailUtil.sendEmail(session, fromEmail, toEmail, subject, emailBody);
        } catch (Exception e) {
            logger.error("Email was no sent: " + e.getMessage());
        }

    }

    public void readConfiguration() throws IOException, URISyntaxException {
        //URL configUrl = ClassLoaderUtil.getResource(confFileName, this.getClass());
        File jarFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        FileInputStream fileIS = new FileInputStream(jarFile.getParentFile() + File.separator + confFileName);
        JSONTokener tokener = new JSONTokener(fileIS);
        config = new JSONObject(tokener);
    }

    public void initClient() {
        tuneSSL();

        String baseUrl = (String) ((JSONObject) config.get("wordPress")).get("base_url");
        String username = (String) ((JSONObject) config.get("wordPress")).get("user");
        String password = (String) ((JSONObject) config.get("wordPress")).get("password");
        String openLoadApiLogin = (String) ((JSONObject) config.get("openLoad")).get("API_login");
        String openLoadApiKey = (String) ((JSONObject) config.get("openLoad")).get("API_Key");
        String loginVerifyCode = (String) ((JSONObject) config.get("openLoad")).get("loginVerifyCode");
        String openLoadCookie = (String) ((JSONObject) config.get("openLoad")).get("cookie");
        String openLoadLoginUrl = (String) ((JSONObject) config.get("openLoad")).get("loginUrl");
        String openLoadTakedownsUrl = (String) ((JSONObject) config.get("openLoad")).get("takedownsUrl");
        String openLoadClearAbusesUrl = (String) ((JSONObject) config.get("openLoad")).get("clearAbusesUrl");

        openLoadClient = new OpenLoadClient(openLoadApiLogin, openLoadApiKey, loginVerifyCode, openLoadCookie);

        boolean debug = false;
        wordPressClient = new WordpressCustomClient((Client) ClientFactory.fromConfig(ClientConfig.of(baseUrl, username, password, true, debug)));
        totalFoundPages = getTotalPages();
        try {
            openLoadWorkersCount = (Integer) ((JSONObject) config.get("openLoad")).get("workers");
            timeoutHours = (Integer) ((JSONObject) config.get("openLoad")).get("timeoutHours");
        } catch (JSONException e) {
            openLoadWorkersCount = 5;
            timeoutHours = 3;
        }

    }

    private int getTotalPages() {
        SearchRequest request = SearchRequest.Builder.aSearchRequest(Post.class)
                .withUri(Request.POSTS)
                .withParam("search", searchParameter)
                //.withParam("filter[s]", "[tab:Openload]")
                //.withParam("filter[meta_key]", "all.night.")
                .withParam("context", "edit")
                .withPagination(perPage, 1)
                .build();
        PagedResponse<Post> response = wordPressClient.search(request);

        int pages = 0;
        try {
            Field pagesField = Utils.getPrivateField(response.getClass(), "pages");
            pages = (Integer) pagesField.get(response);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return pages;
    }

    private void readPosts() throws InterruptedException {
        int callsCount = totalFoundPages;
       // int callsCount = 5;
        executor = Executors.newFixedThreadPool(wpWorkersCount);
        long before = System.currentTimeMillis();
        for (int i = 1; i <= callsCount; i++) {
            int finalI = i;
            Thread worker = new Thread(() -> {
                requestToWP(finalI);
            }, "worker_post_" + i);
            executor.submit(worker);
        }

//        try {
//            executor.shutdownNow();
//            if (!executor.awaitTermination(timeoutHours, TimeUnit.HOURS)) {
//                logger.info("Still waiting...");
//            }
//        } catch (InterruptedException e) {
//            logger.error(e.getMessage());
//        }

        //executor.shutdown();
        //while (!executor.isTerminated()) ;

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        executor.shutdownNow();
        long after = System.currentTimeMillis();
        logger.info("------- done reading posts. Found " + allPosts.size() + " posts. It took " + (after - before) + " ms.");
    }

    private void requestToWP(int startPage) {
        logger.info("------------ read Posts batch# " + startPage + " of " + totalFoundPages);
        try {
            SearchRequest request = SearchRequest.Builder.aSearchRequest(Post.class)
                    .withUri(Request.POSTS)
                    .withParam("search", searchParameter)
                    .withParam("context", "edit")
                    .withPagination(perPage, startPage)
                    .build();
            PagedResponse<Post> response = wordPressClient.search(request);
            allPosts.addAll(response.getList());
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    private void filterPosts() {
        allPosts = allPosts.stream().filter(post -> {
            String content = post.getContent().getRaw();
            String link = linkExtractor(content);
            try {
                if (isLinkValid(link)) {
                    return true;
                } else {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }).collect(toList());
        logger.info("------- filtering posts is done. Posts remains to check: " + allPosts.size());
    }

    private static void setSSLFactories(InputStream keyStream, String keyStorePassword, InputStream trustStream) throws Exception {
        // Get keyStore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        // if your store is password protected then declare it (it can be null however)
        char[] keyPassword = keyStorePassword.toCharArray();

        // load the stream to your store
        keyStore.load(keyStream, keyPassword);

        // initialize a trust manager factory with the trusted store
        KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyFactory.init(keyStore, keyPassword);

        // get the trust managers from the factory
        KeyManager[] keyManagers = keyFactory.getKeyManagers();

        // Now get trustStore
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());

        // if your store is password protected then declare it (it can be null however)
        //char[] trustPassword = password.toCharArray();

        // load the stream to your store
        trustStore.load(trustStream, null);

        // initialize a trust manager factory with the trusted store
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);

        // get the trust managers from the factory
        TrustManager[] trustManagers = trustFactory.getTrustManagers();

        // initialize an ssl context to use these managers and set as default
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(keyManagers, trustManagers, null);
        SSLContext.setDefault(sslContext);
    }

    private void tuneSSL() {
        try {
            File file = new File("localKeystore");
            if (!file.exists()) {
                InputStream link = Thread.currentThread().getContextClassLoader().getResourceAsStream("localKeystore");
                Files.copy(link, file.getAbsoluteFile().toPath());
            }
            System.setProperty("javax.net.ssl.trustStore", file.getAbsolutePath());
        } catch (Exception e) {
            logger.error(getStackTrace(e));
            System.exit(1);
        }
    }


    private void analyzeLinks() throws InterruptedException {
        logger.info("------- start verifying links");
        executor = Executors.newFixedThreadPool(openLoadWorkersCount);
        long before = System.currentTimeMillis();
        List<Future> tasks = Lists.newArrayList();
        for (Post post : allPosts) {
            String content = post.getContent().getRaw();
            String link = linkExtractor(content);
            Thread worker = new Thread(() -> {
                ResultPostAnalyse resultPost = new ResultPostAnalyse(post.getId(), link, openLoadClient.isLinkAlive(link));
                results.add(resultPost);
                logger.info(resultPost + ", request:" + results.size() + " of " + allPosts.size());
            }, "worker_post_" + System.identityHashCode(post));
            Future task = executor.submit(worker);
            tasks.add(task);
        }
        executor.shutdown();
        executor.awaitTermination(timeoutHours, TimeUnit.HOURS);
        executor.shutdownNow();
//        new TimeoutScheduler(10, TimeUnit.SECONDS, new ScheduledTask() {
//            @Override
//            public void run() {
//                System.out.println("------------ stopping...");
//                stopMe = true;
//            }
//        });
//        while (!executor.isTerminated()) {
//            //do process
//            if (stopMe) {
//                logger.info("Interrupted by timeout");
//                executor.shutdownNow();
//                return;
//            }
//        };
//
//        toolkit = Toolkit.getDefaultToolkit();
//        timer = new Timer();
//        timer.schedule(new RemindTask(), TimeUnit.HOURS.toMillis(timeoutHours));
//        while (!executor.isTerminated() && !timeToStop) {
//            //do process
//        };
        //executor.shutdownNow();
        long after = System.currentTimeMillis();
        logger.info("------- done verifying links. It took " + (after - before) + " ms.");
    }

    private Pattern linkValidationPattern = Pattern.compile("https:\\/\\/openload.co\\/embed\\/(.*?)\\/.*.");

    public boolean isLinkValid(String link) {
        try {
            Matcher matcher = linkValidationPattern.matcher(link);
            matcher.find();
            return matcher.matches();
        } catch (Exception e) {
            return false;
        }
    }

    public String linkExtractor(String content) {
        String resultLink = "";
        try {
            //Pattern pattern = Pattern.compile("[tab:Openload].*.<iframe (.+?)><\\/iframe>");
            Pattern pattern = Pattern.compile("\\[tab:Openload\\][\\r\\n]+<iframe(.+?)><\\/iframe>");
            Matcher matcher = pattern.matcher(content);
            matcher.find();
            String dirtyLink = matcher.group(1);
            pattern = Pattern.compile("src=\"(.+?)\"");
            matcher = pattern.matcher(dirtyLink);
            matcher.find();
            resultLink = matcher.group(1);
        } catch (Exception e) {
            logger.debug(e.getMessage());
        }
        return resultLink;
    }



    class ResultPostAnalyse {
        Long postId;
        String link;
        boolean alive;

        public ResultPostAnalyse(Long postId, String link, boolean alive) {
            this.postId = postId;
            this.link = link;
            this.alive = alive;
        }

        @Override
        public String toString() {
            return "Post {" +
                    "postId=" + postId +
                    ", link='" + link + '\'' +
                    ", alive=" + alive +
                    '}';
        }

        public Long getPostId() {
            return postId;
        }

        public void setPostId(Long postId) {
            this.postId = postId;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

        public boolean isAlive() {
            return alive;
        }

        public void setAlive(boolean alive) {
            this.alive = alive;
        }

        public String getReport() {
            return "postId: " + postId + ", link: " + link;
        }
    }

    class RemindTask extends TimerTask {
        public void run() {
            logger.info("Time's up!");
            toolkit.beep();
            timeToStop = true;
        }
    }
}
