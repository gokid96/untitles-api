package com.untitles.domain.image.service;

import com.untitles.domain.image.entity.UploadedImage;
import com.untitles.domain.image.repository.UploadedImageRepository;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.support.H2JpaTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@H2JpaTest
class ImageCleanupServiceTest {

    private static final String U = "0123abcd-0000-4000-8000-";

    @Autowired EntityManager em;
    @Autowired UserRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired UploadedImageRepository uploadedImageRepository;

    private S3Client s3Client;
    private ImageCleanupService service;
    private final Instant now = Instant.parse("2026-10-26T00:00:00Z");

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        service = new ImageCleanupService(s3Client, em, uploadedImageRepository);
        ReflectionTestUtils.setField(service, "bucket", "bucket");
    }

    private static S3Object object(String key, Duration age, Instant now) {
        return S3Object.builder().key(key).lastModified(now.minus(age)).build();
    }

    @Test
    @DisplayName("본문·휴지통·프로필 어디에도 없는 하루 지난 이미지만 지운다")
    void deletesOnlyOldUnreferenced() {
        String used = "posts/" + U + "000000000001.png";
        String trashed = "posts/" + U + "000000000002.jpg";
        String unused = "posts/" + U + "000000000003.webp";
        String fresh = "posts/" + U + "000000000004.png";
        String profileUsed = "profiles/7/" + U + "000000000005.png";
        String profileOld = "profiles/7/" + U + "000000000006.png";

        Users user = userRepository.save(Users.createLocal("a@example.com", "a", "pw", "에이"));
        user.updateProfileImage("https://other-domain.example/" + profileUsed);
        Workspace ws = workspaceRepository.save(Workspace.createPersonal());
        em.persist(Post.create("t", "<p><img src=\"https://img.example.com/" + used + "\"></p>", user, ws, null));
        Post gone = Post.create("t2", "<img src=\"https://img.example.com/" + trashed + "\">", user, ws, null);
        em.persist(gone);
        em.flush();
        em.createNativeQuery("UPDATE post SET deleted_at = CURRENT_TIMESTAMP WHERE post_id = " + gone.getPostId()).executeUpdate();
        uploadedImageRepository.save(UploadedImage.of(user.getUserId(), unused, 100));

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenAnswer(inv -> {
            ListObjectsV2Request req = inv.getArgument(0);
            List<S3Object> objects = req.prefix().equals("posts/")
                    ? List.of(object(used, Duration.ofDays(3), now), object(trashed, Duration.ofDays(3), now),
                              object(unused, Duration.ofDays(3), now), object(fresh, Duration.ofHours(2), now))
                    : List.of(object(profileUsed, Duration.ofDays(3), now), object(profileOld, Duration.ofDays(3), now));
            return ListObjectsV2Response.builder().contents(objects).isTruncated(false).build();
        });

        int deleted = service.cleanup(now);

        assertThat(deleted).isEqualTo(2);
        verify(s3Client).deleteObject(DeleteObjectRequest.builder().bucket("bucket").key(unused).build());
        verify(s3Client).deleteObject(DeleteObjectRequest.builder().bucket("bucket").key(profileOld).build());
        verify(s3Client, times(2)).deleteObject(any(DeleteObjectRequest.class));
        assertThat(uploadedImageRepository.findAll()).isEmpty();
    }
}
