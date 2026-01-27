package com.untitles.global.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;


/**
 * Rate Limiting 설정
 * IP 기반으로 요청 횟수를 제한
 */
@Configuration
public class RateLimitConfig {

    /**
     * IP별 Bucket을 저장하는 캐시
     * 실제 프로덕션에서는 Redis 등을 사용하는 것이 좋음
     */
    @Bean
    public Cache<String, Bucket> rateLimitBuckets() {
        return Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(10000)
                .build();
    }

    /**
     * 일반 API 요청용 Bucket 생성
     * 분당 60회 제한
     */
    public static Bucket createDefaultBucket() {
        Bandwidth limit = Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * 로그인 API용 Bucket 생성 (더 엄격한 제한)
     * 분당 10회 제한 (Brute Force 방지)
     */
    public static Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * 이메일 발송 API용 Bucket 생성 (매우 엄격한 제한)
     * 시간당 5회 제한
     */
    public static Bucket createEmailBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }
}
