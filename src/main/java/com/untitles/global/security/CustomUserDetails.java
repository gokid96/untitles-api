package com.untitles.global.security;

import com.untitles.domain.user.entity.Users;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Users user;

    // JWT 필터 전용 필드 (DB 조회 없이 생성할 때 사용)
    private final Long userId;
    private final String loginId;

    // 기존 생성자 (로그인 시 사용)
    public CustomUserDetails(Users user) {
        this.user = user;
        this.userId = null;
        this.loginId = null;
    }

    // JWT 필터 전용 생성자 (DB 조회 없음)
    public CustomUserDetails(Long userId, String loginId) {
        this.user = null;
        this.userId = userId;
        this.loginId = loginId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 1인 블로그라 권한 구분 불필요, 기본 USER 권한 부여
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getUsername() {
        return user != null ? user.getUserId().toString() : String.valueOf(userId);
    }

    @Override
    public String getPassword() {
        return user != null ? user.getPassword() : null;
    }

    public Long getUserId() {
        return user != null ? user.getUserId() : userId;
    }

    public String getLoginId() {
        return user != null ? user.getLoginId() : loginId;
    }

    public String getNickname() {
        return user != null ? user.getNickname() : null;
    }

    public String getEmail() {
        return user != null ? user.getEmail() : null;
    }

    public String getProfileImage() {
        return user != null ? user.getProfileImage() : null;
    }
}
