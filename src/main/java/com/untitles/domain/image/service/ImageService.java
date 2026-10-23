package com.untitles.domain.image.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {
    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    /**
     * 허용 이미지 형식. 클라이언트가 보낸 Content-Type·확장자는 믿지 않고 파일 앞부분(시그니처)으로 판별한다.
     * SVG는 스크립트를 담을 수 있어 허용하지 않는다.
     */
    enum ImageType {
        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png"),
        GIF("image/gif", ".gif"),
        WEBP("image/webp", ".webp");

        final String contentType;
        final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }
    }

    /**
     * 게시글 이미지 업로드
     */
    public String uploadPostImage(MultipartFile file) {
        return upload(file, "posts");
    }

    /**
     * 프로필 이미지 업로드. 사용자별 경로(profiles/{userId}/)에 저장해 소유자를 확인할 수 있게 한다.
     */
    public String uploadProfileImage(MultipartFile file, Long userId) {
        return upload(file, profilePrefix(userId));
    }

    /**
     * 이 사용자가 업로드한 프로필 이미지 URL인지 확인
     */
    public boolean isOwnProfileImage(String imageUrl, Long userId) {
        return StringUtils.hasText(imageUrl)
                && imageUrl.startsWith(publicUrl + "/" + profilePrefix(userId) + "/");
    }

    /**
     * 본인 프로필 이미지만 삭제. 다른 경로의 URL은 무시한다.
     */
    public void deleteProfileImage(String imageUrl, Long userId) {
        if (!isOwnProfileImage(imageUrl, userId)) {
            return;
        }
        String key = imageUrl.substring(publicUrl.length() + 1);
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }

    private String upload(MultipartFile file, String folder) {
        byte[] bytes = readAndValidate(file);
        ImageType type = detectType(bytes);
        String key = folder + "/" + UUID.randomUUID() + type.extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(type.contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            return publicUrl + "/" + key;
        } catch (Exception e) {
            throw new RuntimeException("이미지 업로드 실패", e);
        }
    }

    private static String profilePrefix(Long userId) {
        return "profiles/" + userId;
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("파일 크기는 5MB 이하여야 합니다.");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다.");
        }
    }

    static ImageType detectType(byte[] b) {
        if (startsWith(b, 0xFF, 0xD8, 0xFF)) return ImageType.JPEG;
        if (startsWith(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return ImageType.PNG;
        if (startsWithAscii(b, 0, "GIF87a") || startsWithAscii(b, 0, "GIF89a")) return ImageType.GIF;
        if (startsWithAscii(b, 0, "RIFF") && startsWithAscii(b, 8, "WEBP")) return ImageType.WEBP;
        throw new IllegalArgumentException("JPG, PNG, GIF, WEBP 이미지만 업로드할 수 있습니다.");
    }

    private static boolean startsWith(byte[] b, int... signature) {
        if (b.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((b[i] & 0xFF) != signature[i]) return false;
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] b, int offset, String text) {
        byte[] t = text.getBytes(StandardCharsets.US_ASCII);
        return b.length >= offset + t.length
                && Arrays.equals(Arrays.copyOfRange(b, offset, offset + t.length), t);
    }
}
