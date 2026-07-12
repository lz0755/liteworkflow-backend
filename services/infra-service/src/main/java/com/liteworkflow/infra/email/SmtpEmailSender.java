package com.liteworkflow.infra.email;

import com.liteworkflow.infra.notification.NotificationProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final NotificationProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, NotificationProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipientAddress, RenderedEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(properties.getEmail().getFrom());
            helper.setTo(recipientAddress);
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), email.htmlBody());
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            throw new EmailSendException(exception);
        }
    }

    private static final class EmailSendException extends RuntimeException {
        private EmailSendException(Throwable cause) {
            super("SMTP delivery failed", cause);
        }
    }
}
