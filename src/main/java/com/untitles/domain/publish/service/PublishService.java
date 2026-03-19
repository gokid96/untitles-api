package com.untitles.domain.publish.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.publish.dto.request.WorkspacePublishRequest;
import com.untitles.domain.publish.dto.response.PublishSettingResponse;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublishService {

    @org.springframework.beans.factory.annotation.Value("${app.public-base-url:https://untitles.net}")
    private String publicBaseUrl;

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final PostRepository postRepository;

    /**
     * 공개 설정 조회 (트리 구조로 현재 상태 반환)
     */
    @Transactional(readOnly = true)
    public PublishSettingResponse getPublishSettings(Long userId, Long workspaceId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkAdminPermission(member);

        Workspace workspace = member.getWorkspace();
        List<Folder> allFolders = folderRepository.findAllByWorkspaceWorkspaceId(workspaceId);
        List<Post> allPosts = postRepository.findAllWithAuthorByWorkspaceWorkspaceId(workspaceId);

        // 루트 게시글 (폴더 없는)
        List<PublishSettingResponse.PostPublishInfo> rootPosts = allPosts.stream()
                .filter(p -> p.getFolder() == null)
                .map(this::toPostPublishInfo)
                .toList();

        // 루트 폴더부터 트리 구성
        List<PublishSettingResponse.FolderPublishInfo> rootFolders = allFolders.stream()
                .filter(f -> f.getParent() == null)
                .map(f -> buildFolderTree(f, allFolders, allPosts))
                .toList();

        return PublishSettingResponse.builder()
                .publishAll(workspace.getPublishAll())
                .publicSlug(workspace.getPublicSlug())
                .publicUrl(workspace.getPublicSlug() != null
                        ? publicBaseUrl + "/public/" + workspace.getPublicSlug()
                        : null)
                .folders(rootFolders)
                .rootPosts(rootPosts)
                .build();
    }

    /**
     * 공개 설정 저장
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "publicWorkspace", key = "#result.publicSlug"),
            @CacheEvict(value = "publicPost", allEntries = true)
    })
    public PublishSettingResponse updatePublishSettings(Long userId, Long workspaceId,
                                                        WorkspacePublishRequest request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkAdminPermission(member);

        Workspace workspace = member.getWorkspace();

        // 1. 워크스페이스 전체공개 설정
        workspace.updatePublishAll(request.getPublishAll());

        // publicSlug가 없으면 자동 생성
        if (workspace.getPublicSlug() == null) {
            workspace.updatePublicSlug(generateSlug(workspace.getName()));
        }

        workspaceRepository.save(workspace);

        // 2. 워크스페이스 전체공개면 개별 설정은 무시 (제외만 처리 가능)
        if (Boolean.TRUE.equals(request.getPublishAll())) {
            // 전체공개 시 제외 처리만
            handleWorkspacePublishAll(workspaceId, request);
        } else {
            // 개별 설정 모드
            handleIndividualPublish(workspaceId, request);
        }

        return getPublishSettings(userId, workspaceId);
    }

    /**
     * 워크스페이스 전체공개 모드 - 제외할 게시글만 처리
     */
    private void handleWorkspacePublishAll(Long workspaceId,
                                            WorkspacePublishRequest request) {
        // 모든 게시글 제외 해제
        List<Post> allPosts = postRepository.findAllWithAuthorByWorkspaceWorkspaceId(workspaceId);
        allPosts.forEach(p -> p.updateIsExcluded(false));

        // 루트 게시글 제외 설정
        if (request.getExcludedRootPostIds() != null) {
            Set<Long> excludedRootIds = new HashSet<>(request.getExcludedRootPostIds());
            allPosts.stream()
                    .filter(p -> p.getFolder() == null && excludedRootIds.contains(p.getPostId()))
                    .forEach(p -> p.updateIsExcluded(true));
        }

        // 폴더별 제외 설정
        if (request.getFolders() != null) {
            for (WorkspacePublishRequest.FolderPublishItem folderItem : request.getFolders()) {
                if (folderItem.getExcludedPostIds() != null) {
                    Set<Long> excludedIds = new HashSet<>(folderItem.getExcludedPostIds());
                    allPosts.stream()
                            .filter(p -> p.getFolder() != null
                                    && p.getFolder().getFolderId().equals(folderItem.getFolderId())
                                    && excludedIds.contains(p.getPostId()))
                            .forEach(p -> p.updateIsExcluded(true));
                }
            }
        }

        postRepository.saveAll(allPosts);
    }

    /**
     * 개별 공개 모드 - 폴더/게시글 각각 설정
     */
    private void handleIndividualPublish(Long workspaceId,
                                          WorkspacePublishRequest request) {
        // 모든 게시글/폴더 초기화
        List<Post> allPosts = postRepository.findAllWithAuthorByWorkspaceWorkspaceId(workspaceId);
        List<Folder> allFolders = folderRepository.findAllByWorkspaceWorkspaceId(workspaceId);

        allPosts.forEach(p -> {
            p.updateIsPublic(false);
            p.updateIsExcluded(false);
        });
        allFolders.forEach(f -> f.updatePublishAll(false));

        // 루트 게시글 공개 설정
        if (request.getPublicPostIds() != null) {
            Set<Long> publicRootPostIds = new HashSet<>(request.getPublicPostIds());
            allPosts.stream()
                    .filter(p -> p.getFolder() == null && publicRootPostIds.contains(p.getPostId()))
                    .forEach(p -> p.updateIsPublic(true));
        }

        // 폴더별 설정
        if (request.getFolders() != null) {
            for (WorkspacePublishRequest.FolderPublishItem folderItem : request.getFolders()) {
                Folder folder = allFolders.stream()
                        .filter(f -> f.getFolderId().equals(folderItem.getFolderId()))
                        .findFirst()
                        .orElse(null);

                if (folder == null) continue;

                if (Boolean.TRUE.equals(folderItem.getPublishAll())) {
                    // 폴더 전체공개
                    folder.updatePublishAll(true);

                    // 제외 처리
                    if (folderItem.getExcludedPostIds() != null) {
                        Set<Long> excludedIds = new HashSet<>(folderItem.getExcludedPostIds());
                        allPosts.stream()
                                .filter(p -> p.getFolder() != null
                                        && p.getFolder().getFolderId().equals(folder.getFolderId())
                                        && excludedIds.contains(p.getPostId()))
                                .forEach(p -> p.updateIsExcluded(true));
                    }
                } else {
                    // 개별 게시글 공개
                    if (folderItem.getPublicPostIds() != null) {
                        Set<Long> publicIds = new HashSet<>(folderItem.getPublicPostIds());
                        allPosts.stream()
                                .filter(p -> p.getFolder() != null
                                        && p.getFolder().getFolderId().equals(folder.getFolderId())
                                        && publicIds.contains(p.getPostId()))
                                .forEach(p -> p.updateIsPublic(true));
                    }
                }
            }
        }

        folderRepository.saveAll(allFolders);
        postRepository.saveAll(allPosts);
    }

    // === 헬퍼 메서드 ===

    private PublishSettingResponse.FolderPublishInfo buildFolderTree(
            Folder folder, List<Folder> allFolders, List<Post> allPosts) {

        List<PublishSettingResponse.PostPublishInfo> posts = allPosts.stream()
                .filter(p -> p.getFolder() != null
                        && p.getFolder().getFolderId().equals(folder.getFolderId()))
                .map(this::toPostPublishInfo)
                .toList();

        List<PublishSettingResponse.FolderPublishInfo> children = allFolders.stream()
                .filter(f -> f.getParent() != null
                        && f.getParent().getFolderId().equals(folder.getFolderId()))
                .map(f -> buildFolderTree(f, allFolders, allPosts))
                .toList();

        return PublishSettingResponse.FolderPublishInfo.builder()
                .folderId(folder.getFolderId())
                .name(folder.getName())
                .publishAll(folder.getPublishAll())
                .posts(posts)
                .children(children)
                .build();
    }

    private PublishSettingResponse.PostPublishInfo toPostPublishInfo(Post post) {
        return PublishSettingResponse.PostPublishInfo.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .isPublic(post.getIsPublic())
                .isExcluded(post.getIsExcluded())
                .build();
    }

    private String generateSlug(String name) {
        // 영문/숫자만 추출하여 slug 생성 시도
        String base = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "")
                .trim();

        // 영문이 없으면 (한글만 있으면) 랜덤 생성
        if (base.isEmpty()) {
            base = UUID.randomUUID().toString().substring(0, 8);
        }

        // 중복 체크 루프
        String slug = base;
        int attempt = 0;
        while (workspaceRepository.existsByPublicSlug(slug)) {
            attempt++;
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 4);
            if (attempt > 10) {
                slug = UUID.randomUUID().toString();
                break;
            }
        }

        return slug;
    }

    private WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        return workspaceMemberRepository.findWithWorkspaceByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
    }

    private void checkAdminPermission(WorkspaceMember member) {
        if (!member.getRole().hasPermission(WorkspaceRole.ADMIN)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_PERMISSION);
        }
    }
}
