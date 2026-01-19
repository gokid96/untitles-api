package com.untitles.domain.workspace.entity;

public enum WorkspaceRole {
    OWNER,      // 소유자 (삭제, 모든 권한)
    ADMIN,      // 관리자 (멤버 관리, 설정 변경)
    MEMBER,     // 일반 멤버 (읽기/쓰기)
    VIEWER      // 뷰어 (읽기만)
}