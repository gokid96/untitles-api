package com.untitles.global.cache;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 워크스페이스 내용(글·폴더·워크스페이스 정보)이 바뀌는 메서드에 붙인다.
 * - workspaceTree: 해당 워크스페이스 트리만 비움 (메서드에 workspaceId 파라미터 필요)
 * - publicWorkspace, publicPost: 공개 페이지 캐시는 slug 기준이라 전부 비움
 *   (수정·삭제한 글이 공개 페이지에 최대 10분간 그대로 보이던 문제 방지)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Caching(evict = {
        @CacheEvict(value = "workspaceTree", key = "#workspaceId"),
        @CacheEvict(value = {"publicWorkspace", "publicPost"}, allEntries = true)
})
public @interface EvictWorkspaceCaches {
}
