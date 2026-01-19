package com.untitles.domain.image.repository;

import com.untitles.domain.image.entity.Image;
import com.untitles.domain.user.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImageRepository extends JpaRepository<Image, Long> {
    List<Image> findByUploaderOrderByCreatedAtDesc(Users uploader);
    Optional<Image> findByStoredName(String storedName);
}