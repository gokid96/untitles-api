package com.untitles.domain.image.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageService {
    private final S3Client s3Client;

    @Value("${cloudflare.r2.bucket}")
    private String bucket;

    @Value("${cloudflare.r2.public-url}")
    private String publicUrl;

    /**
     * 이미지 업로드
     */
    public String upload(MultipartFile file, String folder){
        //파일 검증
        validateImage(file);
        //고유 파일명 생성
        String fileName = folder + "/" + UUID.randomUUID() + getExtension(file.getOriginalFilename());

        try{
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
            return publicUrl + "/" + fileName;
        } catch (Exception e){
            throw new RuntimeException("이미지 업로드 실패", e);
        }
    }

/*
* 이미지 삭제
* */
    public void delete(String imageUrl){
        String key = imageUrl.replace(publicUrl + "/", "");

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        s3Client.deleteObject(request);
    }


/*
* 이미지 파일 검증
* */

    private void validateImage(MultipartFile file){
        if(file.isEmpty()){
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        //5MB 제한
        if(file.getSize() > 5 * 1024 * 1024){
            throw new IllegalArgumentException("파일 크기는 5MB 이하여야 합니다.");
        }
        //허용타입
        String contentType = file.getContentType();
        if(contentType==null || !contentType.startsWith("image/")){
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
    }

    private String getExtension(String filename){
        if(filename==null) return ".jpg";
        int idx = filename.lastIndexOf(".");
        return idx > 0 ? filename.substring(idx) : ".jpg";
    }
}








