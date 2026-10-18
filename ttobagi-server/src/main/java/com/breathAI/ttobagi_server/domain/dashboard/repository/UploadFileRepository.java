package com.breathAI.ttobagi_server.domain.dashboard.repository;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.dashboard.entity.UploadFile;
import com.breathAI.ttobagi_server.domain.dashboard.entity.UploadFile.Status;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

// 업로드 파일 리포지토리
public interface UploadFileRepository extends JpaRepository<UploadFile, String> {
    Optional<UploadFile> findByUploadId(String uploadId);
    List<UploadFile> findByUploadedBy(User user);
    Optional<UploadFile> findByFileName(String fileName);
    Page<UploadFile> findByUploadedByOrderByCreatedAtDesc(User uploadedBy, Pageable pageable);
    List<UploadFile> findByStatusOrderByCreatedAtDesc(Status status);
    boolean existsByFileNameAndUploadedBy(String fileName, User uploadedBy);
    long countByUploadedBy(User uploadedBy);
    void deleteByUploadedBy(User user);
    Optional<UploadFile> findByFileHash(String fileHash);

    // 원본 보관 위치만 기록한다
    // 같은 시점에 AI 콜백이 업로드 기록의 다른 값을 바꿀 수 있어 엔티티 전체를 저장하지 않는다
    @Modifying
    @Transactional
    @Query("UPDATE UploadFile u SET u.filePath = :filePath WHERE u.uploadId = :uploadId")
    int updateFilePath(@Param("uploadId") String uploadId, @Param("filePath") String filePath);
}