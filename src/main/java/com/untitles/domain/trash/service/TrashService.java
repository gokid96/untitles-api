package com.untitles.domain.trash.service;

import com.untitles.domain.folder.repository.FolderRepository;
import com.untitles.domain.folder.repository.FolderRepository.FolderRow;
import com.untitles.domain.link.repository.PostLinkRepository;
import com.untitles.domain.post.repository.PostRepository;
import com.untitles.domain.post.repository.PostRepository.TrashedPostRow;
import com.untitles.domain.trash.dto.TrashResponse;
import com.untitles.domain.workspace.entity.WorkspaceMember;
import com.untitles.global.cache.EvictWorkspaceCaches;
import com.untitles.global.exception.BusinessException;
import com.untitles.global.exception.ErrorCode;
import com.untitles.global.util.WorkspaceMemberHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 휴지통: 글·폴더 삭제는 deleted_at 표시(소프트 삭제)로 처리하고, 30일이 지나면 영구 삭제한다.
 * 폴더를 지우면 하위 폴더와 그 안의 글이 같은 deleted_at으로 함께 휴지통에 들어가며, 복구도 함께 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashService {

    public static final int RETENTION_DAYS = 30;

    private final PostRepository postRepository;
    private final FolderRepository folderRepository;
    private final PostLinkRepository postLinkRepository;
    private final WorkspaceMemberHelper workspaceMemberHelper;

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // ===== 휴지통으로 이동 =====

    @Transactional
    @EvictWorkspaceCaches
    public void trashPost(Long userId, Long workspaceId, Long postId) {
        checkWrite(userId, workspaceId);
        postRepository.findByPostIdAndWorkspaceWorkspaceId(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        postRepository.softDelete(postId, now());
    }

    @Transactional
    @EvictWorkspaceCaches
    public void trashFolder(Long userId, Long workspaceId, Long folderId) {
        checkWrite(userId, workspaceId);
        folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        List<FolderRow> rows = folderRepository.findAllRowsByWorkspace(workspaceId);
        // 아직 휴지통에 없는 하위 폴더만 함께 이동
        Set<Long> ids = subtree(rows, folderId, row -> row.getDeletedAt() == null);
        LocalDateTime deletedAt = now();
        folderRepository.softDeleteByIds(ids, deletedAt);
        postRepository.softDeleteByFolderIds(ids, deletedAt);
    }

    // ===== 조회 =====

    @Transactional(readOnly = true)
    public TrashResponse getTrash(Long userId, Long workspaceId) {
        workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);

        List<FolderRow> rows = folderRepository.findAllRowsByWorkspace(workspaceId);
        Map<Long, FolderRow> byId = rows.stream().collect(Collectors.toMap(FolderRow::getFolderId, r -> r));
        List<TrashedPostRow> trashedPosts = postRepository.findTrashedByWorkspace(workspaceId);

        // 상위 폴더와 같은 시각에 지워졌으면 그 폴더에 포함된 항목
        List<TrashResponse.Item> folders = rows.stream()
                .filter(r -> r.getDeletedAt() != null)
                .filter(r -> !deletedTogether(byId.get(r.getParentId()), r.getDeletedAt()))
                .sorted(Comparator.comparing(FolderRow::getDeletedAt).reversed())
                .map(r -> {
                    Set<Long> ids = subtree(rows, r.getFolderId(), row -> r.getDeletedAt().equals(row.getDeletedAt()));
                    int count = (int) trashedPosts.stream()
                            .filter(p -> p.getFolderId() != null && ids.contains(p.getFolderId())
                                    && r.getDeletedAt().equals(p.getDeletedAt()))
                            .count();
                    return new TrashResponse.Item(r.getFolderId(), r.getName(), r.getDeletedAt(), count);
                })
                .toList();

        List<TrashResponse.Item> posts = trashedPosts.stream()
                .filter(p -> !deletedTogether(p.getFolderId() == null ? null : byId.get(p.getFolderId()), p.getDeletedAt()))
                .sorted(Comparator.comparing(TrashedPostRow::getDeletedAt).reversed())
                .map(p -> new TrashResponse.Item(p.getPostId(), p.getTitle(), p.getDeletedAt(), 0))
                .toList();

        return new TrashResponse(folders, posts, RETENTION_DAYS);
    }

    // ===== 복구 =====

    @Transactional
    @EvictWorkspaceCaches
    public void restorePost(Long userId, Long workspaceId, Long postId) {
        checkWrite(userId, workspaceId);
        TrashedPostRow post = postRepository.findTrashedById(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        // 원래 폴더가 휴지통에 있으면 최상위로 꺼낸다
        Long folderId = post.getFolderId();
        if (folderId != null && folderRepository.findByFolderIdAndWorkspaceWorkspaceId(folderId, workspaceId).isEmpty()) {
            folderId = null;
        }
        postRepository.restore(postId, folderId);
    }

    @Transactional
    @EvictWorkspaceCaches
    public void restoreFolder(Long userId, Long workspaceId, Long folderId) {
        checkWrite(userId, workspaceId);
        List<FolderRow> rows = folderRepository.findAllRowsByWorkspace(workspaceId);
        FolderRow folder = rows.stream()
                .filter(r -> r.getFolderId().equals(folderId) && r.getDeletedAt() != null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));

        LocalDateTime deletedAt = folder.getDeletedAt();
        Set<Long> ids = subtree(rows, folderId, row -> deletedAt.equals(row.getDeletedAt()));

        // 상위 폴더가 휴지통에 있으면 최상위로 꺼낸다
        Long parentId = folder.getParentId();
        if (parentId != null && rows.stream().anyMatch(r -> r.getFolderId().equals(parentId) && r.getDeletedAt() != null)) {
            folderRepository.moveToRoot(folderId);
        }
        folderRepository.restoreByIds(ids);
        postRepository.restoreByFolderIds(ids, deletedAt);
    }

    // ===== 영구 삭제 =====

    @Transactional
    public void deletePostPermanently(Long userId, Long workspaceId, Long postId) {
        checkWrite(userId, workspaceId);
        postRepository.findTrashedById(postId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        hardDeletePosts(List.of(postId));
    }

    @Transactional
    public void deleteFolderPermanently(Long userId, Long workspaceId, Long folderId) {
        checkWrite(userId, workspaceId);
        List<FolderRow> rows = folderRepository.findAllRowsByWorkspace(workspaceId);
        rows.stream()
                .filter(r -> r.getFolderId().equals(folderId) && r.getDeletedAt() != null)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FOLDER_NOT_FOUND));
        hardDeleteFolders(subtree(rows, folderId, row -> true));
    }

    @Transactional
    public void emptyTrash(Long userId, Long workspaceId) {
        checkWrite(userId, workspaceId);
        List<FolderRow> rows = folderRepository.findAllRowsByWorkspace(workspaceId);
        Set<Long> folderIds = new HashSet<>();
        rows.stream().filter(r -> r.getDeletedAt() != null)
                .forEach(r -> folderIds.addAll(subtree(rows, r.getFolderId(), row -> true)));

        hardDeletePosts(postRepository.findTrashedByWorkspace(workspaceId).stream()
                .map(TrashedPostRow::getPostId).toList());
        hardDeleteFolders(folderIds);
    }

    /**
     * 보관 기간이 지난 휴지통 항목을 영구 삭제 (매일 새벽 4시)
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void purgeExpired() {
        LocalDateTime before = now().minusDays(RETENTION_DAYS);

        List<Long> postIds = postRepository.findIdsTrashedBefore(before);
        hardDeletePosts(postIds);

        List<FolderRow> expired = folderRepository.findRowsTrashedBefore(before);
        Set<Long> folderIds = new HashSet<>();
        if (!expired.isEmpty()) {
            for (Long workspaceId : folderRepository.findWorkspaceIdsByFolderIds(
                    expired.stream().map(FolderRow::getFolderId).toList())) {
                List<FolderRow> rows = folderRepository.findAllRowsByWorkspace(workspaceId);
                expired.stream()
                        .filter(e -> rows.stream().anyMatch(r -> r.getFolderId().equals(e.getFolderId())))
                        .forEach(e -> folderIds.addAll(subtree(rows, e.getFolderId(), row -> true)));
            }
        }
        hardDeleteFolders(folderIds);

        if (!postIds.isEmpty() || !folderIds.isEmpty()) {
            log.info("휴지통 정리: 글 {}개, 폴더 {}개 영구 삭제", postIds.size(), folderIds.size());
        }
    }

    // ===== 내부 =====

    private void hardDeletePosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        postLinkRepository.deleteAllByPostIds(postIds);
        postRepository.hardDeleteByIds(postIds);
    }

    private void hardDeleteFolders(Collection<Long> folderIds) {
        if (folderIds.isEmpty()) {
            return;
        }
        hardDeletePosts(postRepository.findAllIdsByFolderIds(folderIds));
        folderRepository.clearParentByIds(folderIds);
        folderRepository.hardDeleteByIds(folderIds);
    }

    private void checkWrite(Long userId, Long workspaceId) {
        WorkspaceMember member = workspaceMemberHelper.getMemberOrThrow(userId, workspaceId);
        workspaceMemberHelper.checkWritePermission(member);
    }

    private static boolean deletedTogether(FolderRow parent, LocalDateTime deletedAt) {
        return parent != null && deletedAt.equals(parent.getDeletedAt());
    }

    /**
     * rootId와, 조건을 만족하는 하위 폴더 ID 전체
     */
    private static Set<Long> subtree(List<FolderRow> rows, Long rootId, java.util.function.Predicate<FolderRow> include) {
        Map<Long, List<FolderRow>> children = rows.stream()
                .filter(r -> r.getParentId() != null)
                .collect(Collectors.groupingBy(FolderRow::getParentId));
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> stack = new ArrayDeque<>(List.of(rootId));
        while (!stack.isEmpty()) {
            Long id = stack.pop();
            if (!result.add(id)) {
                continue;
            }
            for (FolderRow child : children.getOrDefault(id, List.of())) {
                if (include.test(child)) {
                    stack.push(child.getFolderId());
                }
            }
        }
        return result;
    }
}
