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

    /**
     * 폴더 전체와 노트 목록(본문 제외)을 쿼리 두 번으로 읽어 메모리에서 트리를 만든다.
     * (폴더마다 하위 폴더·노트를 따로 읽거나 노트 본문까지 읽지 않도록)
     */
    @Cacheable(value = "workspaceTree", key = "#workspaceId")
    public WorkspaceTreeResponseDTO getTree(Long workspaceId) {
        List<com.untitles.domain.folder.entity.Folder> all = folderRepository.findAllByWorkspaceWorkspaceId(workspaceId);
        java.util.Map<Long, FolderResponseDTO> byId = new java.util.LinkedHashMap<>();
        all.stream()
                .sorted(java.util.Comparator.comparing(com.untitles.domain.folder.entity.Folder::getFolderId))
                .forEach(f -> byId.put(f.getFolderId(), FolderResponseDTO.builder()
                        .folderId(f.getFolderId())
                        .name(f.getName())
                        .parentId(f.getParent() != null ? f.getParent().getFolderId() : null)
                        .createdAt(f.getCreatedAt())
                        .updatedAt(f.getUpdatedAt())
                        .children(new java.util.ArrayList<>())
                        .posts(new java.util.ArrayList<>())
                        .build()));

        List<FolderResponseDTO> roots = new java.util.ArrayList<>();
        for (FolderResponseDTO folder : byId.values()) {
            FolderResponseDTO parent = folder.getParentId() == null ? null : byId.get(folder.getParentId());
            if (parent != null) parent.getChildren().add(folder);
            else if (folder.getParentId() == null) roots.add(folder);
            // 부모가 휴지통에 있는 폴더는 목록에 보이지 않는다 (기존과 같음)
        }

        List<PostSimpleDTO> rootPosts = new java.util.ArrayList<>();
        for (Object[] row : postRepository.findTreeRowsByWorkspaceId(workspaceId)) {
            PostSimpleDTO post = PostSimpleDTO.builder()
                    .postId((Long) row[0])
                    .title((String) row[1])
                    .createdAt((java.time.LocalDateTime) row[2])
                    .updatedAt((java.time.LocalDateTime) row[3])
                    .build();
            Long folderId = (Long) row[4];
            if (folderId == null) {
                rootPosts.add(post);
            } else {
                FolderResponseDTO folder = byId.get(folderId);
                if (folder != null) folder.getPosts().add(post);
            }
        }

        return WorkspaceTreeResponseDTO.builder()
                .folders(roots)
                .rootPosts(rootPosts)
                .build();
    }
}
