package com.untitles.domain.folder.service;

import com.untitles.domain.folder.dto.request.FolderCreateRequestDTO;
import com.untitles.domain.folder.dto.request.FolderUpdateRequestDTO;
import com.untitles.domain.folder.dto.response.FolderResponseDTO;
import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * 워크스페이스 멤버 권한 확인 (공통 헬퍼)
     */
    private WorkspaceMember getMemberOrThrow(Long userId, Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스를 찾을 수 없습니다."));
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new IllegalArgumentException("워크스페이스 접근 권한이 없습니다."));
    }

    /**
     * 쓰기 권한 확인 (VIEWER는 불가)
     */
    private void checkWritePermission(WorkspaceMember member) {
        if (member.getRole() == WorkspaceRole.VIEWER) {
            throw new IllegalArgumentException("쓰기 권한이 없습니다.");
        }
    }

    /**
     * 폴더 생성
     */
    @Transactional
    public FolderResponseDTO createFolder(Long userId, Long workspaceId, FolderCreateRequestDTO request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Users user = member.getUser();
        Workspace workspace = member.getWorkspace();

        // 부모 폴더 조회 (있으면)
        Folder parent = null;
        if (request.getParentId() != null) {
            parent = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(request.getParentId(), workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("부모 폴더를 찾을 수 없습니다."));
        }

        Folder folder = Folder.builder()
                .user(user)
                .workspace(workspace)
                .parent(parent)
                .name(request.getName())
                .build();

        Folder savedFolder = folderRepository.save(folder);
        return FolderResponseDTO.from(savedFolder);
    }

    /**
     * 폴더 수정
     */
    @Transactional
    public FolderResponseDTO updateFolder(Long userId, Long workspaceId, Long folderId, FolderUpdateRequestDTO request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        folder.updateName(request.getName());
        return FolderResponseDTO.from(folder);
    }

    /**
     * 폴더 삭제
     */
    @Transactional
    public void deleteFolder(Long userId, Long workspaceId, Long folderId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        folderRepository.delete(folder);
    }

    /**
     * 폴더 이동
     */
    @Transactional
    public FolderResponseDTO moveFolder(Long userId, Long workspaceId, Long folderId, Long newParentId) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        Folder newParent = null;
        if (newParentId != null) {
            newParent = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(newParentId, workspaceId)
                    .orElseThrow(() -> new IllegalArgumentException("목적지 폴더를 찾을 수 없습니다."));

            // 자기 자신으로 이동 불가
            if (folder.getFolderId().equals(newParentId)) {
                throw new IllegalArgumentException("자기 자신으로 이동할 수 없습니다.");
            }

            // 하위 폴더로 이동 불가 체크
            Folder current = newParent.getParent();
            while (current != null) {
                if (current.getFolderId().equals(folderId)) {
                    throw new IllegalArgumentException("하위 폴더로 이동할 수 없습니다.");
                }
                current = current.getParent();
            }
        }

        folder.moveToParent(newParent);
        return FolderResponseDTO.from(folder);
    }

    /**
     * 루트 폴더 목록 조회
     */
    public List<FolderResponseDTO> getRootFolders(Long userId, Long workspaceId) {
        getMemberOrThrow(userId, workspaceId);  // 읽기 권한 확인

        List<Folder> folders = folderRepository.findByWorkspaceWorkspaceIdAndParentIsNull(workspaceId);
        return folders.stream()
                .map(FolderResponseDTO::from)
                .toList();
    }

    /**
     * 하위 폴더 목록 조회
     */
    public List<FolderResponseDTO> getChildFolders(Long userId, Long workspaceId, Long parentId) {
        getMemberOrThrow(userId, workspaceId);  // 읽기 권한 확인

        // 부모 폴더가 해당 워크스페이스에 속하는지 확인
        folderRepository.findByFolderIdAndWorkspaceWorkspaceId(parentId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        List<Folder> children = folderRepository.findByParentFolderId(parentId);
        return children.stream()
                .map(FolderResponseDTO::from)
                .toList();
    }
}