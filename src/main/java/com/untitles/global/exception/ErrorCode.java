package com.untitles.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력입니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "이메일 인증이 필요합니다."),
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "이미 사용중인 이메일입니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.BAD_REQUEST, "이미 사용중인 아이디입니다."),
    DUPLICATE_NICKNAME(HttpStatus.BAD_REQUEST, "이미 사용중인 닉네임입니다."),
    POST_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "게시글은 워크스페이스당 최대 50개까지 생성할 수 있습니다."),
    FOLDER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "폴더는 워크스페이스당 최대 20개까지 생성할 수 있습니다."),
    WORKSPACE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "워크스페이스는 최대 3개까지 생성할 수 있습니다."),
    MEMBER_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "워크스페이스 멤버는 최대 5명까지 초대할 수 있습니다."),
    CANNOT_MOVE_TO_SELF(HttpStatus.BAD_REQUEST, "자기 자신으로 이동할 수 없습니다."),
    CANNOT_MOVE_TO_CHILD(HttpStatus.BAD_REQUEST, "하위 폴더로 이동할 수 없습니다."),
    CANNOT_DELETE_PERSONAL_WORKSPACE(HttpStatus.BAD_REQUEST, "개인 워크스페이스는 삭제할 수 없습니다."),
    CANNOT_INVITE_TO_PERSONAL_WORKSPACE(HttpStatus.BAD_REQUEST, "개인 워크스페이스에는 멤버를 초대할 수 없습니다."),
    CANNOT_ASSIGN_OWNER_ROLE(HttpStatus.BAD_REQUEST, "OWNER 권한은 부여할 수 없습니다."),
    ALREADY_WORKSPACE_MEMBER(HttpStatus.BAD_REQUEST, "이미 워크스페이스 멤버입니다."),
    CANNOT_MODIFY_HIGHER_ROLE(HttpStatus.BAD_REQUEST, "동급 이상의 권한은 변경할 수 없습니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.BAD_REQUEST, "OWNER는 워크스페이스를 나갈 수 없습니다."),

    // 400 Bad Request (이메일 인증 관련)
    ALREADY_REGISTERED_EMAIL(HttpStatus.BAD_REQUEST, "이미 가입된 이메일입니다."),
    VERIFICATION_NOT_REQUESTED(HttpStatus.BAD_REQUEST, "인증 요청을 먼저 해주세요."),
    VERIFICATION_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다."),
    VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "인증번호가 일치하지 않습니다."),

    // 401 Unauthorized
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    WRITE_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "쓰기 권한이 없습니다."),
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, "권한이 부족합니다."),
    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "폴더를 찾을 수 없습니다."),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "워크스페이스를 찾을 수 없습니다."),

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "멤버를 찾을 수 없습니다."),
    // 409 Conflict
    VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 수정되었습니다.");


    private final HttpStatus status;
    private final String message;
}