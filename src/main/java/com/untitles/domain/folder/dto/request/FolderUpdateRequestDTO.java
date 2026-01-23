package com.untitles.domain.folder.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FolderUpdateRequestDTO {
    @Size(max = 50, message = "폴더이름은 50자를 넘을 수 없습니다.")
    private String name;
}