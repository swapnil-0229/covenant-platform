package com.covenant.platform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    @Value("${spring.mail.username:onboarding@resend.dev}")
    private String fromEmail;

    @Async
    public void sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null) {
            log.warn("Cannot send mail. Recipient mail is null");
            return;
        }
        if (resendApiKey == null || resendApiKey.isEmpty()) {
            log.error("RESEND_API_KEY is not set.");
            return;
        }

        try {
            log.info("Sending mail to {} via Resend API..", toEmail);
            RestTemplate restTemplate = new RestTemplate();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("from", "Covenant Platform <" + fromEmail + ">");
            requestBody.put("to", List.of(toEmail));
            requestBody.put("subject", subject);
            requestBody.put("text", body);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.postForEntity("https://api.resend.com/emails", request, String.class);
            
            log.info("Mail sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send mail to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
