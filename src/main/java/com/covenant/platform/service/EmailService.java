package com.covenant.platform.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender javaMailSender;

    public void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null) {
            log.warn("Cannot send mail. Recipient mail is null");
            return;
        }
        try {
            log.info("Sending mail to {}..", toEmail);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);

            javaMailSender.send(message);
            log.info("Mail sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send mail to {}: {}", toEmail, e.getMessage(), e);
        }
    }

}
