package com.untitles.domain.link.dto;

import java.util.List;

/**
 * 워크스페이스 노트 그래프
 * - nodes: 워크스페이스의 모든 글
 * - links: 글 사이의 직접 링크 (source → target)
 */
public record GraphResponse(List<Node> nodes, List<Link> links) {

    public record Node(Long postId, String title, Long folderId) {}

    public record Link(Long source, Long target) {}
}
