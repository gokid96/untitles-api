package com.untitles.domain.image.controller;

import com.untitles.domain.image.service.ImageService;
import com.untitles.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.Map;

@RestController
@RequestMapping("/api/v1/images")
@RequiredArgsConstructor
public class ImageController{

    private final ImageService imageService;

    /**
     * 게시글 이미지 업로드
     */
    @PostMapping("/post")
    public ResponseEntity<Map<String, String>> uploadPostImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {

        String url = imageService.upload(file, "posts");
        return ResponseEntity.ok(Map.of("url", url));
    }

    /**
     * 프로필 이미지 업로드
     */
    @PostMapping("/profile")
    public ResponseEntity<Map<String, String>> uploadProfileImage(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") MultipartFile file) {

        String url = imageService.upload(file, "profiles");
        return ResponseEntity.ok(Map.of("url", url));
    }
}
