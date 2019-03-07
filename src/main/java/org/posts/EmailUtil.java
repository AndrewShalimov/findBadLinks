package org.posts;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.Date;

public class EmailUtil {

    /**
     * Utility method to send simple HTML email
     *
     * @param session
     * @param toEmail
     * @param subject
     * @param body
     */
    public static void sendEmail(Session session, String fromEmail, String toEmail, String subject, String body) throws MessagingException {
        sendEmail(session, fromEmail, toEmail, subject, body, null);
    }

    public static void sendEmail(Session session, String fromEmail, String toEmail, String subject, String body, File attachment) throws MessagingException {
        MimeMessage msg = new MimeMessage(session);
        //set message headers
        msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
        msg.addHeader("format", "flowed");
        msg.addHeader("Content-Transfer-Encoding", "8bit");
        msg.setFrom(new InternetAddress(fromEmail));
        msg.setReplyTo(InternetAddress.parse(fromEmail, false));
        msg.setSubject(subject, "UTF-8");
        msg.setSentDate(new Date());
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));

        if (attachment != null) {
            MimeBodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(body, "UTF-8");
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);

            // Part two is attachment
            messageBodyPart = new MimeBodyPart();
            String fileName = attachment.getAbsolutePath();
            String emailFileName = attachment.getName();
            DataSource source = new FileDataSource(fileName);
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName(emailFileName);
            multipart.addBodyPart(messageBodyPart);

            msg.setContent(multipart);
        } else {
            msg.setText(body, "UTF-8");
        }

        Transport.send(msg);
    }
}