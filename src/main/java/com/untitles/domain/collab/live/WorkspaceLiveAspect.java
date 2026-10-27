package com.untitles.domain.collab.live;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 워크스페이스 내용을 바꾸는 메서드(@EvictWorkspaceCaches)가 성공하면 변경 알림을 보낸다.
 * 알림 종류는 메서드 이름(createPost, updateFolder, trashPost ...)을 그대로 쓴다.
 * 실시간 편집 저장(store)은 입력할 때마다 불리므로 여기서 빼고, 제목이 바뀔 때만 따로 알린다.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)  // 트랜잭션 안쪽에서 실행되어 커밋 후 알림으로 등록된다
public class WorkspaceLiveAspect {

    private static final Set<String> SKIP = Set.of("store", "deleteWorkspaceData");
    private static final Set<String> ID_PARAMS = Set.of("postId", "folderId", "newFolderId", "newParentId");

    private final WorkspaceLiveNotifier notifier;

    @AfterReturning("@annotation(com.untitles.global.cache.EvictWorkspaceCaches)")
    public void afterChange(JoinPoint joinPoint) {
        if (!notifier.isEnabled()) return;
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String action = signature.getName();
        if (SKIP.contains(action)) return;

        String[] names = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        Long workspaceId = null;
        Long actorId = null;
        Map<String, Long> ids = new LinkedHashMap<>();
        for (int i = 0; i < names.length && i < args.length; i++) {
            if (!(args[i] instanceof Long value)) continue;
            switch (names[i]) {
                case "workspaceId" -> workspaceId = value;
                case "userId" -> actorId = value;
                default -> {
                    if (ID_PARAMS.contains(names[i])) ids.put(names[i], value);
                }
            }
        }
        // 새로 만든 노트·폴더의 ID는 반환값에 있지만, 받는 쪽은 목록을 다시 불러오므로 없어도 된다
        notifier.notify(workspaceId, action, actorId, ids);
    }
}
