package com.untitles.domain.workspace.repository;

import com.untitles.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    // 내가 속한 모든 워크스페이스
    @Query("SELECT w FROM Workspace w JOIN w.members m WHERE m.user.userId = :userId")
    List<Workspace> findAllByUserId(@Param("userId") Long userId);
}