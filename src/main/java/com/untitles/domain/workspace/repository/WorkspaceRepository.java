package com.untitles.domain.workspace.repository;

import com.untitles.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    Optional<Workspace> findByPublicSlug(String publicSlug);
    boolean existsByPublicSlug(String publicSlug);

    /**
     * 노트·폴더 개수 제한을 확인하는 동안 같은 워크스페이스의 동시 생성을 줄 세운다.
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT w FROM Workspace w WHERE w.workspaceId = :workspaceId")
    Optional<Workspace> findByIdForUpdate(@org.springframework.data.repository.query.Param("workspaceId") Long workspaceId);
}