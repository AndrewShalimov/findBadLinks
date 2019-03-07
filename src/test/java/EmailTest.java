import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.posts.EmailUtil;
import org.posts.PostAnalyser;

import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import java.io.File;
import java.net.URLEncoder;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.posts.Utils.readStringFromFile;

public class EmailTest {

    private ClassLoader classLoader;

    @Before
    public void init() {
        classLoader = getClass().getClassLoader();
    }

    @Test
    public void testEmailSending() throws Exception {
        String emailBody = "Bad links list:\n";
        String textPart = readStringFromFile(new File(classLoader.getResource("bad_series_body.txt").getFile()).getAbsolutePath());
        emailBody = emailBody + textPart;
        File attachment = new File(classLoader.getResource("bad_series_file.txt").getFile());
        sendMail("Posts analyzer report", emailBody, attachment);
        FileUtils.deleteQuietly(attachment);
    }

    public void sendMail(String subject, String emailBody, File attachment) {
        String smtpHost = "smtp.gmail.com";
        String smtpPort = "465";
        String user = "sergeeva.dmca@gmail.com";
        String password = "A4mcAV)lNU=hPzL&99";
        String toEmail = "shalimandr@gmail.com";
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
            e.printStackTrace();
        }

    }

}
