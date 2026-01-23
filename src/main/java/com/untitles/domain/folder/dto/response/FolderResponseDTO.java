package com.untitles.domain.folder.dto.response;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class FolderResponseDTO {
    private Long folderId;
    private String name;
    private Long parentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // children을 수정 가능하게
    @Setter
    private List<FolderResponseDTO> children;
    @Getter
    private List<PostSimpleDTO> posts;

    /* 하위 폴더 + 게시글 포함 */
    public static FolderResponseDTO from(Folder folder) {
        return FolderResponseDTO.builder()
                .folderId(folder.getFolderId())
                .name(folder.getName())
                .parentId(folder.getParent() != null ? folder.getParent().getFolderId() : null)
                .createdAt(folder.getCreatedAt())
                .updatedAt(folder.getUpdatedAt())
                .children(new ArrayList<>())
                .posts(folder.getPosts().stream()
                        .map(PostSimpleDTO::from)
                        .collect(Collectors.toList()))
                .build();
    }

}
