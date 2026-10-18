package com.breathAI.ttobagi_server.global.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

// Object Storage 클라이언트 설정 (실제로 연결하지는 않는다)
class ObjectStorageConfigTest {

    @Test
    @DisplayName("NCP 주소와 리전으로 클라이언트를 만든다")
    void buildsClientForNcp() {
        ObjectStorageProperties properties = new ObjectStorageProperties(
                true, "https://kr.object.ncloudstorage.com", "kr-standard", "test-key", "test-secret", "breathai-uploads");

        try (S3Client client = new ObjectStorageConfig().s3Client(properties)) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("https://kr.object.ncloudstorage.com"));
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.of("kr-standard"));
        }
    }
}
