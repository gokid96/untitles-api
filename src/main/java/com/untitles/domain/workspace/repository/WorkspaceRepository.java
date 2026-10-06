package com.untitles.domain.workspace.repository;

import com.untitles.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    Optional<Workspace> findByPublicSlug(String publicSlug);
    boolean existsByPublicSlug(String publicSlug);
}