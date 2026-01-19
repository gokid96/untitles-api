package com.untitles.domain.post.dto.request;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostUpdateRequestDTO {

    private String title;
    private String content;
    private String summary;

}
