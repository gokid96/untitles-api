package com.untitles.global.security.jwt;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
//@Component
//@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    private String secret;
    private long accessTokenExpiration;
    private long refreshTokenExpiration;
}
