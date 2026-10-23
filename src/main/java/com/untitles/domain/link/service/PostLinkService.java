package com.untitles.domain.link.service;

import com.untitles.domain.link.dto.GraphResponse;
import com.untitles.domain.link.entity.PostLink;
import com.untitles.domain.link.repository.PostLinkRepository;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.global.util.WorkspaceMemberHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostLinkService {

    // 정화된 본문에서 노트 링크의 대상 글 ID 추출
    private static final Pattern NOTE_LINK = Pattern.compile("data-note-id=\"(\\d+)\"");

    private final PostLinkRepository postLinkRepository;
    private final PostRepository postRepository;
    private final WorkspaceMemberHelper workspaceMemberHelper;

    /**
     * 글 본문의 노트 링크를 다시 계산해 저장 (글 생성·본문 수정 시 호출)
     * 같은 워크스페이스에 실제로 있는 글만, 자기 자신은 제외
     */
    @Transactional
    public void syncLinks(Post post) {
        Long workspaceId = post.getWorkspace().getWorkspaceId();
        postLinkRepository.deleteBySourcePostId(post.getPostId());

        Set<Long> targetIds = extractTargetIds(post.getContent());
        targetIds.remove(post.getPostId());
        if (targetIds.isEmpty()) {
            return;
        }

        List<PostLink> links = postRepository.findAllById(targetIds).stream()
                .filter(target -> target.getWorkspace().getWorkspaceId().equals(workspaceId))
                .map(target -> PostLink.of(workspaceId, post.getPostId(), target.getPostId()))
                .toList();
        postLinkRepository.saveAll(links);
    }

    static Set<Long> extractTargetIds(String html) {
        Set<Long> ids = new LinkedHashSet<>();
        if (html == null) {
            return ids;
        }
        Matcher matcher = NOTE_LINK.matcher(html);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // 지나치게 긴 숫자는 무시
            }
        }
        return ids;
    }

    /**
     * 워크스페이스 그래프 (멤버만)
     */
    public GraphResponse getGraph(Long userId, Long workspaceId) {
        workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);

        List<GraphResponse.Node> nodes = postRepository.findAllByWorkspaceWorkspaceId(workspaceId).stream()
                .sorted(Comparator.comparing(Post::getPostId))
                .map(p -> new GraphResponse.Node(
                        p.getPostId(), p.getTitle(), p.getFolder() != null ? p.getFolder().getFolderId() : null))
                .toList();

        Set<Long> nodeIds = nodes.stream().map(GraphResponse.Node::postId).collect(Collectors.toSet());
        List<GraphResponse.Link> links = postLinkRepository.findAllByWorkspaceId(workspaceId).stream()
                .filter(l -> nodeIds.contains(l.getSourcePostId()) && nodeIds.contains(l.getTargetPostId()))
                .map(l -> new GraphResponse.Link(l.getSourcePostId(), l.getTargetPostId()))
                .toList();

        return new GraphResponse(nodes, links);
    }
}
