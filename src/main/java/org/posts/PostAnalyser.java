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
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.posts.model.Serie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.net.ssl.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.posts.Utils.*;

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
    private final static String ATTACHMENT_FILE_NAME = "bad_series.txt";
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

        if ("restore_abused_files".equals(parameter)) {
            analyzer.restoreAbusedFiles();
        }

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
                analyzer.sendMail("Posts analyzer report", emailBody);
            } else {
                emailBody = "Bad links list (" + badPosts.size() + "):\n";
                emailBody = emailBody + badPosts.stream().map(ResultPostAnalyse::getReport).collect(Collectors.joining("\n"));
                File attachment = analyzer.buildResultFile(badPosts);
                analyzer.sendMail("Posts analyzer report", emailBody, attachment);
                analyzer.putBadSeriesToGoogleSheet(badPosts);
                FileUtils.deleteQuietly(attachment);
            }
        }

        if ("grab_new_series".equals(parameter)) {
            try {
                List<Serie> announcedSeries = analyzer.getAnnouncedSeries();
                List<Serie> series = new Grabber().grabNewSeries(announcedSeries);
                series = analyzer.processNewSeries(series);
                analyzer.sendNewSeriesReport(series);
            } catch (Exception e) {
                logger.error(Utils.getStackTrace(e));
                return;
            }
        }

        if ("add_posts".equals(parameter)) {
            try {
                analyzer.addNewPosts();
            } catch (Exception e) {
                logger.error(Utils.getStackTrace(e));
                return;
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private void restoreAbusedFiles() {
        logger.info("Start Total restore from Sheet 'Abused'.");
        GoogleSheet googleSheet = new GoogleSheet();
        String sheetId = (String) ((JSONObject) config.get("abuses")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("abuses")).get("sheetName");
        String range = (String) ((JSONObject) config.get("abuses")).get("range");

        List<Abused> abused = new ArrayList<>();
        try {
            List<List<String>> dataFromSheet = googleSheet.getGoogleSheet(sheetId, sheetName, range.replace(":B", ":C"));
            for (List<String> row : dataFromSheet) {
                if (!isEmpty(row)) {
                    abused.add(new Abused(row.get(0), new Long(row.get(1))));
                }
            }
        } catch (IOException e) {
            logger.error(getStackTrace(e));
            System.exit(1);
        }

        if (isEmpty(abused)) {
            String emailBody = "No abused files found.";
            logger.info(emailBody);
            sendMail("Restore Abused report", emailBody);
            return;
        }

        sheetId = (String) ((JSONObject) config.get("catalog")).get("sheetId");
        sheetName = (String) ((JSONObject) config.get("catalog")).get("sheetName");
        range = (String) ((JSONObject) config.get("catalog")).get("range");
        List<String> catalog = new ArrayList<>();
        try {
            List<List<String>> dataFromSheet = googleSheet.getGoogleSheet(sheetId, sheetName, range);
            for (List<String> row : dataFromSheet) {
                if (!isEmpty(row)) {
                    catalog.add(row.get(0));
                }
            }
        } catch (IOException e) {
            logger.error(getStackTrace(e));
            System.exit(1);
        }

        logger.info("Total restore: Found {} files to restore. Restore process started.", abused.size());
        executor = Executors.newFixedThreadPool(openLoadWorkersCount);
        for (Abused abusedElement : abused) {
            for (String catFile : catalog) {
                if ( catFile.contains(abusedElement.fileName) || catFile.contains(encodeString(abusedElement.fileName)) || decodeString(catFile).contains(abusedElement.fileName) ) {
                    //uploadFileAndUpdatePost(catFile, abusedElement.postId);
                    Thread worker = new Thread(() -> {
                        w(1000);
                        abusedElement.restoredSuccessfully = uploadFileAndUpdatePost(catFile, abusedElement.postId);
                    }, "worker_post_" + System.identityHashCode(catFile));
                    executor.execute(worker);
                    //System.out.println("Upload " + catFile + " and update PostID " + abusedElement.postId);
                    break;
                }
            }
        }
        executor.shutdown();
        toolkit = Toolkit.getDefaultToolkit();
        timer = new Timer();
        timer.schedule(new RemindTask(), TimeUnit.HOURS.toMillis(2));
        while (!executor.isTerminated() && !timeToStop) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        executor.shutdownNow();

        logger.info("Restore process completed. Successfully restored {} files. Failed to restore {} files.", successFileNames.size(), failedFileNames.size());
        String emailBody = "";
        if (!isEmpty(successFileNames)) {
            emailBody = "Files restored successfully " + successFileNames.size() + " of total abused " + abused.size() + " :\n\n" + String.join("\n", successFileNames);
            sendMail("Files restored successfully", emailBody);
        }
        if (!isEmpty(failedFileNames)) {
            emailBody = "Files failed to restore " + failedFileNames.size() + " of total abused " + abused.size() + " :\n\n" + String.join("\n", failedFileNames);
            sendMail("Files failed to restore", emailBody);
        }

        //update Goolge Sheet 'Abused' with results (OK / FAILED)
        try {
            final String sheetIdGoogle = (String) ((JSONObject) config.get("abuses")).get("sheetId");
            final String sheetNameGoogle = (String) ((JSONObject) config.get("abuses")).get("sheetName");
            range = (String) ((JSONObject) config.get("abuses")).get("range");

            List<List<String>> dataFromSheet = googleSheet.getGoogleSheet(sheetIdGoogle, sheetNameGoogle, range.replace(":B", ":D"));
            List<Abused> rawAbused = new ArrayList<>();
            for (List<String> row : dataFromSheet) {
                if (isEmpty(row)) {
                    rawAbused.add(new Abused());
                } else {
                    rawAbused.add(new Abused(row.get(0), row.get(1)));
                }
            }
            List<List<String>> resultData = new ArrayList();
            for (Abused abusedElement : abused) {
                List<String> resultRow = new ArrayList<>();
                if (abusedElement.restoredSuccessfully) {
                    resultRow.add("OK");
                } else {
                    resultRow.add("FAILED");
                    String failReason = "";
                    try {
                        failReason = failedFileNames.stream().filter(f -> f.contains(abusedElement.fileName)).collect(toList()).get(0);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                    resultRow.add(failReason);
                }
                resultData.add(resultRow);
            }
            String rowIndex = range.split(":")[0].replaceAll("[^0-9]+", "");
            googleSheet.setGoogleData(sheetIdGoogle, sheetNameGoogle, "D" + rowIndex, resultData);

//            executor = Executors.newFixedThreadPool(2);
//            for (Abused abusedElement : abused) {
//                Optional<Abused> maybeFoundAbused = rawAbused.stream().filter(rA -> rA.postId.equals(abusedElement.postId)).findFirst();
//                if (maybeFoundAbused.isPresent()) {
//                    int index = rawAbused.indexOf(maybeFoundAbused.get());
//                    Thread worker = new Thread(() -> {
//                        try {
//                            w(500);
//                            googleSheet.setGoogleCell(sheetIdGoogle, sheetNameGoogle, "D" + (3 + index), abusedElement.restoredSuccessfully ? "OK" : "FAILED");
//                        } catch (IOException e) {
//                            logger.error(e.getMessage());
//                        }
//                    }, "worker_post_" + System.identityHashCode(abusedElement));
//                    executor.execute(worker);
//                }
//            }
//            executor.shutdown();
//            toolkit = Toolkit.getDefaultToolkit();
//            timer = new Timer();
//            timer.schedule(new RemindTask(), TimeUnit.HOURS.toMillis(2));
//            while (!executor.isTerminated() && !timeToStop) {
//                try {
//                    Thread.sleep(100);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            executor.shutdownNow();
        } catch (IOException e) {
            logger.error(getStackTrace(e));
            System.exit(1);
        }

        System.exit(0);
    }


    private void putBadSeriesToGoogleSheet(List<ResultPostAnalyse> badPosts) {
        List<List<String>> badSeries = new ArrayList();
        for (ResultPostAnalyse post : badPosts) {
            badSeries.add(new ArrayList() {{
                add(post.getFileName());
                add(post.getPostId().toString().replace("'", ""));
            }});
        }
        GoogleSheet googleSheet = new GoogleSheet();

        String sheetId = (String) ((JSONObject) config.get("abuses")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("abuses")).get("sheetName");
        String range = (String) ((JSONObject) config.get("abuses")).get("range");
        String start = range.split(":")[0].replaceAll("\\D+", "");
        Integer startRow = new Integer(start);
        Integer endRow = startRow + badSeries.size();
        String columnName = range.split(":")[0].replaceAll("[^a-zA-Z].*", "");
        range = range.split(":")[0] + ":" + "C" + endRow;
        try {
            googleSheet.setGoogleData(sheetId, sheetName, range, badSeries);
        } catch (IOException e) {
            logger.error(getStackTrace(e));
        }
    }

    private void putBadSeriesToGoogleSheet_old(List<ResultPostAnalyse> badPosts) {
        List<String> badSeries = new ArrayList();
        for (ResultPostAnalyse post : badPosts) {
            badSeries.add(post.getFileName());
        }
        GoogleSheet googleSheet = new GoogleSheet();

        String sheetId = (String) ((JSONObject) config.get("abuses")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("abuses")).get("sheetName");
        String range = (String) ((JSONObject) config.get("abuses")).get("range");
        String start = range.split(":")[0].replaceAll("\\D+", "");
        Integer startRow = new Integer(start);
        Integer endRow = startRow + badSeries.size();
        String columnName = range.split(":")[0].replaceAll("[^a-zA-Z].*", "");
        range = range.split(":")[0] + ":" + columnName + endRow;
        try {
            googleSheet.setGoogleColumn(sheetId, sheetName, range, new ArrayList<>(badSeries));
        } catch (IOException e) {
            logger.error(getStackTrace(e));
        }
    }

    private File buildResultFile(List<ResultPostAnalyse> badPosts) {
        File resultFile = new File(getSystemTempDir() + ATTACHMENT_FILE_NAME);
        StringBuilder badSeries = new StringBuilder();
        for (ResultPostAnalyse post : badPosts) {
            badSeries.append(post.getFileName()).append("\n");
        }
        writeStringToFile(badSeries.toString(), resultFile);
        return resultFile;
    }

    private void sendNewSeriesReport(List<Serie> series) {
        StringBuilder serieProcessed = new StringBuilder("Total posts processed: " + series.size() + "\n");
        for (Serie serie : series) {
            String result = serie.getSuccessUpdate() ? "Done" : "Failed : " + serie.getErrorMessage();
            serieProcessed.append("\n " + serie.getFullName() + " : " + result);
        }
        String emailBody = serieProcessed.toString();
        sendMail("Updated posts with new series", emailBody);
    }

    /**
     * Uploads serie to OpenLoad
     * Updates Posts in WordPress using embed link from OpenLoadResponse (posts should be prepared earlier)
     * Adds this embed link to GoogleSheet 'Add New Posts'
     */
    private List<Serie> processNewSeries(List<Serie> series) {
        String sheetId = (String) ((JSONObject) config.get("updatePreparedPosts")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("updatePreparedPosts")).get("sheetName");
        //String range = (String) ((JSONObject) config.get("updatePreparedPosts")).get("range");
        String range = "A2:M1000";
        GoogleSheet googleSheet = new GoogleSheet();
        List<Serie> seriesFromSheet = new ArrayList<>();

        try {
            List<List<String>> dataFromSheet = googleSheet.getGoogleSheet(sheetId, sheetName, range);
            for (List<String> row : dataFromSheet) {
                Serie serie = buildSerie(row);
                if (serie != null) {
                    seriesFromSheet.add(serie);
                }
            }
        } catch (IOException e) {
            logger.warn(getStackTrace(e));
            System.exit(1);
        }

        for (Serie serie : series) {
            OpenLoadClient.OpenLoadResponse uploadResult = null;
            if (isEmpty(serie.getOpenLoadLinks())) {
                String errorLine = String.format("Serie '%s' was not found on 'www1.swatchseries.to'", serie.getFullName());
                logger.warn(errorLine);
                serie.setErrorMessage(errorLine);
                serie.setSuccessUpdate(false);
                continue;
            }
            for (URL url : serie.getOpenLoadLinks()) {
                try {
                    uploadResult = openLoadClient.uploadFile(url.toString(), "");
                    break;
                } catch (OpenLoadException e) {
                    logger.warn("OpenLoad link '{}' is bad or unable to upload: {}", url, e.getMessage());
                }
            }

            if (uploadResult == null) {
                String errorLine = String.format("Error uploading file '%s' to OpenLoad. All Openload links are dead.", serie.getFullName());
                logger.warn(errorLine);
                serie.setErrorMessage(errorLine);
                serie.setSuccessUpdate(false);
            } else {
                try {
                    String postContent = "[tab:Openload]\n" + uploadResult.embedLink;
                    Long postId = new Long(serie.getPostId());
                    Post updatedPost = wordPressClient.getPost(postId);
                    updatedPost.setContent(ContentBuilder.aContent().withRendered(postContent).build());
                    wordPressClient.updatePost(updatedPost);
                    //googleSheet.setGoogleCell(sheetId, sheetName, "B" + (getSerieIndex(seriesFromSheet, serie)), uploadResult.embedLink);
                    googleSheet.setGoogleCell(sheetId, sheetName, "B" + (getSerieIndex(seriesFromSheet, serie)), uploadResult.openLoadLink);
                    String successLine = String.format("Post '%d' updated OK.", postId);
                    logger.info(successLine);
                    serie.setSuccessUpdate(true);
                } catch (Exception e) {
                    String errorLine = String.format("Error updating Post %s: " + e.getMessage(), serie.getPostId());
                    logger.warn(errorLine);
                    serie.setErrorMessage(errorLine);
                    serie.setSuccessUpdate(false);
                }
            }
        }
        return series;
    }

    private int getSerieIndex(List<Serie> series, Serie serie) {
        int index = 2;
        for (Serie serieIteration : series) {
            if (serieIteration.getPostId().equals(serie.getPostId())) {
                break;
            }
            index++;
        }
        return index;
    }

    private List<Serie> getAnnouncedSeries() {
        String sheetId = (String) ((JSONObject) config.get("updatePreparedPosts")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("updatePreparedPosts")).get("sheetName");
        String range = (String) ((JSONObject) config.get("updatePreparedPosts")).get("range");
        GoogleSheet sheet = new GoogleSheet();
        List<List<String>> newPostsData = Lists.newArrayList();
        try {
            newPostsData = sheet.getGoogleSheet(sheetId, sheetName, range);
        } catch (IOException e) {
            logger.error(getStackTrace(e));
            String emailBody = "Error reading Catalog Google sheet: \n" + getStackTrace(e);
            sendMail("Restore Abused report", emailBody);
            System.exit(1);
        }

        List<Serie> result = new ArrayList<>();
        for (List<String> row : newPostsData) {
            int index = newPostsData.indexOf(row);
            if (isEmpty(row)
                    || row.size() < 11
                    || isEmpty(row.get(6))
                    || isEmpty(row.get(8))
                    || isEmpty(row.get(10))
                    || isEmpty(row.get(2))
                    || !row.get(1).trim().equalsIgnoreCase("- sorry try again later -")
                    ) {
                logger.warn("Incorrect data in row #" + index);
                continue;
            }
            Serie serie = buildSerie(row);
            if (serie != null) {
                result.add(serie);
            }
        }
        return result;
    }

    private Serie buildSerie(List<String> row) {
        Serie serie = null;
        try {
            serie = new Serie();
            serie.setName(row.get(6));
            serie.setSeason(row.get(8));
            serie.setEpisode(row.get(10));
            serie.setPostId(row.get(2));
            serie.setTitle(row.get(12).trim());
        } catch (Exception e) {
            logger.warn(e.getMessage());
        }
        return serie;
    }

    private void addNewPosts() {
        logger.info("------- start creating new Posts");
        String sheetId = (String) ((JSONObject) config.get("newPosts")).get("sheetId");
        String sheetName = (String) ((JSONObject) config.get("newPosts")).get("sheetName");
        String range = (String) ((JSONObject) config.get("newPosts")).get("range");
        GoogleSheet sheet = new GoogleSheet();
        List<List<String>> newPostsData = Lists.newArrayList();
        try {
            newPostsData = sheet.getGoogleSheet(sheetId, sheetName, range);
        } catch (IOException e) {
            logger.error(getStackTrace(e));
            String emailBody = "Error reading Catalog Google sheet: \n" + getStackTrace(e);
            sendMail("Restore Abused report", emailBody);
            return;
        }
        List<Post> newlyCreatedPosts = Lists.newArrayList();
        for (List<String> row : newPostsData) {
            if (isEmpty(row)
                    || row.size() < 2
                    || isEmpty(row.get(0))
                    || isEmpty(row.get(1))) {
                continue;
            }
            int index = newPostsData.indexOf(row);
            String postTitle = row.get(0);
            String postContent = "[tab:Openload]\n" + row.get(1);
            Post createdPost = wordPressClient.addPost(postTitle, postContent);
            String postId = createdPost.getId().toString();
            String permaLink = createdPost.getLink();
            try {
                sheet.setGoogleCell(sheetId, sheetName, "C" + (index + 2), postId);
                sheet.setGoogleCell(sheetId, sheetName, "E" + (index + 2), permaLink);
            } catch (IOException e) {
                logger.warn(getStackTrace(e));
            }
            newlyCreatedPosts.add(createdPost);
        }

        if (!isEmpty(newlyCreatedPosts)) {
            StringBuilder newPosts = new StringBuilder("Total new posts: " + newlyCreatedPosts.size() + "\n");
            for (Post post : newlyCreatedPosts) {
                newPosts.append("\n ID: " + post.getId() + ", Link: " + post.getLink());
            }
            String emailBody = newPosts.toString();
            sendMail("New posts created", emailBody);
        }
        logger.info("------- done creating new Posts: " + newlyCreatedPosts.size() + " posts created.");
    }

    private void saveAbusedLocally(List<String> abusedFiles) {
        String abusedToSave = String.join("\n", abusedFiles);
        File jarFile;
        try {
            jarFile = new File(PostAnalyser.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File file = new File(jarFile.getParentFile() + File.separator + ABUSED_BACKUP_FOLDER + new SimpleDateFormat("dd-MM-yyyy_HH.mm.ss").format(new Date()) + "___" + abusedFiles.size() + ".txt");
            writeStringToFile(abusedToSave, file);
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
        for (String fileToRestore : filesToRestore) {
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
        }
        ;
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

    private boolean uploadFileAndUpdatePost(String fileToUpload, Long postId) {
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
                return false;
            }

            fileName = uploadResult.fileName;
            Post post = wordPressClient.getPost(postId);
            String newContent = post.getContent().getRaw();
            postId = post.getId();
            String oldEmbedLink = newContent.substring(newContent.indexOf("<iframe"), newContent.indexOf("</iframe>") + "</iframe>".length());
            newContent = newContent.replace(oldEmbedLink, uploadResult.embedLink);
            post.setContent(ContentBuilder.aContent().withRendered(newContent).build());
            wordPressClient.updatePost(post);
            String successLine = String.format("File '%s' restored OK. PostID: %d", fileName, postId);
            logger.info(successLine);
            successFileNames.add(successLine);
            return true;
        } catch (Exception e) {
            String infoLine = String.format("Failed to update WordPress post. PostID: %d, file name: '%s'. %s", postId, fileName, e.getMessage());
            logger.info(infoLine);
            failedFileNames.add(infoLine);
            return false;
        }
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

    public List<Post> tryHardCoreWordPressSearch(String fileName) {
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


    public void sendMail(String subject, String emailBody, File attachment) {
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
            EmailUtil.sendEmail(session, fromEmail, toEmail, subject, emailBody, attachment);
        } catch (Exception e) {
            logger.error("Email was no sent: " + e.getMessage());
        }

    }

    public void sendMail(String subject, String emailBody) {
        sendMail(subject, emailBody, null);
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
        try {
            totalFoundPages = getTotalPages();
        } catch (Exception e) {
            String exceptionText = e.getMessage();
            if (exceptionText.contains("timestamp check failed") ||
                    exceptionText.contains("validity check failed")) {
                logger.warn("Certificate in WP is invalid. Use non-secure connection.");
                baseUrl = (String) ((JSONObject) config.get("wordPress")).get("base_url_no_secure");
                wordPressClient = new WordpressCustomClient((Client) ClientFactory.fromConfig(ClientConfig.of(baseUrl, username, password, true, debug)));
                totalFoundPages = getTotalPages();
            } else {
                logger.error(getStackTrace(e));
                System.exit(1);
            }
        }
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
                file = new File("localKeystore");
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
                resultPost.setFileName(link.replaceAll("https:\\/\\/openload.co\\/embed\\/(.*)\\/", ""));
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
        private Long postId;
        private String link;
        private String fileName;
        private boolean alive;

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

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ResultPostAnalyse that = (ResultPostAnalyse) o;
            return alive == that.alive &&
                    Objects.equals(postId, that.postId) &&
                    Objects.equals(link, that.link) &&
                    Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(postId, link, fileName, alive);
        }
    }

    class RemindTask extends TimerTask {
        public void run() {
            logger.info("Time's up!");
            toolkit.beep();
            timeToStop = true;
        }
    }

    class CatalogFile {
        String openLoadLink;
        String fileName;

        public CatalogFile(String openLoadLink, String fileName) {
            this.openLoadLink = openLoadLink;
            this.fileName = fileName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CatalogFile that = (CatalogFile) o;
            return Objects.equals(openLoadLink, that.openLoadLink) &&
                    Objects.equals(fileName, that.fileName);
        }

        @Override
        public int hashCode() {

            return Objects.hash(openLoadLink, fileName);
        }

        @Override
        public String toString() {
            return "CatalogFile{" +
                    "openLoadLink='" + openLoadLink + '\'' +
                    ", fileName='" + fileName + '\'' +
                    '}';
        }
    }

    class Abused {
        String fileName;
        Long postId = 0L;
        Boolean restoredSuccessfully = false;

        public Abused() {
        }

        public Abused(String fileName, Long postId) {
            this.fileName = fileName;
            this.postId = postId;
        }

        public Abused(String fileName, String postId) {
            this.fileName = fileName;
            try {
                this.postId = new Long(postId);
            } catch (NumberFormatException e) {
                logger.error(getStackTrace(e));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Abused abused = (Abused) o;
            return Objects.equals(fileName, abused.fileName) &&
                    Objects.equals(postId, abused.postId) &&
                    Objects.equals(restoredSuccessfully, abused.restoredSuccessfully);
        }

        @Override
        public int hashCode() {
            return Objects.hash(fileName, postId, restoredSuccessfully);
        }

        @Override
        public String toString() {
            return "Abused {" +
                    "fileName='" + fileName + '\'' +
                    ", postId=" + postId +
                    ", restoredSuccessfully=" + restoredSuccessfully +
                    '}';
        }
    }
}
