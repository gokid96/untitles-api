package com.untitles.domain.workspace.dto.request;

import jakarta.validation.constraints.Size;

public record WorkspaceUpdateRequest(
        @Size(max = 50)
        String name,
        @Size(max = 200) String description
) {
}