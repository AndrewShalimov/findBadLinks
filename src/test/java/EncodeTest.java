import org.junit.Test;

import java.net.URLEncoder;

public class EncodeTest {


    @Test
    public void testUpload() throws Exception {
        String fileName = "Ballers_S01E09_720p~{KiNg}.mkv.mp4";
        String fileName_encoded = URLEncoder.encode(fileName, "UTF-8");
        System.out.println(fileName_encoded);
    }


}
