package com.untitles.domain.publish.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.publish.dto.response.PublicWorkspaceResponse;
import com.untitles.domain.publish.dto.response.PublicWorkspaceResponse.PublicFolderItem;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class PublicViewService {

    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final PostRepository postRepository;

    /**
     * 공개 워크스페이스 조회 (비로그인)
     * - 내부용 /workspaces/{workspaceId}/folders 와 동일한 흐름
     *   ① 루트 폴더 조회 + Lazy Loading (children/posts)
     *   ② 루트 게시글 조회 (폴더 없는 공개 게시글)
     * - 공개 설정 판단은 폴더/게시글의 publishAll, isPublic, isExcluded 필드로 처리
     */
    @Transactional(readOnly = true)
    public PublicWorkspaceResponse getPublicWorkspace(String slug) {
        Workspace workspace = workspaceRepository.findByPublicSlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        // 공개 판단에 필요한 폴더 Map (publishAll 체크용)
        Map<Long, Folder> folderMap = folderRepository
                .findAllByWorkspaceWorkspaceId(workspace.getWorkspaceId())
                .stream()
                .collect(Collectors.toMap(Folder::getFolderId, f -> f));

        // 공개 게시글 ID Set
        List<Post> allPosts = postRepository
                .findAllWithAuthorByWorkspaceWorkspaceId(workspace.getWorkspaceId());

        Set<Long> visiblePostIds = allPosts.stream()
                .filter(post -> isPostVisible(post, workspace, folderMap))
                .map(Post::getPostId)
                .collect(Collectors.toSet());

        if (visiblePostIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PUBLIC_CONTENT_NOT_FOUND);
        }

        // 루트 폴더 조회 → Lazy Loading으로 children/posts 트리 구성 (내부용과 동일한 방식)
        List<Folder> rootFolders = folderRepository
                .findByWorkspaceWorkspaceIdAndParentIsNull(workspace.getWorkspaceId());

        List<PublicFolderItem> folderTree = rootFolders.stream()
                .map(folder -> PublicFolderItem.from(folder, visiblePostIds))
                .collect(Collectors.toList());

        // 루트 게시글 (폴더 없는 공개 게시글)
        List<PostSimpleDTO> rootPosts = postRepository
                .findByWorkspaceWorkspaceIdAndFolderIsNull(workspace.getWorkspaceId())
                .stream()
                .filter(p -> visiblePostIds.contains(p.getPostId()))
                .map(PostSimpleDTO::from)
                .collect(Collectors.toList());

        return PublicWorkspaceResponse.of(
                workspace.getName(),
                workspace.getDescription(),
                folderTree,
                rootPosts);
    }

    /**
     * 공개 게시글 상세 조회 (비로그인)
     */
    @Transactional(readOnly = true)
    public PublicWorkspaceResponse.PublicPostDetail getPublicPost(String slug, Long postId) {
        Workspace workspace = workspaceRepository.findByPublicSlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        Post post = postRepository
                .findWithAuthorByPostIdAndWorkspaceWorkspaceId(postId, workspace.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        Map<Long, Folder> folderMap = folderRepository
                .findAllByWorkspaceWorkspaceId(workspace.getWorkspaceId())
                .stream()
                .collect(Collectors.toMap(Folder::getFolderId, f -> f));

        if (!isPostVisible(post, workspace, folderMap)) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        return PublicWorkspaceResponse.PublicPostDetail.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .content(post.getContent())
                .authorNickname(post.getAuthor().getNickname())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    // ────────────────────────────────────────────────
    // Private helpers
    // ────────────────────────────────────────────────

    /**
     * 특정 게시글이 공개 상태인지 확인
     */
    private boolean isPostVisible(Post post, Workspace workspace, Map<Long, Folder> folderMap) {
        if (Boolean.TRUE.equals(workspace.getPublishAll())) {
            return !Boolean.TRUE.equals(post.getIsExcluded());
        }

        if (post.getFolder() == null) {
            return Boolean.TRUE.equals(post.getIsPublic());
        }

        // post.getFolder()는 Lazy 프록시이므로 folderMap에서 꺼낸 객체로 교체
        Folder folder = folderMap.get(post.getFolder().getFolderId());
        if (isFolderPublishAll(folder, folderMap)) {
            return !Boolean.TRUE.equals(post.getIsExcluded());
        }

        return Boolean.TRUE.equals(post.getIsPublic());
    }

    /**
     * 해당 폴더 또는 상위 폴더가 전체공개인지 확인
     */
    private boolean isFolderPublishAll(Folder folder, Map<Long, Folder> folderMap) {
        Folder current = folder;
        while (current != null) {
            if (Boolean.TRUE.equals(current.getPublishAll())) {
                return true;
            }
            current = current.getParent() != null
                    ? folderMap.get(current.getParent().getFolderId())
                    : null;
        }
        return false;
    }
}
