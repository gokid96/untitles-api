package com.untitles.domain.image.service;

import com.untitles.domain.image.repository.UploadedImageRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 어디에서도 쓰이지 않는 이미지를 R2에서 지운다 (매일 새벽 4시 30분).
 * - 글 이미지(posts/): 휴지통을 포함한 모든 글 본문에 키가 없으면 삭제
 * - 프로필 이미지(profiles/): 어떤 사용자의 프로필 이미지도 아니면 삭제
 * 방금 올리고 아직 저장하지 않은 이미지를 지우지 않도록 하루가 지난 파일만 대상으로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageCleanupService {

    static final Duration MIN_AGE = Duration.ofDays(1);
    static final int MAX_DELETES_PER_RUN = 500;

    // 도메인이 바뀌어도 키로 찾을 수 있도록 경로만 추출
    private static final Pattern IMAGE_KEY =
            Pattern.compile("((?:posts|profiles/\\d+)/[0-9a-fA-F-]{36}\\.(?:jpg|png|gif|webp))");
    private static final int PAGE_SIZE = 200;

    private final S3Client s3Client;
    private final EntityManager em;
    private final UploadedImageRepository uploadedImageRepository;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${app.image-cleanup.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void cleanup() {
        if (!enabled) {
            return;
        }
        try {
            int deleted = cleanup(Instant.now());
            if (deleted > 0) {
                log.info("사용하지 않는 이미지 {}개 삭제", deleted);
            }
        } catch (Exception e) {
            // R2가 설정되지 않은 환경 등에서는 건너뛴다
            log.warn("이미지 정리 실패: {}", e.getMessage());
        }
    }

    /**
     * @return 삭제한 파일 수
     */
    int cleanup(Instant now) {
        Set<String> inUse = referencedKeys();
        Instant cutoff = now.minus(MIN_AGE);

        List<String> unused = new ArrayList<>();
        for (String prefix : List.of("posts/", "profiles/")) {
            String token = null;
            do {
                ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucket).prefix(prefix).continuationToken(token).build());
                for (S3Object object : page.contents()) {
                    if (object.lastModified() != null && object.lastModified().isBefore(cutoff)
                            && !inUse.contains(object.key())) {
                        unused.add(object.key());
                    }
                }
                token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
            } while (token != null && unused.size() < MAX_DELETES_PER_RUN);
        }

        List<String> targets = unused.subList(0, Math.min(unused.size(), MAX_DELETES_PER_RUN));
        for (String key : targets) {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        }
        if (!targets.isEmpty()) {
            uploadedImageRepository.deleteByObjectKeyIn(targets);
        }
        return targets.size();
    }

    /**
     * 지금 쓰이고 있는 이미지 키 (휴지통 글 포함 모든 글 본문 + 사용자 프로필)
     */
    @SuppressWarnings("unchecked")
    Set<String> referencedKeys() {
        Set<String> keys = new HashSet<>();
        long lastId = 0;
        while (true) {
            List<Object[]> rows = em.createNativeQuery(
                            "SELECT post_id, content FROM post WHERE post_id > :lastId ORDER BY post_id")
                    .setParameter("lastId", lastId)
                    .setMaxResults(PAGE_SIZE)
                    .getResultList();
            if (rows.isEmpty()) {
                break;
            }
            for (Object[] row : rows) {
                lastId = ((Number) row[0]).longValue();
                collect(row[1] == null ? null : row[1].toString(), keys);
            }
        }
        List<String> profiles = em.createNativeQuery(
                "SELECT profile_image FROM users WHERE profile_image IS NOT NULL").getResultList();
        profiles.forEach(url -> collect(url, keys));
        return keys;
    }

    private static void collect(String text, Set<String> keys) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Matcher m = IMAGE_KEY.matcher(text);
        while (m.find()) {
            keys.add(m.group(1));
        }
    }
}
