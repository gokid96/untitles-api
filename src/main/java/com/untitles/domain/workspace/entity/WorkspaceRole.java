package com.untitles.domain.workspace.entity;

import lombok.Getter;

@Getter
public enum WorkspaceRole {
    OWNER (0),      // 소유자 (삭제, 모든 권한)
    ADMIN (1),      // 관리자 (멤버 관리, 설정 변경)
    MEMBER(2),     // 일반 멤버 (읽기/쓰기)
    VIEWER(3);      // 뷰어 (읽기만)
    private final int level;

    WorkspaceRole(int level) {
        this.level = level;
    }

    public boolean hasPermission(WorkspaceRole required)   {
        return this.level <= required.level;
    }
}