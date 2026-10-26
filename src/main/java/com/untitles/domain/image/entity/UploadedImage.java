package com.untitles.domain.image.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 업로드한 이미지 기록. 사용자별 저장 용량 계산과 쓰이지 않는 이미지 정리에 사용한다.
 */
@Entity
@Table(name = "uploaded_image", indexes = @Index(name = "idx_uploaded_image_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 탈퇴해도 정리될 때까지 기록이 남도록 FK 없이 ID만 저장
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "object_key", nullable = false, unique = true, length = 300)
    private String objectKey;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static UploadedImage of(Long userId, String objectKey, long sizeBytes) {
        UploadedImage image = new UploadedImage();
        image.userId = userId;
        image.objectKey = objectKey;
        image.sizeBytes = sizeBytes;
        return image;
    }
}
