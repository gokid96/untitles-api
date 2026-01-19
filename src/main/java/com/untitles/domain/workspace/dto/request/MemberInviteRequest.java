package com.untitles.domain.workspace.dto.request;

import com.untitles.domain.workspace.entity.WorkspaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MemberInviteRequest(
    @NotBlank String email,
    @NotNull WorkspaceRole role
) {}
