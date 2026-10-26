package com.untitles.domain.image.service;

import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageServiceTest {

    private static final String PUBLIC_URL = "https://img.example.com";
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    private S3Client s3Client;
    private ImageService imageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        imageService = new ImageService(s3Client, mock(com.untitles.domain.image.repository.UploadedImageRepository.class));
        ReflectionTestUtils.setField(imageService, "bucket", "bucket");
        ReflectionTestUtils.setField(imageService, "publicUrl", PUBLIC_URL);
    }

    @Test
    @DisplayName("실제 형식으로 확장자와 Content-Type을 정한다 (보낸 값은 무시)")
    void usesDetectedType() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.html", "text/html", PNG);

        String url = imageService.uploadProfileImage(file, 7L);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getValue().key()).startsWith("profiles/7/").endsWith(".png");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
        assertThat(url).startsWith(PUBLIC_URL + "/profiles/7/");
    }

    @Test
    @DisplayName("SVG는 image/svg+xml로 보내도 거부한다")
    void rejectsSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "a.svg", "image/svg+xml", svg);

        assertThatThrownBy(() -> imageService.uploadPostImage(file, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("다른 사용자의 이미지 URL은 삭제하지 않는다")
    void doesNotDeleteOthersImage() {
        imageService.deleteProfileImage(PUBLIC_URL + "/profiles/8/a.png", 7L);
        imageService.deleteProfileImage(PUBLIC_URL + "/posts/a.png", 7L);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("본인 프로필 이미지는 삭제한다")
    void deletesOwnImage() {
        imageService.deleteProfileImage(PUBLIC_URL + "/profiles/7/a.png", 7L);

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().key()).isEqualTo("profiles/7/a.png");
    }

    @Test
    @DisplayName("5MB를 넘으면 IMAGE_TOO_LARGE")
    void tooLarge() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", big);

        assertThatThrownBy(() -> imageService.uploadPostImage(file, 1L))
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_TOO_LARGE);
    }
}
