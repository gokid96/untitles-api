package com.untitles.domain.email.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSender {

    private final SesV2Client sesV2Client;

    @Value("${aws.ses.from-email}")
    private String fromEmail;

    private static final int EXPIRATION_MINUTES = 10;

    @Async("emailTaskExecutor")
    public void sendAsync(String toEmail, String verificationCode) {
        try{
            String subject = "[untitles] 회원가입 인증 코드";
            String htmlBody = String.format("""
                <html>
                <body>
                    <h2>이메일 인증</h2>
                    <p>아래 인증 코드를 입력해주세요:</p>
                    <h1 style="color: #4A90D9;">%s</h1>
                    <p>%d분간 유효합니다.</p>
                </body>
                </html>
                """, verificationCode, EXPIRATION_MINUTES);

            SendEmailRequest request = SendEmailRequest.builder()
                    .fromEmailAddress(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(toEmail)
                            .build())
                    .content(EmailContent.builder()
                            .simple(Message.builder()
                                    .subject(Content.builder()
                                            .data(subject)
                                            .charset("UTF-8")
                                            .build())
                                    .body(Body.builder()
                                            .html(Content.builder()
                                                    .data(htmlBody)
                                                    .charset("UTF-8")
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build();

            sesV2Client.sendEmail(request);
            //기존 EmailService의 sendEmail 로직

            log.info("이메일 발송 완료: {}", toEmail);
        }catch (Exception e){
            log.error("이메일 발송 실패: {} - {}", toEmail, e.getMessage());
        }

    }
}
