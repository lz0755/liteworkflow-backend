package com.liteworkflow.infra.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.liteworkflow.infra.notification.NotificationProperties;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpEmailSenderTest {

    @Test
    void createsUtf8MultipartAlternativeMailForMailHogCompatibleSmtp() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        NotificationProperties properties = new NotificationProperties();
        properties.getEmail().setFrom("no-reply@liteworkflow.local");
        SmtpEmailSender sender = new SmtpEmailSender(mailSender, properties);

        sender.send(
                "recipient@example.test",
                new RenderedEmail(
                        "A built-in subject",
                        "Plain text body",
                        "<p>HTML body</p>"));

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("A built-in subject");
        assertThat(message.getRecipients(Message.RecipientType.TO))
                .extracting(Object::toString)
                .containsExactly("recipient@example.test");
        assertThat(message.getContentType()).containsIgnoringCase("multipart/");
    }
}
