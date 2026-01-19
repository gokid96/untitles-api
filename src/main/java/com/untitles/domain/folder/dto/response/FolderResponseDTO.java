package com.untitles.domain.folder.dto.response;

import com.untitles.domain.folder.entity.Folder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class FolderResponseDTO {
    private Long folderId;
    private String name;
    private Long parentId;
    private Integer orderIndex;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<FolderResponseDTO> children;

    /*하위 폴더 포함*/
    public static FolderResponseDTO from(Folder folder) {
        return FolderResponseDTO.builder()
                .folderId(folder.getFolderId())
                .name(folder.getName())
                .parentId(folder.getParent() != null ? folder.getParent().getFolderId() : null)
                .children(folder.getChildren().stream()
                        .map(FolderResponseDTO::from)
                        .collect(Collectors.toList()))
                .build();
    }
    /*
    * 하위 폴더 제외
    * */
    public static FolderResponseDTO fromSimple(Folder folder) {
        return FolderResponseDTO.builder()
                .folderId(folder.getFolderId())
                .name(folder.getName())
                .parentId(folder.getParent() != null ? folder.getParent().getFolderId() : null)
                .build();
    }



}
