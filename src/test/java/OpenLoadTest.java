public class OpenLoadTest {

//
//    @Before
//    public void init() {
//        client = new OpenLoadClient(API_login, API_Key, loginVerifyCode, cookie);
//        classLoader = getClass().getClassLoader();
//        objectMapper = new ObjectMapper();
//    }
//
//
//    //@Test
//    public void testUpload() throws Exception {
//        String fileToUpload = "https://openload.co/f/xwqxa5ibSuk/the.last.man.on.earth.s04e04.web.x264-tbs.mkv.mp4";
//        OpenLoadClient.OpenLoadResponse uploadResponse = client.uploadFile(fileToUpload, null);
//        System.out.println(uploadResponse);
//    }
//
//    //@Test
//    public void testBuildResponseObject() throws Exception {
//        String testResponse = "{\"105934750\":{\"bytes_loaded\":null,\"added\":\"2018-06-07 18:31:25\",\"last_update\":\"2018-06-07 18:31:25\",\"remoteurl\":\"https://openload.co/f/xwqxa5ibSuk/the.last.man.on.earth.s04e04.web.x264-tbs.mkv.mp4\",\"id\":\"105934750\",\"bytes_total\":null,\"extid\":\"uh-WsnVbid8\",\"folderid\":\"3827129\",\"url\":\"https://openload.co/f/uh-WsnVbid8\",\"status\":\"finished\"}}";
//        String remoteUploadId = "105934750";
//        JSONObject resultJson = new JSONObject(testResponse).getJSONObject(remoteUploadId);
//        OpenLoadUploadStatus result = objectMapper.convertValue(resultJson.toMap(), OpenLoadUploadStatus.class);
//        System.out.println(result);
//    }



}
