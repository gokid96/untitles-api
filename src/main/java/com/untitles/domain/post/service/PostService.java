package com.untitles.domain.post.service;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.request.PostCreateRequestDTO;
import com.untitles.domain.post.dto.request.PostUpdateRequestDTO;
import com.untitles.domain.post.dto.response.PostResponseDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.HtmlSanitizer;
import com.untitles.global.util.WorkspaceMemberHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final PostRepository postRepository;
    private final FolderRepository folderRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final HtmlSanitizer htmlSanitizer;
    private final WorkspaceMemberHelper workspaceMemberHelper;

    /**
     * 게시글 상세 조회
     */
    public PostResponseDTO getPost(Long userId, Long workspaceId, Long postId) {
        workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return PostResponseDTO.from(post);
    }

    /**
     * 게시글 생성
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public PostResponseDTO createPost(Long userId, Long workspaceId, PostCreateRequestDTO request) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Users user = member.getUser();
        Workspace workspace = member.getWorkspace();

        long postCount = postRepository.countByWorkspace(workspace);
        if (postCount >= 50) {
            throw new BusinessException(ErrorCode.POST_LIMIT_EXCEEDED);
        }

        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(request.getFolderId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        }

        String sanitizedContent = htmlSanitizer.sanitize(request.getContent());
        Post post = Post.create(request.getTitle(), sanitizedContent, user, workspace, folder);
        Post savedPost = postRepository.save(post);
        return PostResponseDTO.from(savedPost);
    }

    /**
     * 게시글 수정
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public PostResponseDTO updatePost(Long userId, Long workspaceId, Long postId, PostUpdateRequestDTO request) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if (request.getTitle() != null) post.updateTitle(request.getTitle());
        if (request.getContent() != null) {
            String sanitizedContent = htmlSanitizer.sanitize(request.getContent());
            post.updateContent(sanitizedContent);
        }
        return PostResponseDTO.from(postRepository.saveAndFlush(post));
    }

    /**
     * 게시글 삭제
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public void deletePost(Long userId, Long workspaceId, Long postId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        postRepository.delete(post);
    }

    /**
     * 게시글 이동 (폴더 변경)
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public PostResponseDTO movePost(Long userId, Long workspaceId, Long postId, Long newFolderId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

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
}
