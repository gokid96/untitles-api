package com.untitles.domain.folder.dto.response;

import com.untitles.domain.post.dto.response.PostSimpleDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class WorkspaceTreeResponseDTO {
    private List<FolderResponseDTO> folders;
    private List<PostSimpleDTO> rootPosts;

    public static WorkspaceTreeResponseDTO of(
            List<FolderResponseDTO> folders,
            List<PostSimpleDTO> rootPosts) {
        return WorkspaceTreeResponseDTO.builder()
                .folders(folders)
                .rootPosts(rootPosts)
                .build();
    }
}
