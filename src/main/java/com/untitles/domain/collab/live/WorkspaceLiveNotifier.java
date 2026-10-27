package com.untitles.domain.collab.live;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 워크스페이스 안의 변경(노트·폴더 생성/이름 변경/이동/삭제, 멤버 변경 등)을
 * 실시간 편집 서버에 알려, 같은 워크스페이스를 보고 있는 사람들의 화면을 바로 갱신하게 한다.
 * - DB에 반영된 뒤(커밋 후)에만 보낸다
 * - 편집 서버가 없거나 응답하지 않아도 요청 처리에는 영향이 없다 (별도 스레드, 짧은 제한 시간)
 */
@Slf4j
@Component
public class WorkspaceLiveNotifier {

    private final RestClient client;
    private final String secret;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "workspace-live-notify");
        thread.setDaemon(true);
        return thread;
    });

    public WorkspaceLiveNotifier(@Value("${app.collab.secret:}") String secret,
                                 @Value("${app.collab.internal-url:http://collab:1234}") String internalUrl) {
        this.secret = secret;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(internalUrl).requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return StringUtils.hasText(secret);
    }

    /**
     * @param action 무엇이 바뀌었는지 (createPost, trashFolder, members ...)
     * @param ids    postId, folderId 등 (null 값은 빠짐)
     */
    public void notify(Long workspaceId, String action, Long actorId, Map<String, Long> ids) {
        if (!isEnabled() || workspaceId == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workspaceId", workspaceId);
        payload.put("action", action);
        if (actorId != null) payload.put("actorId", actorId);
        if (ids != null) ids.forEach((k, v) -> {
            if (v != null) payload.put(k, v);
        });

        Runnable send = () -> executor.execute(() -> {
            try {
                client.post().uri("/internal/notify")
                        .header("X-Collab-Secret", secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
            } catch (Exception e) {
                log.debug("워크스페이스 변경 알림 실패 (무시): {}", e.getMessage());
            }
        });

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
