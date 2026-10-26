package com.untitles.domain.post.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequestDTO {

    @Size(max = 200)
    private String title;
    @Size(max = 500000, message = "노트가 너무 깁니다. (최대 50만 자)")
    private String content;
    private Long folderId;
}
