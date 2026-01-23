package com.untitles.domain.folder.repository;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    // 워크스페이스 + 폴더 ID로 조회 (권한 체크용)
    Optional<Folder> findByFolderIdAndWorkspaceWorkspaceId(Long folderId, Long workspaceId);

    // 폴더과 게시글 한번에 조회
    @Query("SELECT DISTINCT f FROM Folder f " +
            "LEFT JOIN FETCH f.posts " +
            "WHERE f.workspace.workspaceId = :workspaceId"
    )
    List<Folder> findAllByWorkspaceIdWithPosts(@Param("workspaceId") Long workspaceId);

    long countByWorkspace(Workspace workspace);
}
