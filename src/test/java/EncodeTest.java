import org.junit.Test;
import org.posts.Utils;

import java.net.URLEncoder;

public class EncodeTest {


    @Test
    public void testUpload() throws Exception {
        String fileName = "Fresh_Off_the_Boat_S02E24_720p_~{KiNg}.mkv.mp4";
        String fileName_encoded = URLEncoder.encode(fileName, "UTF-8");
        System.out.println(fileName_encoded);

        fileName_encoded = Utils.encodeString(fileName);
        System.out.println(fileName_encoded);

        fileName = Utils.decodeString(fileName_encoded);
        System.out.println(fileName);
    }


}
