package com.untitles.domain.post.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MovePostRequestDTO {
    private Long folderId;  // null이면 루트로 이동
}