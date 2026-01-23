package com.untitles.domain.folder.service;

import com.untitles.domain.folder.dto.request.FolderCreateRequestDTO;
import com.untitles.domain.folder.dto.request.FolderUpdateRequestDTO;
import com.untitles.domain.folder.dto.response.FolderResponseDTO;
import com.untitles.domain.folder.dto.response.WorkspaceTreeResponseDTO;
import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.user.entity.Users;
import com.untitles.domain.user.repository.UserRepository;
import com.untitles.domain.workspace.entity.Workspace;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PostRepository postRepository;

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
    public FolderResponseDTO createFolder(Long userId, Long workspaceId, FolderCreateRequestDTO request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Users user = member.getUser();
        Workspace workspace = member.getWorkspace();

        long folderCount = folderRepository.countByWorkspace(workspace);
        if(folderCount >= 20){
            throw new IllegalArgumentException("폴더는 워크스페이스당 최대 20개까지 생성할 수 있습니다.");
        }

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
    public FolderResponseDTO updateFolder(Long userId, Long workspaceId, Long folderId, FolderUpdateRequestDTO request) {
        WorkspaceMember member = getMemberOrThrow(userId, workspaceId);
        checkWritePermission(member);

        Folder folder = folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("폴더를 찾을 수 없습니다."));

        folder.updateName(request.getName());
        folderRepository.save(folder);
        return FolderResponseDTO.from(folder);
    }

    /**
     * 폴더 삭제
     */
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
            Folder current = newParent;
            while (current != null) {
                if (current.getFolderId().equals(folderId)) {
                    throw new IllegalArgumentException("하위 폴더로 이동할 수 없습니다.");
                }
                current = current.getParent();
            }
        }

        folder.moveToParent(newParent);
        folderRepository.save(folder);
        return FolderResponseDTO.from(folder);
    }

    /**
     * 루트 폴더 목록 조회 (하위 폴더 포함된 트리 구조)
     */
    public WorkspaceTreeResponseDTO getRootFolders(Long userId, Long workspaceId) {
        getMemberOrThrow(userId, workspaceId);

        // 폴더 + 게시글 조회
        List<Folder> allFolders = folderRepository.findAllByWorkspaceIdWithPosts(workspaceId);

        // 루트 게시글 조회 (폴더 없는 게시글)
        List<Post> rootPosts = postRepository.findByWorkspaceWorkspaceIdAndFolderIsNull(workspaceId);

        // Map 생성: 폴더ID → DTO (children은 빈 리스트로 초기화)
        Map<Long, FolderResponseDTO> dtoMap = allFolders.stream()
                .collect(Collectors.toMap(
                        Folder::getFolderId,
                        FolderResponseDTO::from
                ));

        // 부모-자식 관계 연결
        //    parent와 dtoMap.get(parentId)는 같은 객체를 참조하므로
        //    parent를 수정하면 Map 안의 객체도 함께 수정됨
        for (Folder folder : allFolders) {
            if (folder.getParent() != null) {
                FolderResponseDTO parent = dtoMap.get(folder.getParent().getFolderId());
                FolderResponseDTO child = dtoMap.get(folder.getFolderId());
                if (parent != null) {
                    parent.getChildren().add(child);
                }
            }
        }

        // 루트 폴더 목록
        List<FolderResponseDTO> rootFolders = allFolders.stream()
                .filter(f -> f.getParent() == null)
                .map(f -> dtoMap.get(f.getFolderId()))
                .toList();

        // 루트 게시글 변환
        List<PostSimpleDTO> rootPostDTOs = rootPosts.stream()
                .map(PostSimpleDTO::from)
                .toList();

        return WorkspaceTreeResponseDTO.builder()
                .folders(rootFolders)
                .rootPosts(rootPostDTOs)
                .build();
    }
}
