package com.untitles.domain.folder.service;

import com.untitles.domain.folder.dto.request.FolderCreateRequestDTO;
import com.untitles.domain.folder.dto.request.FolderUpdateRequestDTO;
import com.untitles.domain.folder.dto.response.FolderResponseDTO;
import com.untitles.domain.folder.dto.response.WorkspaceTreeResponseDTO;
import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private final FolderRepository folderRepository;
    private final PostRepository postRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceMemberHelper workspaceMemberHelper;

    /**
     * 폴더 생성
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public FolderResponseDTO createFolder(Long userId, Long workspaceId, FolderCreateRequestDTO request) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Users user = member.getUser();
        Workspace workspace = member.getWorkspace();

        long folderCount = folderRepository.countByWorkspace(workspace);
        if (folderCount >= 20) {
            throw new BusinessException(ErrorCode.FOLDER_LIMIT_EXCEEDED);
        }

        Folder parent = null;
        if (request.getParentId() != null) {
            parent = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(request.getParentId(), workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        }

        Folder folder = Folder.create(user, workspace, parent, request.getName());
        Folder savedFolder = folderRepository.save(folder);
        return FolderResponseDTO.from(savedFolder);
    }

    /**
     * 폴더 수정
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public FolderResponseDTO updateFolder(Long userId, Long workspaceId, Long folderId, FolderUpdateRequestDTO request) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        folder.updateName(request.getName());
        folderRepository.save(folder);
        return FolderResponseDTO.from(folder);
    }

    /**
     * 폴더 삭제
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public void deleteFolder(Long userId, Long workspaceId, Long folderId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        folderRepository.delete(folder);
    }

    /**
     * 폴더 이동
     */
    @Transactional
    @CacheEvict(value = "workspaceTree", key = "#workspaceId")
    public FolderResponseDTO moveFolder(Long userId, Long workspaceId, Long folderId, Long newParentId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        Folder newParent = null;
        if (newParentId != null) {
            newParent = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(newParentId, workspaceId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

            if (folder.getFolderId().equals(newParentId)) {
                throw new BusinessException(ErrorCode.CANNOT_MOVE_TO_SELF);
            }

            Folder current = newParent;
            while (current != null) {
                if (current.getFolderId().equals(folderId)) {
                    throw new BusinessException(ErrorCode.CANNOT_MOVE_TO_CHILD);
                }
                current = current.getParent();
            }
        }

        folder.moveToParent(newParent);
        folderRepository.save(folder);
        return FolderResponseDTO.from(folder);
    }

    /**
     * 워크스페이스 트리 조회 (루트 폴더 + 루트 게시글)
     */
    @Cacheable(value = "workspaceTree", key = "#workspaceId")
    public WorkspaceTreeResponseDTO getRootFolders(Long userId, Long workspaceId) {
        workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);

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
