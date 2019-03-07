import com.google.common.collect.Lists;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.posts.GoogleSheet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.posts.Utils.getStackTrace;

public class GoogleTest {

    private GoogleSheet googleSheet;
    private ClassLoader classLoader;

    @Before
    public void init() {
        googleSheet = new GoogleSheet();
        classLoader = getClass().getClassLoader();
    }

    @Test
    public void read_test_data() {
        String sheetId = "1GyBjpFrhetXLuxibq2vt5PcrBmLPnUqOlLgl_9UuDo8";
        String sheetName = "Add New Post";
        String range = "A2:B100";

        List<List<String>> data = Lists.newArrayList();
        try {
            data = googleSheet.getGoogleSheet(sheetId, sheetName, range);
            System.out.println(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void write_test_data() {
        String sheetId = "1GyBjpFrhetXLuxibq2vt5PcrBmLPnUqOlLgl_9UuDo8";
        String sheetName = "Update Post";
        String range = "B2";

        try {
            googleSheet.setGoogleCell(sheetId, sheetName, range, "test");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void write_test_column() {
        String sheetId = "1GyBjpFrhetXLuxibq2vt5PcrBmLPnUqOlLgl_9UuDo8";
        String sheetName = "Abuses";
        String range = "B3:B13";
        List<String> data = new ArrayList() {{
            add("S.W.A.T.2017.S02E14.HDTV.x264-KILLERS.mkv");
            add("siren.2018.s02e03.web.x264-tbs.mkv");
            add("6dqygsRoI04");
            add("Married.At.First.Sight.AU.S06E09.720p.WEB-DL.AAC2.0.H.264-BTN.mp4");
            add("Suits.S08E13.HDTV.x264-BFF.mkv");
            add("the.simpsons.s30e13.web.x264-tbs.mkv");
            add("5VE7BYr8vtg");
            add("family.guy.s17e12.720p.web.x264-tbs.mkv");
            add("zvxgqmuPDcQ");
            add("bobs.burgers.s09e13.720p.web.x264-tbs.mkv");
        }};

        try {
            googleSheet.setGoogleColumn(sheetId, sheetName, range, new ArrayList<>(data));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void write_test_data_two_columns() {
        String sheetId = "1GyBjpFrhetXLuxibq2vt5PcrBmLPnUqOlLgl_9UuDo8";
        String sheetName = "Abuses";
        String range = "B3:C13";
        List<List<String>> data = asList(
                asList("S.W.A.T.2017.S02E14.HDTV.x264-KILLERS.mkv", "947209"),
                asList("siren.2018.s02e03.web.x264-tbs.mkv", "947195"),
                asList("6dqygsRoI04", "947202"),
                asList("Married.At.First.Sight.AU.S06E09.720p.WEB-DL.AAC2.0.H.264-BTN.mp4", "947190"),
                asList("Suits.S08E13.HDTV.x264-BFF.mkv", "945916"),
                asList("the.simpsons.s30e13.web.x264-tbs.mkv", "948613"),
                asList("5VE7BYr8vtg", "948609"),
                asList("family.guy.s17e12.720p.web.x264-tbs.mkv", "948606"),
                asList("zvxgqmuPDcQ", "948605"),
                asList("bobs.burgers.s09e13.720p.web.x264-tbs.mkv", "948615")
                );

        try {
            googleSheet.setGoogleData(sheetId, sheetName, range, data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Test
    public void write_test_data_two_columns_test_range() {
        String sheetId = "1GyBjpFrhetXLuxibq2vt5PcrBmLPnUqOlLgl_9UuDo8";
        String sheetName = "Abuses";
        String range = "D3";
        List<List<String>> data = asList(
                asList("FAILED", "_reason_"),
                asList("FAILED", "_reason_"),
                asList("FAILED", "_reason_"),
                asList("OK", "_reason_"),
                asList("OK", "_reason_"),
                asList("FAILED", "_reason_"),
                asList("FAILED", "_reason_"),
                asList("FAILED", "_reason_"),
                asList("OK", "_reason_"),
                asList("OK", "_reason_")
        );

        try {
            googleSheet.setGoogleData(sheetId, sheetName, range, data);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
