package com.untitles.domain.image.repository;

import com.untitles.domain.image.entity.UploadedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface UploadedImageRepository extends JpaRepository<UploadedImage, Long> {

    // 사용자가 지금 차지하고 있는 저장 용량 (바이트)
    @Query("SELECT COALESCE(SUM(i.sizeBytes), 0) FROM UploadedImage i WHERE i.userId = :userId")
    long sumSizeByUserId(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM UploadedImage i WHERE i.objectKey IN :keys")
    void deleteByObjectKeyIn(@Param("keys") Collection<String> keys);
}
