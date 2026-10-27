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

    /** 문서 불러오기. ydoc은 유효한 저장 상태가 있을 때만 (Base64) */
    public record DocumentResponse(String title, String content, String hash, String ydoc) {
    }

    /** 문서 저장. baseHash는 편집 서버가 마지막으로 알고 있던 해시 */
    public record StoreRequest(String title, String content, String ydoc, String baseHash) {
    }

    /** 저장 결과. 충돌(409) 시에는 현재 DB 내용을 함께 돌려준다 */
    public record StoreResponse(String hash, String title, String content) {
    }
}
