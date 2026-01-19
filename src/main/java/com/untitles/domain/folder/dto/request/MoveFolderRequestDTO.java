package com.untitles.domain.folder.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveFolderRequestDTO {
    private Long parentId;  // null 가능 (루트로 이동)
}