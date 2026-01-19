package com.untitles.domain.folder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class FolderCreateRequestDTO {

    @NotBlank(message = "폴더 이름을 비울수 없습니다.")
    @Size(max = 50, message = "폴더이름은 50자를 넘을 수 없습니다.")
    private String name;
    private Long parentId;
    private Integer orderIndex;

}
