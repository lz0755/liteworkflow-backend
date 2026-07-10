package com.liteworkflow.identity.infrastructure;

import com.liteworkflow.identity.config.IdentityProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetMailSender.class);

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    public PasswordResetMailSender(JavaMailSender mailSender, IdentityProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void send(PasswordResetMailRequested event) {
        try {
            String separator = properties.getPasswordResetUrl().contains("?") ? "&" : "?";
            String url = properties.getPasswordResetUrl() + separator + "token="
                    + URLEncoder.encode(event.rawToken(), StandardCharsets.UTF_8);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.email());
            message.setSubject("Reset your liteworkflow password");
            message.setText("Use this one-time password reset link before " + event.expiresAt() + ":\n" + url);
            mailSender.send(message);
        } catch (Exception exception) {
            // Never put email addresses, reset URLs, or raw tokens in logs.
            log.warn("Password reset delivery failed for userId={}", event.userId());
        }
    }
}
