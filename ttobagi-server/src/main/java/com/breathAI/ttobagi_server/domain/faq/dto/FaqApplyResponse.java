package com.breathAI.ttobagi_server.domain.faq.dto;

import com.breathAI.ttobagi_server.domain.faq.entity.FaqEditHistory.EditType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
// FAQ 후보 반영 결과
public class FaqApplyResponse {
    private Long appliedFaqId;
    private Long candidateId;
    private EditType editType;
    private Long historyId;

    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
        timezone = "UTC"
    )
    private LocalDateTime appliedAt;
}
