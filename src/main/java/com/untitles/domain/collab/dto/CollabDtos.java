package com.untitles.domain.collab.dto;

/**
 * 실시간 편집 서버(Hocuspocus) ↔ API 내부 통신용
 */
public final class CollabDtos {

    private CollabDtos() {
    }

    /** 접속 권한 */
    public record AccessResponse(boolean canRead, boolean canWrite) {
    }

    /**
     * 문서 불러오기. ydoc은 저장된 편집 상태(Base64, 없으면 null).
     * stale이면 그 상태 이후 다른 경로로 바뀐 것이므로 편집 서버가 title·content를 그 위에 반영해야 한다
     */
    public record DocumentResponse(String title, String content, String hash, String ydoc, boolean stale) {
    }

    /** 문서 저장. baseHash는 편집 서버가 마지막으로 알고 있던 해시 */
    public record StoreRequest(String title, String content, String ydoc, String baseHash) {
    }

    /** 저장 결과. 충돌(409) 시에는 현재 DB 내용을 함께 돌려준다 */
    public record StoreResponse(String hash, String title, String content) {
    }
}
