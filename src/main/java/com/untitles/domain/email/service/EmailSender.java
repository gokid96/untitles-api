package com.untitles.domain.email.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

/**
 * 메일 발송. app.mail.provider로 수단을 고른다.
 * - log: 실제로 보내지 않고 로그만 남긴다 (기본값, 개발용)
 * - smtp: spring.mail.* 설정의 SMTP 서버 (Gmail, 네이버 등)
 * - ses: AWS SES
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private static final int EXPIRATION_MINUTES = 10;

    private final ObjectProvider<SesV2Client> sesV2Client;
    private final ObjectProvider<JavaMailSender> javaMailSender;

    @Value("${app.mail.provider:log}")
    private String provider;

    @Value("${app.mail.from:noreply@untitles.net}")
    private String fromEmail;

    /**
     * 메일 발송 수단이 설정돼 있는지 (log면 실제로 보내지 않음)
     */
    public boolean isEnabled() {
        return !"log".equalsIgnoreCase(provider);
    }

    @Async("emailTaskExecutor")
    public void sendAsync(String toEmail, String verificationCode) {
        deliver(toEmail, "[untitles] 회원가입 인증 코드",
                codeBody("이메일 인증", "아래 인증 코드를 입력해주세요:", verificationCode));
    }

    @Async("emailTaskExecutor")
    public void sendPasswordResetAsync(String toEmail, String code) {
        deliver(toEmail, "[untitles] 비밀번호 재설정 인증 코드",
                codeBody("비밀번호 재설정", "비밀번호를 재설정하려면 아래 인증 코드를 입력해주세요."
                        + " 요청하지 않았다면 이 메일은 무시하셔도 됩니다.", code));
    }

    private static String codeBody(String title, String message, String code) {
        return String.format("""
                <html>
                <body>
                    <h2>%s</h2>
                    <p>%s</p>
                    <h1 style="color: #4A90D9;">%s</h1>
                    <p>%d분간 유효합니다.</p>
                </body>
                </html>
                """, title, message, code, EXPIRATION_MINUTES);
    }

    private void deliver(String toEmail, String subject, String htmlBody) {
        try {
            switch (provider.toLowerCase()) {
                case "smtp" -> sendSmtp(toEmail, subject, htmlBody);
                case "ses" -> sendSes(toEmail, subject, htmlBody);
                default -> {
                    log.warn("메일 발송 수단이 설정되지 않아 발송하지 않음 (MAIL_PROVIDER): {} / {}", toEmail, subject);
                    return;
                }
            }
            log.info("이메일 발송 완료: {}", toEmail);
        } catch (Exception e) {
            log.error("이메일 발송 실패: {} - {}", toEmail, e.getMessage());
        }
    }

    private void sendSmtp(String toEmail, String subject, String htmlBody) throws Exception {
        JavaMailSender sender = javaMailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException("SMTP 설정(SPRING_MAIL_HOST)이 없습니다");
        }
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        sender.send(message);
    }

    private void sendSes(String toEmail, String subject, String htmlBody) {
        SesV2Client client = sesV2Client.getObject();
        client.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder().toAddresses(toEmail).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                        .build())
                                .build())
                        .build())
                .build());
    }
}
