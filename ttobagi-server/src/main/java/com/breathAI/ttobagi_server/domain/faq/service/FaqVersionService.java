package com.breathAI.ttobagi_server.domain.faq.service;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.faq.dto.FaqVersionDetailResponse;
import com.breathAI.ttobagi_server.domain.faq.dto.FaqVersionListResponse;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqEditHistory;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqEditHistory.EditType;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqVersion;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqVersion.VersionType;
import com.breathAI.ttobagi_server.domain.faq.repository.FaqEditHistoryRepository;
import com.breathAI.ttobagi_server.domain.faq.repository.FaqRepository;
import com.breathAI.ttobagi_server.domain.faq.repository.FaqVersionItemRepository;
import com.breathAI.ttobagi_server.domain.faq.repository.FaqVersionRepository;
import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// FAQ 버전 생성 및 조회
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqVersionService {

    // 버전 이름의 날짜는 운영자가 보는 값이라 한국 시간 기준으로 붙인다
    private static final ZoneId NAME_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter NAME_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final FaqVersionRepository faqVersionRepository;
    private final FaqVersionItemRepository faqVersionItemRepository;
    private final FaqEditHistoryRepository faqEditHistoryRepository;
    private final FaqRepository faqRepository;

    // 버전 생성, 엑셀 업로드·다운로드 시점에 호출된다
    // 직전 버전 이후의 변경 이력을 새 버전에 묶고, 현재 활성 FAQ 전체를 사본으로 남긴다
    @Transactional
    public FaqVersion createVersion(VersionType versionType, User createdBy) {
        FaqVersion previous = faqVersionRepository.findTopByOrderByVersionIdDesc().orElse(null);

        Map<EditType, Long> pendingCounts = new EnumMap<>(EditType.class);
        for (Object[] row : faqEditHistoryRepository.countPendingByEditType()) {
            pendingCounts.put((EditType) row[0], (Long) row[1]);
        }

        // 이름에 버전 번호가 들어가므로 먼저 저장해 번호를 받는다
        FaqVersion version = faqVersionRepository.saveAndFlush(FaqVersion.builder()
                .versionName("")
                .versionType(versionType)
                .totalFaqCount((int) faqRepository.countByIsActiveTrue())
                .createdCount(pendingCounts.getOrDefault(EditType.CREATE, 0L).intValue())
                .expandedCount(pendingCounts.getOrDefault(EditType.EXPAND, 0L).intValue())
                .manualCount(pendingCounts.getOrDefault(EditType.MANUAL, 0L).intValue())
                .deletedCount(pendingCounts.getOrDefault(EditType.DELETE, 0L).intValue())
                .previousVersion(previous)
                .createdBy(createdBy)
                .build());
        version.rename("v" + version.getVersionId() + "_" + LocalDate.now(NAME_ZONE).format(NAME_DATE));

        int assigned = faqEditHistoryRepository.assignPendingToVersion(version);
        int copied = faqVersionItemRepository.snapshotActiveFaqs(version.getVersionId());

        log.info("FAQ 버전 생성: versionId={}, name={}, type={}, 변경 {}건, FAQ {}건",
                version.getVersionId(), version.getVersionName(), versionType, assigned, copied);
        return version;
    }

    // 버전 목록 조회, 최신순 페이징
    @Transactional(readOnly = true)
    public FaqVersionListResponse getVersions(int page, int size) {
        Page<FaqVersion> versionPage = faqVersionRepository.findAllByOrderByVersionIdDesc(PageRequest.of(page, size));
        Long latestId = latestVersionId();

        List<FaqVersionListResponse.VersionItem> items = versionPage.getContent().stream()
                .map(v -> FaqVersionListResponse.VersionItem.builder()
                        .versionId(v.getVersionId())
                        .versionName(v.getVersionName())
                        .versionType(v.getVersionType())
                        .totalFaqCount(v.getTotalFaqCount())
                        .createdCount(v.getCreatedCount())
                        .expandedCount(v.getExpandedCount())
                        .isLatest(v.getVersionId().equals(latestId))
                        // 탈퇴한 사용자가 만든 버전은 생성자 정보 없이 반환
                        .createdBy(v.getCreatedBy() != null ? v.getCreatedBy().getEmail() : null)
                        .createdAt(v.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return FaqVersionListResponse.builder()
                .totalCount(versionPage.getTotalElements())
                .versions(items)
                .totalPages(versionPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    // 버전 상세 조회, 이 버전에 묶인 변경 목록 포함
    @Transactional(readOnly = true)
    public FaqVersionDetailResponse getVersionDetail(Long versionId) {
        FaqVersion version = getVersion(versionId);

        List<FaqVersionDetailResponse.ChangeItem> changes =
                faqEditHistoryRepository.findByVersion_VersionIdOrderByHistoryIdAsc(versionId).stream()
                        .map(this::toChangeItem)
                        .collect(Collectors.toList());

        return FaqVersionDetailResponse.builder()
                .versionId(version.getVersionId())
                .versionName(version.getVersionName())
                .versionType(version.getVersionType())
                .totalFaqCount(version.getTotalFaqCount())
                .createdCount(version.getCreatedCount())
                .expandedCount(version.getExpandedCount())
                .manualCount(version.getManualCount())
                .deletedCount(version.getDeletedCount())
                .previousVersionId(version.getPreviousVersion() != null
                        ? version.getPreviousVersion().getVersionId() : null)
                .isLatest(version.getVersionId().equals(latestVersionId()))
                .createdBy(version.getCreatedBy() != null ? version.getCreatedBy().getEmail() : null)
                .createdAt(version.getCreatedAt())
                .changes(changes)
                .build();
    }

    // 버전 단건 조회, 없으면 404
    @Transactional(readOnly = true)
    public FaqVersion getVersion(Long versionId) {
        return faqVersionRepository.findById(versionId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_VERSION_NOT_FOUND));
    }

    private Long latestVersionId() {
        return faqVersionRepository.findTopByOrderByVersionIdDesc()
                .map(FaqVersion::getVersionId)
                .orElse(null);
    }

    // 삭제 이력은 수정 후 값이 없으므로 수정 전 질문을 보여준다
    private FaqVersionDetailResponse.ChangeItem toChangeItem(FaqEditHistory history) {
        return FaqVersionDetailResponse.ChangeItem.builder()
                .historyId(history.getHistoryId())
                .faqId(history.getFaq().getFaqId())
                .editType(history.getEditType())
                .qType(history.getFaq().getQType())
                .standardQuestion(history.getAfterQuestion() != null
                        ? history.getAfterQuestion() : history.getBeforeQuestion())
                .build();
    }
}
