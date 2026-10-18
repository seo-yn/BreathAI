package com.breathAI.ttobagi_server.domain.dashboard;

import com.breathAI.ttobagi_server.domain.dashboard.repository.UploadFileRepository;
import com.breathAI.ttobagi_server.domain.dashboard.service.UploadArchiveService;
import com.breathAI.ttobagi_server.global.storage.ObjectStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// 업로드 원본 보관 (저장소 호출은 가짜로 바꿔 확인)
class UploadArchiveServiceTest {

    private final ObjectStorageProperties properties =
            new ObjectStorageProperties(true, "https://example.com", "kr-standard", "key", "secret", "test-uploads");
    private final UploadFileRepository repository = mock(UploadFileRepository.class);
    private final S3Client s3 = mock(S3Client.class);

    @SuppressWarnings("unchecked")
    private UploadArchiveService service(S3Client client) {
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return new UploadArchiveService(provider, properties, repository);
    }

    @Test
    @DisplayName("원본을 uploads/연/월/업로드ID.확장자로 올리고 위치를 기록한다")
    void storesOriginalAndRecordsPath() {
        service(s3).archive("upload-1", "data".getBytes(), "미답변 데이터.XLSX");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("test-uploads");
        assertThat(request.getValue().key()).matches("uploads/\\d{4}/\\d{2}/upload-1\\.xlsx");
        verify(repository).updateFilePath("upload-1", "s3://test-uploads/" + request.getValue().key());
    }

    @Test
    @DisplayName("저장에 실패하면 위치를 기록하지 않고 예외도 밖으로 내보내지 않는다")
    void failureIsSwallowed() {
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenThrow(new RuntimeException("down"));

        service(s3).archive("upload-2", "data".getBytes(), "data.csv");

        verify(repository, never()).updateFilePath(anyString(), anyString());
    }

    @Test
    @DisplayName("저장소가 꺼져 있으면 아무것도 하지 않는다")
    void disabledDoesNothing() {
        service(null).archive("upload-3", "data".getBytes(), "data.csv");

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("파일 이름이 없거나 확장자가 없어도 키를 만든다")
    void objectKeyWithoutExtension() {
        LocalDate date = LocalDate.of(2026, 3, 5);
        assertThat(callObjectKey("id", "noext", date)).isEqualTo("uploads/2026/03/id");
        assertThat(callObjectKey("id", null, date)).isEqualTo("uploads/2026/03/id");
        assertThat(callObjectKey("id", "a.b.CSV", date)).isEqualTo("uploads/2026/03/id.csv");
    }

    // 같은 패키지가 아니라서 실제 저장 호출로 키를 확인한다
    private String callObjectKey(String id, String name, LocalDate date) {
        S3Client client = mock(S3Client.class);
        service(client).archive(id, new byte[0], name);
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        String key = request.getValue().key();
        // 날짜 부분은 실행 시점이므로 고정 날짜로 바꿔 비교한다
        return key.replaceFirst("uploads/\\d{4}/\\d{2}/", String.format("uploads/%d/%02d/", date.getYear(), date.getMonthValue()));
    }
}
