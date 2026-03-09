package com.untitles.domain.post.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostUpdateRequestDTO {
    @Size(max = 200)
    private String title;
    @Size(max = 50000)
    private String content;
    private Long version;
}
