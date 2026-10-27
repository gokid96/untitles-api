package com.untitles.domain.collab.service;

import com.untitles.domain.collab.dto.CollabDtos.AccessResponse;
import com.untitles.domain.collab.dto.CollabDtos.DocumentResponse;
import com.untitles.domain.collab.dto.CollabDtos.StoreResponse;
import com.untitles.domain.collab.entity.PostCollabState;
import com.untitles.domain.collab.repository.PostCollabStateRepository;
import com.untitles.domain.link.service.PostLinkService;
import com.untitles.domain.post.entity.Post;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.workspace.entity.WorkspaceRole;
import com.untitles.domain.workspace.repository.WorkspaceMemberRepository;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class CollabService {

    static final int MAX_CONTENT_LENGTH = 500_000;
    static final int MAX_TITLE_LENGTH = 200;

    private final PostRepository postRepository;
    private final PostCollabStateRepository stateRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final PostLinkService postLinkService;
    private final HtmlSanitizer htmlSanitizer;
    private final com.untitles.domain.collab.live.WorkspaceLiveNotifier liveNotifier;
    private final org.springframework.cache.CacheManager cacheManager;

    /** 저장 시 baseHash가 현재 DB와 다를 때 (다른 경로로 바뀜) */
    public static class StaleBaseException extends RuntimeException {
        private final transient StoreResponse current;

        public StaleBaseException(StoreResponse current) {
            super("collab base is stale");
            this.current = current;
        }

        public StoreResponse getCurrent() {
            return current;
        }
    }

    @Transactional(readOnly = true)
    public AccessResponse access(Long userId, Long workspaceId, Long postId) {
        // postId가 없으면 워크스페이스 변경 알림 채널: 멤버인지만 본다
        if (postId != null && postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId).isEmpty()) {
            return new AccessResponse(false, false);
        }
        return memberRepository.findByWorkspaceWorkspaceIdAndUserUserId(workspaceId, userId)
                .map(member -> new AccessResponse(true, member.getRole() != WorkspaceRole.VIEWER))
                .orElse(new AccessResponse(false, false));
    }

    @Transactional(readOnly = true)
    public DocumentResponse load(Long workspaceId, Long postId) {
        Post post = findPost(workspaceId, postId);
        String hash = hashOf(post);
        // 저장된 편집 상태는 다른 경로로 바뀌었어도(stale) 그대로 준다. 편집 서버가 그 위에 바뀐 부분만 반영해야
        // 예전 상태를 들고 있던 사람이 다시 연결될 때 본문이 중복되지 않는다
        var state = stateRepository.findById(postId);
        String ydoc = state.map(s -> Base64.getEncoder().encodeToString(s.getYdoc())).orElse(null);
        boolean stale = state.map(s -> !s.getContentHash().equals(hash)).orElse(false);
        return new DocumentResponse(post.getTitle(), post.getContent(), hash, ydoc, stale);
    }

    // 입력 중 2~10초마다 불리므로 캐시는 실제로 바뀌었을 때만 비운다 (@EvictWorkspaceCaches를 쓰지 않음)
    @Transactional
    public StoreResponse store(Long workspaceId, Long postId, String title, String content, String ydocBase64, String baseHash) {
        Post post = findPost(workspaceId, postId);
        String currentHash = hashOf(post);
        if (baseHash != null && !baseHash.equals(currentHash)) {
            throw new StaleBaseException(new StoreResponse(currentHash, post.getTitle(), post.getContent()));
        }

        String safeContent = content == null ? "" : content;
        if (safeContent.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String safeTitle = StringUtils.hasText(title) ? title.trim() : "무제";
        if (safeTitle.length() > MAX_TITLE_LENGTH) {
            safeTitle = safeTitle.substring(0, MAX_TITLE_LENGTH);
        }

        String sanitized = htmlSanitizer.sanitize(safeContent);
        if (sanitized == null) sanitized = "";
        boolean titleChanged = !safeTitle.equals(post.getTitle());
        if (titleChanged || !sanitized.equals(post.getContent())) {
            post.updateTitle(safeTitle);
            post.updateContent(sanitized);
            postLinkService.syncLinks(post);
            postRepository.saveAndFlush(post);  // 버전이 올라가 예전 방식으로 편집 중인 기기는 충돌로 감지한다
            evictCaches(workspaceId);
        }

        if (titleChanged) {
            // 다른 사람들의 노트 목록에도 바뀐 제목이 보이도록
            liveNotifier.notify(workspaceId, "updatePost", null, java.util.Map.of("postId", postId));
        }

        String newHash = hashOf(post);
        byte[] ydoc = Base64.getDecoder().decode(ydocBase64);
        stateRepository.findById(postId).ifPresentOrElse(
                state -> state.update(ydoc, newHash),
                () -> stateRepository.save(PostCollabState.of(postId, ydoc, newHash)));
        return new StoreResponse(newHash, post.getTitle(), post.getContent());
    }

    private void evictCaches(Long workspaceId) {
        Runnable evict = () -> {
            java.util.Optional.ofNullable(cacheManager.getCache("workspaceTree")).ifPresent(c -> c.evict(workspaceId));
            java.util.Optional.ofNullable(cacheManager.getCache("publicWorkspace")).ifPresent(org.springframework.cache.Cache::clear);
            java.util.Optional.ofNullable(cacheManager.getCache("publicPost")).ifPresent(org.springframework.cache.Cache::clear);
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            evict.run();
                        }
                    });
        } else {
            evict.run();
        }
    }

    /** 매일 새벽, 영구 삭제된 노트의 편집 상태를 지운다 */
    @Scheduled(cron = "0 45 4 * * *")
    @Transactional
    public void deleteOrphans() {
        stateRepository.deleteOrphans();
    }

    private Post findPost(Long workspaceId, Long postId) {
        return postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    static String hashOf(Post post) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((post.getTitle() + "\u0000" + post.getContent()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
