package com.covenant.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Async
    public void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null) {
            log.warn("Cannot send mail. Recipient mail is null");
            return;
        }
        if (fromEmail == null || fromEmail.isEmpty()) {
            log.error("EMAIL_USERNAME is not set. Skipping email to {}", toEmail);
            return;
        }

        try {
            log.info("Sending mail to {} from {} via SMTP..", toEmail, fromEmail);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            log.info("Mail sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send mail to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
