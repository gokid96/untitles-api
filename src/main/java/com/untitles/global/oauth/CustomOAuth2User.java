package com.untitles.global.oauth;

import com.untitles.domain.user.entity.Users;
import com.untitles.global.security.CustomUserDetails;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomOAuth2User extends CustomUserDetails implements OAuth2User {
    private final Map<String, Object> attributes;

    public CustomOAuth2User(Users user, Map<String, Object> attributes) {
        super(user);
        this.attributes = attributes;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return super.getAuthorities();
    }

    @Override
    public String getName() {
        return getEmail();
    }
}