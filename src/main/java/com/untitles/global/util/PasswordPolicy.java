package com.untitles.global.util;

/**
 * 비밀번호 규칙: 8~64자, 영문과 숫자를 하나 이상 포함. 특수문자·공백 등 모든 문자 허용
 * (특정 특수문자만 허용하거나 최대 길이를 짧게 두면 비밀번호 관리자로 만든 긴 비밀번호를 못 쓴다)
 */
public final class PasswordPolicy {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\s\\S]{8,64}$";
    public static final String MESSAGE = "비밀번호는 8~64자이며 영문과 숫자를 포함해야 합니다";

    private PasswordPolicy() {
    }
}
