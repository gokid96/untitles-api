package com.untitles.domain.post.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.request.PostUpdateRequestDTO;
import com.untitles.domain.post.dto.response.PostResponseDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.HtmlSanitizer;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final HtmlSanitizer htmlSanitizer;

    /**
     * 게시글 상세 조회
     */
    public PostResponseDTO getPost(Long userId, Long workspaceId, Long postId) {
        getMemberOrThrow(userId, workspaceId);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return PostResponseDTO.from(post);
    }

    /**
     * 게시글 생성
     */
    public PostResponseDTO createPost(Long userId, Long workspaceId, PostCreateRequestDTO request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Users user = member.getUser();
        Workspace workspace = member.getWorkspace();

        long postCount = postRepository.countByWorkspace(workspace);
        if (postCount >= 100) {
            throw new BusinessException(ErrorCode.POST_LIMIT_EXCEEDED);
        }

        // 폴더 조회 (있으면)
        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(request.getFolderId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        }

        // XSS 방지를 위한 HTML Sanitize
        String sanitizedContent = htmlSanitizer.sanitize(request.getContent());

        Post post = Post.builder()
                .title(request.getTitle())
                .content(sanitizedContent)
                .author(user)
                .workspace(workspace)
                .folder(folder)
                .build();

        Post savedPost = postRepository.save(post);
        return PostResponseDTO.from(savedPost);
    }


    /**
     * 게시글 수정
     */
    public PostResponseDTO updatePost(Long userId, Long workspaceId, Long postId, PostUpdateRequestDTO request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 클라이언트 version과 DB version 비교
        if (request.getVersion() != null && !request.getVersion().equals(post.getVersion())) {
            throw new OptimisticLockException("다른 곳에서 수정되었습니다.");
        }

        if (request.getTitle() != null) post.updateTitle(request.getTitle());
        if (request.getContent() != null) {
            // XSS 방지를 위한 HTML Sanitize
            String sanitizedContent = htmlSanitizer.sanitize(request.getContent());
            post.updateContent(sanitizedContent);
        }

        return PostResponseDTO.from(postRepository.saveAndFlush(post));
    }

    /**
     * 게시글 삭제
     */
    public void deletePost(Long userId, Long workspaceId, Long postId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        postRepository.delete(post);
    }

    /**
     * 게시글 이동 (폴더 변경)
     */
    public PostResponseDTO movePost(Long userId, Long workspaceId, Long postId, Long newFolderId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        Folder newFolder = null;
        if (newFolderId != null) {
            newFolder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(newFolderId, workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        }

        post.updateFolder(newFolder);
        postRepository.save(post);
        return PostResponseDTO.from(post);
    }

    private WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
    }

    private void checkWritePermission(WorkspaceMember member) {
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new BusinessException(ErrorCode.WRITE_PERMISSION_DENIED);
        }
    }
}
