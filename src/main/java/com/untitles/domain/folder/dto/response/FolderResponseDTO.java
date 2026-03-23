package com.untitles.domain.folder.dto.response;

import com.untitles.domain.folder.entity.Folder;
import com.untitles.domain.post.dto.response.PostSimpleDTO;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
                .children(folder.getChildren().stream() // Lazy 컬렉션 접근 → @BatchSize IN 쿼리 발생
                        .map(FolderResponseDTO::from)   // 자식 폴더도 from() 재귀 호출
                        .collect(Collectors.toList()))  // 자식이 없으면 빈 리스트 반환 → 재귀 종료
                .posts(folder.getPosts() != null
                        ? folder.getPosts().stream()
                        .map(PostSimpleDTO::from)
                        .collect(Collectors.toList())
                        : new ArrayList<>())
                .build();

//        return FolderResponseDTO.builder()
//                .folderId(folder.getFolderId())
//                .name(folder.getName())
//                .parentId(folder.getParent() != null ? folder.getParent().getFolderId() : null)
//                .children(folder.getChildren().stream()
//                        .map(FolderResponseDTO::from)
//                        .collect(Collectors.toList()))
//                .build();
    }

}
