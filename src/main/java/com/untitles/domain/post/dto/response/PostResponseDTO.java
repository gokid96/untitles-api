package com.untitles.domain.post.dto.response;

import com.untitles.domain.post.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponseDTO {

    private Long postId;
    private String title;
    private String content;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long folderId;

    // 작성자 정보
    private Long authorId;
    private String authNickname;


    // Entity -> DTO
    public static PostResponseDTO from(Post post){
        return PostResponseDTO.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .content(post.getContent())
                .version(post.getVersion())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .authorId(post.getAuthor().getUserId())
                .authNickname(post.getAuthor().getNickname())
                .folderId(post.getFolder() != null ? post.getFolder().getFolderId() : null)
                .build();

    }


}
