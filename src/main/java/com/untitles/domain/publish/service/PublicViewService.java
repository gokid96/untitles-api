package com.untitles.domain.publish.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.publish.dto.response.PublicWorkspaceResponse;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicViewService {

    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final PostRepository postRepository;

    /**
     * 공개 워크스페이스 조회 (비로그인)
     */
    @Transactional(readOnly = true)
    public PublicWorkspaceResponse getPublicWorkspace(String slug) {
        Workspace workspace = workspaceRepository.findByPublicSlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        List<Post> visiblePosts = getVisiblePosts(workspace);

        if (visiblePosts.isEmpty()) {
            throw new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND);  // 공개 콘텐츠 없음
        }

        return buildPublicResponse(workspace, visiblePosts);
    }

    /**
     * 공개 게시글 상세 조회 (비로그인)
     */
    @Transactional(readOnly = true)
    public PublicWorkspaceResponse.PublicPostDetail getPublicPost(String slug, Long postId) {
        Workspace workspace = workspaceRepository.findByPublicSlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        // 워크스페이스 소속 확인
        Post post = postRepository.findWithAuthorByPostIdAndWorkspaceWorkspaceId(postId, workspace.getWorkspaceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 폴더 맵을 한 번만 조회하여 재사용
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

    /**
     * 공개 가능한 게시글 필터링 (핵심 로직)
     */
    private List<Post> getVisiblePosts(Workspace workspace) {
        List<Post> allPosts = postRepository.findAllWithAuthorByWorkspaceWorkspaceId(
                workspace.getWorkspaceId());

        // 1. 워크스페이스 전체공개
        if (Boolean.TRUE.equals(workspace.getPublishAll())) {
            return allPosts.stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getIsExcluded()))
                    .toList();
        }

        // 2. 개별 판단
        List<Post> visiblePosts = new ArrayList<>();

        Map<Long, Folder> folderMap = folderRepository
                .findAllByWorkspaceWorkspaceId(workspace.getWorkspaceId())
                .stream()
                .collect(Collectors.toMap(Folder::getFolderId, f -> f));

        for (Post post : allPosts) {
            if (post.getFolder() == null) {
                // 루트 게시글: is_public만 체크
                if (Boolean.TRUE.equals(post.getIsPublic())) {
                    visiblePosts.add(post);
                }
            } else {
                // 폴더 소속 게시글
                Folder folder = post.getFolder();

                // 폴더 또는 상위 폴더 중 publishAll인 게 있는지 확인
                if (isFolderPublishAll(folder, folderMap)) {
                    if (!Boolean.TRUE.equals(post.getIsExcluded())) {
                        visiblePosts.add(post);
                    }
                } else if (Boolean.TRUE.equals(post.getIsPublic())) {
                    visiblePosts.add(post);
                }
            }
        }

        return visiblePosts;
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

    /**
     * 특정 게시글이 공개 상태인지 단건 확인
     */
    private boolean isPostVisible(Post post, Workspace workspace, Map<Long, Folder> folderMap) {
        if (Boolean.TRUE.equals(workspace.getPublishAll())) {
            return !Boolean.TRUE.equals(post.getIsExcluded());
        }

        if (post.getFolder() == null) {
            return Boolean.TRUE.equals(post.getIsPublic());
        }

        if (isFolderPublishAll(post.getFolder(), folderMap)) {
            return !Boolean.TRUE.equals(post.getIsExcluded());
        }

        return Boolean.TRUE.equals(post.getIsPublic());
    }

    /**
     * 공개 응답 구성 (폴더 트리 + 게시글)
     */
    private PublicWorkspaceResponse buildPublicResponse(Workspace workspace,
                                                         List<Post> visiblePosts) {
        // 루트 게시글
        List<PublicWorkspaceResponse.PublicPostItem> rootPosts = visiblePosts.stream()
                .filter(p -> p.getFolder() == null)
                .map(this::toPostItem)
                .toList();

        // 폴더별 게시글 그룹핑
        Map<Long, List<Post>> postsByFolder = visiblePosts.stream()
                .filter(p -> p.getFolder() != null)
                .collect(Collectors.groupingBy(p -> p.getFolder().getFolderId()));

        // 공개 게시글이 있는 폴더만 트리로 구성
        List<Folder> allFolders = folderRepository
                .findAllByWorkspaceWorkspaceId(workspace.getWorkspaceId());

        List<PublicWorkspaceResponse.PublicFolderItem> folderTree =
                buildPublicFolderTree(null, allFolders, postsByFolder);

        return PublicWorkspaceResponse.builder()
                .workspaceName(workspace.getName())
                .description(workspace.getDescription())
                .folders(folderTree)
                .rootPosts(rootPosts)
                .build();
    }

    /**
     * 공개 게시글이 있는 폴더만 포함하는 트리
     */
    private List<PublicWorkspaceResponse.PublicFolderItem> buildPublicFolderTree(
            Long parentId, List<Folder> allFolders, Map<Long, List<Post>> postsByFolder) {

        List<PublicWorkspaceResponse.PublicFolderItem> result = new ArrayList<>();

        List<Folder> children = allFolders.stream()
                .filter(f -> {
                    if (parentId == null) return f.getParent() == null;
                    return f.getParent() != null
                            && f.getParent().getFolderId().equals(parentId);
                })
                .toList();

        for (Folder folder : children) {
            List<PublicWorkspaceResponse.PublicPostItem> posts =
                    postsByFolder.getOrDefault(folder.getFolderId(), List.of())
                            .stream()
                            .map(this::toPostItem)
                            .toList();

            List<PublicWorkspaceResponse.PublicFolderItem> subFolders =
                    buildPublicFolderTree(folder.getFolderId(), allFolders, postsByFolder);

            // 이 폴더나 하위에 공개 게시글이 있을 때만 포함
            if (!posts.isEmpty() || !subFolders.isEmpty()) {
                result.add(PublicWorkspaceResponse.PublicFolderItem.builder()
                        .folderId(folder.getFolderId())
                        .name(folder.getName())
                        .posts(posts)
                        .children(subFolders)
                        .build());
            }
        }

        return result;
    }

    private PublicWorkspaceResponse.PublicPostItem toPostItem(Post post) {
        return PublicWorkspaceResponse.PublicPostItem.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .authorNickname(post.getAuthor().getNickname())
                .createdAt(post.getCreatedAt())
                .build();
    }
}