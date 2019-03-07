package org.posts;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GoogleSheet {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private static final String APPLICATION_NAME = "Google Sheets Example";
    private static final String GOOGLE_CONF_FILE_NAME = "client_secret.json";
    private static final String CREDENTIALS_FOLDER = "credentials"; // Directory to store user credentials.
    private Sheets sheetService = null;

    private Credential authorize() throws IOException, GeneralSecurityException, URISyntaxException {
        File jarFile = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        FileInputStream fileIS = new FileInputStream(jarFile.getParentFile() + File.separator + GOOGLE_CONF_FILE_NAME);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JacksonFactory.getDefaultInstance(), new InputStreamReader(fileIS));

        List<String> scopes = Arrays.asList(SheetsScopes.SPREADSHEETS);
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow
                .Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory.getDefaultInstance(), clientSecrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(CREDENTIALS_FOLDER)))
                .setAccessType("offline")
                .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        return credential;
    }


    public Sheets getSheetsService() {
        try {
            if (sheetService == null) {
                Credential credential = authorize();
                sheetService = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        JacksonFactory.getDefaultInstance(), credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return sheetService;
    }

    public List<String> getFilmCatalog(String spreadsheetId, String tabName, String cellsRange) throws IOException {
        List<List<Object>> values = getGoogleSheetData(spreadsheetId, tabName, cellsRange);
        List<String> result = values.stream().map(val -> val.get(0).toString()).collect(Collectors.toList());
        return result;
    }

    public List<List<Object>> getGoogleSheetData(String spreadsheetId, String tabName, String cellsRange) throws IOException {
        String range = tabName + "!" + cellsRange;
        ValueRange response = getSheetsService().spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> values = response.getValues();
        return values;
    }

    public List<List<String>> getGoogleSheet(String spreadsheetId, String tabName, String cellsRange) throws IOException {
        String range = tabName + "!" + cellsRange;
        ValueRange response = getSheetsService().spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();

        List<List<Object>> rawValues = response.getValues();
        List<List<String>> result = rawValues.stream().map(val -> val.stream().map(row -> row.toString()).collect(Collectors.toList())).collect(Collectors.toList());
        return result;
    }

    /**
     * @param spreadsheetId
     * @param tabName
     * @param cellsAddress  - e.g. A4
     * @param data
     * @throws IOException
     */
    public UpdateValuesResponse setGoogleCell(String spreadsheetId, String tabName, String cellsAddress, String data) throws IOException {
        String range = tabName + "!" + cellsAddress;
        List<List<Object>> values = Arrays.asList(
                Arrays.asList(
                        data
                )
        );
        ValueRange body = new ValueRange().setValues(values);
        UpdateValuesResponse result =
                getSheetsService().spreadsheets().values().update(spreadsheetId, range, body)
                        .setValueInputOption("RAW")
                        .execute();
        return result;
    }

    public UpdateValuesResponse setGoogleColumn(String spreadsheetId, String tabName, String cellsAddress, List<Object> data) throws IOException {
        String range = tabName + "!" + cellsAddress;
//        ValueRange body = new ValueRange()
//                .setValues(Arrays.asList(
//                        Arrays.asList("Expenses January"),
//                        Arrays.asList("books"),
//                        Arrays.asList("pens"),
//                        Arrays.asList("Expenses February"),
//                        Arrays.asList("clothes"),
//                        Arrays.asList("shoes")));
        ValueRange body = new ValueRange()
                .setValues(
                        data.stream()
                                .map(d -> Arrays.asList(d)).collect(Collectors.toList())
                );

        UpdateValuesResponse result =
                getSheetsService().spreadsheets().values().update(spreadsheetId, range, body)
                        .setValueInputOption("RAW")
                        .execute();
        return result;
    }

    public UpdateValuesResponse setGoogleData(String spreadsheetId, String tabName, String cellsAddress, List<List<String>> data) throws IOException {
        String range = tabName + "!" + cellsAddress;
        ValueRange body = new ValueRange()
                .setValues(data.stream()
                        .map(list -> list.stream()
                                .map(element -> getStringAsObject(element))
                                .collect(Collectors.toList()))
                        .collect(Collectors.toList()));

        UpdateValuesResponse result =
                getSheetsService().spreadsheets().values().update(spreadsheetId, range, body)
                        .setValueInputOption("RAW")
                        .execute();
        return result;
    }

    private Object getStringAsObject(String string) {
        Object obj = string;
        return obj;
    }

}
