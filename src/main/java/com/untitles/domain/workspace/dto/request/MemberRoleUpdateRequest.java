package com.untitles.domain.workspace.dto.request;

import com.untitles.domain.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record MemberRoleUpdateRequest(
    @NotNull WorkspaceRole role
) {}