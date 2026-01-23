package com.untitles.domain.post.dto.response;

import com.untitles.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
/*
* 트리에 표시할 때는 id, title, 날짜만 필요
* */
@Builder
@Getter
public class PostSimpleDTO {
    private Long postId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostSimpleDTO from(final Post post) {
        return PostSimpleDTO.builder()
                .postId(post.getPostId())
                .title(post.getTitle())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

}
