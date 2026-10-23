package com.untitles.global.cache;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.post.service.PostService;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.global.util.WorkspaceMemberHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(EvictWorkspaceCachesTest.Config.class)
class EvictWorkspaceCachesTest {

    @Configuration
    @EnableCaching
    @Import(PostService.class)
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("workspaceTree", "publicWorkspace", "publicPost");
        }
    }

    @MockitoBean PostRepository postRepository;
    @MockitoBean FolderRepository folderRepository;
    @MockitoBean HtmlSanitizer htmlSanitizer;
    @MockitoBean WorkspaceMemberHelper workspaceMemberHelper;
    @MockitoBean com.untitles.domain.link.service.PostLinkService postLinkService;
    @MockitoBean com.untitles.domain.link.repository.PostLinkRepository postLinkRepository;

    @Autowired PostService postService;
    @Autowired CacheManager cacheManager;

    @Test
    @DisplayName("글을 삭제하면 공개 페이지 캐시와 해당 워크스페이스 트리 캐시가 비워진다")
    void deletePostEvictsPublicCaches() {
        cacheManager.getCache("publicWorkspace").put("my-slug", "old");
        cacheManager.getCache("publicPost").put("my-slug:10", "old");
        cacheManager.getCache("workspaceTree").put(1L, "tree-1");
        cacheManager.getCache("workspaceTree").put(2L, "tree-2");

        Users user = Users.builder().userId(1L).loginId("tester").nickname("테스터").build();
        Workspace workspace = Workspace.createPersonal();
        when(workspaceMemberHelper.getMemberOrThrow(1L, 1L)).thenReturn(WorkspaceMember.createOwner(workspace, user));
        when(postRepository.findByPostIdAndWorkspaceWorkspaceId(10L, 1L))
                .thenReturn(Optional.of(Post.create("t", "c", user, workspace, null)));

        postService.deletePost(1L, 1L, 10L);

        assertThat(cacheManager.getCache("publicWorkspace").get("my-slug")).isNull();
        assertThat(cacheManager.getCache("publicPost").get("my-slug:10")).isNull();
        assertThat(cacheManager.getCache("workspaceTree").get(1L)).isNull();
        assertThat(cacheManager.getCache("workspaceTree").get(2L)).isNotNull();  // 다른 워크스페이스는 유지
    }
}
