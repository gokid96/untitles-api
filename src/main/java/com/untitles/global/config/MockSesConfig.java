package com.untitles.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

@Profile("test")
@Configuration
public class MockSesConfig {

    @Bean
    @Primary
    public SesV2Client sesV2Client() {
        return new SesV2Client() {
            @Override
            public SendEmailResponse sendEmail(SendEmailRequest request) {
                return SendEmailResponse.builder()
                        .messageId("mock-message-id")
                        .build();
            }

            @Override
            public String serviceName() {
                return "mock-ses";
            }

            @Override
            public void close() {}
        };
    }
}