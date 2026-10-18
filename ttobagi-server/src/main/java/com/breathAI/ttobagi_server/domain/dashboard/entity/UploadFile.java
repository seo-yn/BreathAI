package com.breathAI.ttobagi_server.domain.dashboard.entity;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// 업로드 파일 메타 정보
@Entity
@Getter
@Table(
    name = "gold_upload_file",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_upload_file_name", columnNames = {"file_name"})
    },
    comment = "업로드 파일 메타 정보"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadFile {
    
    @Id
    @Column(name = "upload_id", length = 36, columnDefinition = "VARCHAR(36) COMMENT '파일 업로드 식별 UUID'")
    private String uploadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = true, foreignKey = @ForeignKey(name = "fk_upload_user_id"))
    private User uploadedBy;

    @Column(name = "original_file_name", nullable = false,
            columnDefinition = "VARCHAR(255) COMMENT '사용자가 올린 실제 파일명'")
    private String originalFileName;

    @Column(name = "file_name", nullable = false, unique = true,
            columnDefinition = "VARCHAR(255) COMMENT '서버 저장용 고유 파일명 (uuid_원본파일명)'")
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500, columnDefinition = "VARCHAR(500) COMMENT '파일 저장 경로'")
    private String filePath;

    @Column(name = "file_size", columnDefinition = "BIGINT COMMENT '파일 크기 (byte)'")
    private Long fileSize;

    @Column(name = "row_count", columnDefinition = "INT COMMENT '엑셀 행 수 (데이터 개수)'")
    private Integer rowCount;

    @Column(name = "status", nullable = false, length = 20, columnDefinition = "VARCHAR(20) COMMENT 'UPLOADED, INGESTED, FAIL'")
    @Enumerated(EnumType.STRING)
    private Status status;

    @Column(name = "error_message", columnDefinition = "TEXT COMMENT '적재 실패 시 사유'")
    private String errorMessage;

    @Column(name = "file_hash", length = 64, columnDefinition = "VARCHAR(64) COMMENT '파일 SHA-256 해시값 (중복 업로드 방지)'")
    private String fileHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    public enum Status {
        UPLOADED, INGESTED, FAIL
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = Status.UPLOADED;
    }

    @Builder
    public UploadFile(String uploadId, User uploadedBy, String fileName, String originalFileName, 
                    String filePath, Long fileSize, String fileHash) { 
        this.uploadId = uploadId;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
        this.originalFileName = originalFileName;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.fileHash = fileHash;
        this.status = Status.UPLOADED;
    }

    // 적재 완료 처리, 데이터 행 수 기록
    public void ingested(int rowCount) {
        this.status = Status.INGESTED;
        this.rowCount = rowCount;
    }

    // 적재 실패 처리, 사유 기록
    public void fail(String errorMessage) {
        this.status = Status.FAIL;
        this.errorMessage = errorMessage;
    }

    // 업로더 참조 해제, 회원 탈퇴 시 사용
    public void clearUploadedBy() {
        this.uploadedBy = null;
    }
}
