package com.untitles.domain.trash.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 휴지통 목록. 폴더와 함께 지워진 글·하위 폴더는 그 폴더 안에 포함되므로 최상위 항목만 내려준다.
 */
public record TrashResponse(List<Item> folders, List<Item> posts, int retentionDays) {

    /**
     * @param id        글 또는 폴더 ID
     * @param name      글 제목 또는 폴더 이름
     * @param deletedAt 휴지통으로 이동한 시각
     * @param postCount 폴더와 함께 지워진 글 수 (글 항목이면 0)
     */
    public record Item(Long id, String name, LocalDateTime deletedAt, int postCount) {
    }
}
