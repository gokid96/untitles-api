package com.untitles.domain.folder.service;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(FolderServiceCacheTest.Config.class)
class FolderServiceCacheTest {

    @Configuration
    @EnableCaching
    @Import({FolderService.class, WorkspaceTreeReader.class})
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("workspaceTree");
        }
    }

    @MockitoBean FolderRepository folderRepository;
    @MockitoBean PostRepository postRepository;
    @MockitoBean WorkspaceMemberHelper workspaceMemberHelper;
    @MockitoBean com.untitles.domain.trash.service.TrashService trashService;

    @Autowired FolderService folderService;

    @Test
    @DisplayName("트리가 캐시돼 있어도 멤버가 아니면 조회할 수 없다")
    void cachedTreeStillChecksMembership() {
        when(folderRepository.findByWorkspaceWorkspaceIdAndParentIsNull(1L)).thenReturn(List.of());
        when(postRepository.findByWorkspaceWorkspaceIdAndFolderIsNull(1L)).thenReturn(List.of());
        when(workspaceMemberHelper.getMemberOrThrow(2L, 1L))
                .thenThrow(new BusinessException(ErrorCode.ACCESS_DENIED));

        // 멤버(1번)가 먼저 조회해서 캐시에 저장
        folderService.getRootFolders(1L, 1L);
        folderService.getRootFolders(1L, 1L);
        verify(folderRepository, times(1)).findByWorkspaceWorkspaceIdAndParentIsNull(1L);

        // 멤버가 아닌 사용자(2번)는 캐시가 있어도 거부
        assertThatThrownBy(() -> folderService.getRootFolders(2L, 1L))
                .extracting("errorCode").isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
