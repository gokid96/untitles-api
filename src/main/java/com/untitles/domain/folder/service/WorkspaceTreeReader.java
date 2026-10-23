package com.untitles.domain.folder.service;

import com.untitles.domain.folder.dto.response.FolderResponseDTO;
import com.untitles.domain.folder.dto.response.WorkspaceTreeResponseDTO;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import com.untitles.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 워크스페이스 트리 캐시 조회
 * 권한 확인을 하지 않으므로 반드시 멤버 확인 후에 호출해야 한다 (FolderService.getRootFolders)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceTreeReader {

    private final FolderRepository folderRepository;
    private final PostRepository postRepository;

    @Cacheable(value = "workspaceTree", key = "#workspaceId")
    public WorkspaceTreeResponseDTO getTree(Long workspaceId) {
        List<FolderResponseDTO> folders = folderRepository
                .findByWorkspaceWorkspaceIdAndParentIsNull(workspaceId)
                .stream()
                .map(FolderResponseDTO::from)
                .toList();

        List<PostSimpleDTO> rootPosts = postRepository
                .findByWorkspaceWorkspaceIdAndFolderIsNull(workspaceId)
                .stream()
                .map(PostSimpleDTO::from)
                .toList();

        return WorkspaceTreeResponseDTO.builder()
                .folders(folders)
                .rootPosts(rootPosts)
                .build();
    }
}
