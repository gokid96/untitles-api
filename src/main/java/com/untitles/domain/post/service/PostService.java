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
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * 게시글 상세 조회
     */
    public PostResponseDTO getPost(Long userId, Long workspaceId, Long postId) {
        getMemberOrThrow(userId, workspaceId);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

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
        if (postCount >= 100){
            throw new IllegalArgumentException("게시글은 워크스페이스당 최대 100개까지 생성할 수 있습니다.");
        }
        
        // 폴더 조회 (있으면)
        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(request.getFolderId(), workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        }

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
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
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        // 클라이언트 version과 DB version 비교
        if (request.getVersion() != null && !request.getVersion().equals(post.getVersion())) {
            throw new OptimisticLockException("다른 곳에서 수정되었습니다.");
        }

        if (request.getTitle() != null) post.updateTitle(request.getTitle());
        if (request.getContent() != null) post.updateContent(request.getContent());

        return PostResponseDTO.from(postRepository.saveAndFlush(post));
    }

    /**
     * 게시글 삭제
     */
    public void deletePost(Long userId, Long workspaceId, Long postId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        postRepository.delete(post);
    }

    /**
     * 게시글 이동 (폴더 변경)
     */
    public PostResponseDTO movePost(Long userId, Long workspaceId, Long postId, Long newFolderId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Post post = postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        Folder newFolder = null;
        if (newFolderId != null) {
            newFolder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(newFolderId, workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));
        }

        post.updateFolder(newFolder);
        postRepository.save(post);
        return PostResponseDTO.from(post);
    }

    private WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스 접근 권한이 없습니다."));
    }

    private void checkWritePermission(WorkspaceMember member) {
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new IllegalArgumentException("쓰기 권한이 없습니다.");
        }
    }
}
