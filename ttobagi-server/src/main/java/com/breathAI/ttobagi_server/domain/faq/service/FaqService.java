package com.breathAI.ttobagi_server.domain.faq.service;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.auth.repository.UserRepository;
import com.breathAI.ttobagi_server.domain.faq.dto.*;
import com.breathAI.ttobagi_server.domain.faq.entity.*;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate.CandidateType;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqCandidate.ReviewStatus;
import com.breathAI.ttobagi_server.domain.faq.entity.FaqEditHistory.EditType;
import com.breathAI.ttobagi_server.domain.faq.repository.*;
import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 운영 FAQ 관리 및 AI 생성 FAQ 후보 반영 처리
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;
    private final FaqCandidateRepository faqCandidateRepository;
    private final FaqCandidateMatchRepository faqCandidateMatchRepository;
    private final SynonymCandidateRepository synonymCandidateRepository;
    private final FaqActionLogRepository faqActionLogRepository;
    private final FaqEditHistoryRepository faqEditHistoryRepository;
    private final FaqVersionRepository faqVersionRepository;
    private final FaqVersionItemRepository faqVersionItemRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // FAQ 목록 조회, 활성 항목만 최신순 페이징
    // versionId가 있으면 그 버전 시점의 FAQ를, 없으면 현재 운영 FAQ를 조회한다
    // 카테고리는 마스터의 표시명과 일치하는 코드로, 키워드는 질문·답변·키워드 목록에서 검색
    @Transactional(readOnly = true)
    public FaqListResponse getFaqList(Long versionId, int page, int size, String category, String keyword) {
        FaqVersion version = null;
        if (versionId != null) {
            version = faqVersionRepository.findById(versionId)
                    .orElseThrow(() -> new CustomException(ErrorCode.FAQ_VERSION_NOT_FOUND));
        }
        Long responseVersionId = version != null ? version.getVersionId() : null;
        String responseVersionName = version != null ? version.getVersionName() : null;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // 카테고리 조건이 있을 때만 마스터를 조회한다
        String categoryName = blankToNull(category);
        List<Integer> qTypes = List.of();
        if (categoryName != null) {
            qTypes = faqRepository.findQTypesByCategoryName(categoryName);
            // 마스터에 없는 카테고리명이면 결과 없음
            if (qTypes.isEmpty()) {
                return FaqListResponse.builder()
                        .versionId(responseVersionId)
                        .versionName(responseVersionName)
                        .faqList(List.of())
                        .totalCount(0)
                        .totalPages(0)
                        .currentPage(page)
                        .size(size)
                        .build();
            }
        }

        if (version != null) {
            Page<FaqVersionItem> itemPage = faqVersionItemRepository.search(
                    version.getVersionId(), categoryName != null, qTypes, blankToNull(keyword),
                    PageRequest.of(page, size, Sort.by("faqCreatedAt").descending()));

            return FaqListResponse.builder()
                    .versionId(responseVersionId)
                    .versionName(responseVersionName)
                    .faqList(itemPage.getContent().stream()
                            .map(i -> FaqListResponse.FaqItem.builder()
                                    .faqId(i.getFaqId())
                                    .standardQuestion(i.getQuestion())
                                    .answer(i.getAnswer())
                                    .keywords(parseKeywords(i.getKeywords()))
                                    .qType(i.getQType())
                                    .category(i.getCategory())
                                    .createdAt(i.getFaqCreatedAt().toLocalDate())
                                    .build())
                            .collect(Collectors.toList()))
                    .totalCount(itemPage.getTotalElements())
                    .totalPages(itemPage.getTotalPages())
                    .currentPage(page)
                    .size(size)
                    .build();
        }

        Page<Faq> faqPage = faqRepository.search(
                categoryName != null, qTypes, blankToNull(keyword), pageRequest);

        List<FaqListResponse.FaqItem> items = faqPage.getContent().stream()
                .map(f -> FaqListResponse.FaqItem.builder()
                        .faqId(f.getFaqId())
                        .standardQuestion(f.getQuestion())
                        .answer(f.getAnswer())
                        .keywords(parseKeywords(f.getKeywords()))
                        .qType(f.getQType())
                        .category(f.getCategory())
                        .createdAt(f.getCreatedAt().toLocalDate())
                        .build())
                .collect(Collectors.toList());

        return FaqListResponse.builder()
                .faqList(items)
                .totalCount(faqPage.getTotalElements())
                .totalPages(faqPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    // FAQ 단건 상세 조회
    @Transactional(readOnly = true)
    public FaqDetailResponse getFaqDetail(Long faqId) {
        Faq faq = faqRepository.findByFaqIdAndIsActiveTrue(faqId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_NOT_FOUND));

        return FaqDetailResponse.builder()
                .faqId(faq.getFaqId())
                .standardQuestion(faq.getQuestion())
                .answer(faq.getAnswer())
                .keywords(parseKeywords(faq.getKeywords()))
                .qType(faq.getQType())
                .category(faq.getCategory())
                .qaCnt(faq.getQaCnt())
                .createdAt(faq.getCreatedAt().toLocalDate())
                .updatedAt(faq.getUpdatedAt().toLocalDate())
                .build();
    }

    // FAQ 수정, 변경 전후 값을 이력에 남긴다
    @Transactional
    public FaqListUpdateResponse updateFaq(Long faqId, FaqListUpdateRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Faq faq = faqRepository.findByFaqIdAndIsActiveTrue(faqId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_NOT_FOUND));

        String beforeQuestion = faq.getQuestion();
        String beforeAnswer = faq.getAnswer();
        List<String> beforeKeywords = parseKeywords(faq.getKeywords());

        faq.update(request.getStandardQuestion(), request.getAnswer(),
                toKeywordsJson(request.getKeywords()), request.getQType());
        // 수정 시각이 응답에 반영되도록 즉시 반영한다
        faqRepository.saveAndFlush(faq);

        // 요청값이 아닌 반영 결과를 기록해야 일부 필드만 수정한 경우에도 이력이 정확하다
        List<String> afterKeywords = parseKeywords(faq.getKeywords());
        FaqEditHistory history = FaqEditHistory.builder()
                .faq(faq)
                .editedBy(user)
                .editType(EditType.MANUAL)
                .beforeQuestion(beforeQuestion)
                .beforeAnswer(beforeAnswer)
                .beforeKeywords(beforeKeywords)
                .afterQuestion(faq.getQuestion())
                .afterAnswer(faq.getAnswer())
                .afterKeywords(afterKeywords)
                .editReason(request.getEditReason())
                .build();
        faqEditHistoryRepository.save(history);

        return FaqListUpdateResponse.builder()
                .faqId(faq.getFaqId())
                .qType(faq.getQType())
                .question(faq.getQuestion())
                .answer(faq.getAnswer())
                .keywords(afterKeywords)
                .historyId(history.getHistoryId())
                .updatedAt(faq.getUpdatedAt())
                .build();
    }

    // FAQ 삭제, 실제 삭제 없이 비활성 처리하고 삭제 이력을 남긴다
    @Transactional
    public void deleteFaq(Long faqId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Faq faq = faqRepository.findByFaqIdAndIsActiveTrue(faqId)
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_NOT_FOUND));

        faq.deactivate();

        faqEditHistoryRepository.save(FaqEditHistory.builder()
                .faq(faq)
                .editedBy(user)
                .editType(EditType.DELETE)
                .beforeQuestion(faq.getQuestion())
                .beforeAnswer(faq.getAnswer())
                .beforeKeywords(parseKeywords(faq.getKeywords()))
                .build());
        log.info("FAQ 삭제 완료: faqId={}", faqId);
    }

    // FAQ 변경 이력 조회, 전체 FAQ 대상 최신순 페이징
    @Transactional(readOnly = true)
    public FaqEditHistoryResponse getFaqHistory(int page, int size, EditType editType) {
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending().and(Sort.by("historyId").descending()));
        Page<FaqEditHistory> historyPage = faqEditHistoryRepository.search(editType, pageRequest);

        List<FaqEditHistoryResponse.HistoryItem> items =
                historyPage.getContent().stream()
                        .map(h -> FaqEditHistoryResponse.HistoryItem.builder()
                                .historyId(h.getHistoryId())
                                .faqId(h.getFaq().getFaqId())
                                .editType(h.getEditType())
                                .analysisId(h.getAnalysisJob() != null
                                        ? h.getAnalysisJob().getAnalysisId() : null)
                                .versionId(h.getVersion() != null
                                        ? h.getVersion().getVersionId() : null)
                                .beforeQuestion(h.getBeforeQuestion())
                                .beforeAnswer(h.getBeforeAnswer())
                                .beforeKeywords(h.getBeforeKeywords())
                                .afterQuestion(h.getAfterQuestion())
                                .afterAnswer(h.getAfterAnswer())
                                .afterKeywords(h.getAfterKeywords())
                                .editReason(h.getEditReason())
                                // 탈퇴한 사용자의 이력은 수정자 정보 없이 반환
                                .editedBy(h.getEditedBy() != null
                                        ? h.getEditedBy().getEmail() : null)
                                .createdAt(h.getCreatedAt())
                                .build())
                        .collect(Collectors.toList());

        return FaqEditHistoryResponse.builder()
                .totalCount(historyPage.getTotalElements())
                .histories(items)
                .totalPages(historyPage.getTotalPages())
                .currentPage(page)
                .size(size)
                .build();
    }

    // 분석 결과 기반 FAQ 후보 추천 목록 조회
    @Transactional(readOnly = true)
    public FaqCandidateListResponse getFaqRecommendations(Long analysisId) {
        List<FaqCandidate> candidates = faqCandidateRepository
                .findByAnalysisJobAnalysisId(analysisId);

        // 후보별 유사 FAQ 매칭과, 매칭된 순번에 해당하는 운영 FAQ를 한 번에 조회한다
        Map<Long, List<FaqCandidateMatch>> matchesByCandidate = faqCandidateMatchRepository
                .findByCandidate_AnalysisJob_AnalysisIdOrderByMatchScoreDesc(analysisId).stream()
                .collect(Collectors.groupingBy(m -> m.getCandidate().getCandidateId()));

        List<Integer> seqNums = matchesByCandidate.values().stream()
                .flatMap(List::stream)
                .map(FaqCandidateMatch::getMatchedFaqSeqNum)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, Faq> faqBySeqNum = seqNums.isEmpty()
                ? Map.of()
                : faqRepository.findBySourceSeqNumIn(seqNums).stream()
                        .collect(Collectors.toMap(Faq::getSourceSeqNum, f -> f, (a, b) -> a));

        List<FaqCandidateListResponse.FaqCandidateItem> items = candidates.stream()
                .map(c -> buildFaqCandidateItem(c,
                        matchesByCandidate.getOrDefault(c.getCandidateId(), List.of()), faqBySeqNum))
                .collect(Collectors.toList());

        return FaqCandidateListResponse.builder()
                .analysisId(analysisId)
                .recommendations(items)
                .build();
    }

    // FAQ 후보를 운영 FAQ로 반영
    // NEW 후보는 FAQ를 새로 등록하고, EXPAND 후보는 가장 유사한 기존 FAQ를 확장한다
    // 상태 전이는 PENDING -> ACCEPTED -> APPLIED 순이며, 그 외 상태는 반영 대상이 아니다
    @Transactional
    public FaqApplyResponse applyFaqCandidate(FaqApplyRequest request, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        FaqCandidate candidate = faqCandidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_CANDIDATE_NOT_FOUND));

        ReviewStatus beforeStatus = candidate.getReviewStatus();

        // 이미 반영되었거나 반려된 후보는 재반영할 수 없다
        if (beforeStatus != ReviewStatus.PENDING && beforeStatus != ReviewStatus.ACCEPTED) {
            throw new CustomException(ErrorCode.FAQ_CANDIDATE_NOT_APPLICABLE);
        }

        boolean isExpand = candidate.getCandidateType() == CandidateType.EXPAND;

        // 확장 대상이 없으면 후보 상태를 바꾸기 전에 거부한다
        Faq targetFaq = isExpand ? findExpandTarget(candidate) : null;

        // 승인 절차를 거치지 않은 후보는 반영과 동시에 승인 처리한다
        if (beforeStatus == ReviewStatus.PENDING) {
            candidate.accept(user);
        }
        candidate.apply();

        Faq appliedFaq;
        String beforeQuestion = null;
        String beforeAnswer = null;
        List<String> beforeKeywords = null;

        if (isExpand) {
            beforeQuestion = targetFaq.getQuestion();
            beforeAnswer = targetFaq.getAnswer();
            beforeKeywords = parseKeywords(targetFaq.getKeywords());

            // 키워드는 기존 것에 합치고, 질문과 답변은 운영자가 확정한 값으로 바꾼다
            LinkedHashSet<String> mergedKeywords = new LinkedHashSet<>(beforeKeywords);
            if (request.getKeywords() != null) {
                mergedKeywords.addAll(request.getKeywords());
            }
            targetFaq.update(request.getFinalQuestion(), request.getFinalAnswer(),
                    toKeywordsJson(new ArrayList<>(mergedKeywords)), request.getQType());
            // 수정 시각이 응답에 반영되도록 즉시 반영한다
            appliedFaq = faqRepository.saveAndFlush(targetFaq);
        } else {
            appliedFaq = faqRepository.save(Faq.builder()
                    .candidate(candidate)
                    .question(request.getFinalQuestion())
                    .answer(request.getFinalAnswer())
                    .keywords(toKeywordsJson(request.getKeywords()))
                    // 운영자가 카테고리 코드를 지정하지 않으면 후보의 값을 따른다
                    .qType(request.getQType() != null ? request.getQType() : candidate.getQType())
                    .category(candidate.getCategory())
                    .createdBy(user)
                    .build());
        }

        FaqActionLog actionLog = FaqActionLog.builder()
                .candidate(candidate)
                .actedBy(user)
                .action(FaqActionLog.Action.APPLY)
                .beforeStatus(beforeStatus)
                .afterStatus(ReviewStatus.APPLIED)
                .note(Boolean.TRUE.equals(request.getIsManualPatchConfirmed())
                        ? "수동 반영 확인됨" : null)
                .build();
        faqActionLogRepository.save(actionLog);

        // 신규 등록은 CREATE, 기존 FAQ 확장은 EXPAND로 이력을 남긴다
        EditType editType = isExpand ? EditType.EXPAND : EditType.CREATE;
        FaqEditHistory history = FaqEditHistory.builder()
                .faq(appliedFaq)
                .analysisJob(candidate.getAnalysisJob())
                .editedBy(user)
                .editType(editType)
                .beforeQuestion(beforeQuestion)
                .beforeAnswer(beforeAnswer)
                .beforeKeywords(beforeKeywords)
                .afterQuestion(appliedFaq.getQuestion())
                .afterAnswer(appliedFaq.getAnswer())
                .afterKeywords(parseKeywords(appliedFaq.getKeywords()))
                .build();
        faqEditHistoryRepository.save(history);

        log.info("FAQ 후보 반영 완료: candidateId={}, faqId={}, editType={}",
                candidate.getCandidateId(), appliedFaq.getFaqId(), editType);

        return FaqApplyResponse.builder()
                .appliedFaqId(appliedFaq.getFaqId())
                .candidateId(candidate.getCandidateId())
                .editType(editType)
                .historyId(history.getHistoryId())
                .appliedAt(isExpand ? appliedFaq.getUpdatedAt() : appliedFaq.getCreatedAt())
                .build();
    }

    // EXPAND 후보의 확장 대상 조회
    // 후보의 유사 FAQ 중 점수가 가장 높은 것을 원본 순번으로 운영 FAQ에서 찾는다
    private Faq findExpandTarget(FaqCandidate candidate) {
        return faqCandidateMatchRepository
                .findByCandidateCandidateIdOrderByMatchScoreDesc(candidate.getCandidateId()).stream()
                .findFirst()
                .flatMap(m -> faqRepository
                        .findFirstBySourceSeqNumAndIsActiveTrueOrderByFaqIdAsc(m.getMatchedFaqSeqNum()))
                .orElseThrow(() -> new CustomException(ErrorCode.FAQ_EXPAND_TARGET_NOT_FOUND));
    }

    // 후보 단건을 응답 DTO로 변환, 유사어와 유사 FAQ 목록 포함
    private FaqCandidateListResponse.FaqCandidateItem buildFaqCandidateItem(
            FaqCandidate candidate, List<FaqCandidateMatch> matches, Map<Integer, Faq> faqBySeqNum) {
        List<FaqCandidateListResponse.FaqCandidateItem.SynonymItem> synonyms =
                synonymCandidateRepository
                        .findByCandidateCandidateId(candidate.getCandidateId())
                        .stream()
                        .map(s -> FaqCandidateListResponse.FaqCandidateItem.SynonymItem.builder()
                                .text(s.getSynonymText())
                                .type(s.getSynonymType())
                                .build())
                        .collect(Collectors.toList());

        // 매칭된 순번의 FAQ가 운영 FAQ에 없으면 ID와 질문은 비워 둔다
        List<FaqCandidateListResponse.FaqCandidateItem.MatchedFaqItem> matchedFaqs = matches.stream()
                .map(m -> {
                    Faq matched = faqBySeqNum.get(m.getMatchedFaqSeqNum());
                    return FaqCandidateListResponse.FaqCandidateItem.MatchedFaqItem.builder()
                            .matchedFaqId(matched != null ? matched.getFaqId() : null)
                            .matchedFaqSeqNum(m.getMatchedFaqSeqNum())
                            .question(matched != null ? matched.getQuestion() : null)
                            .matchScore(m.getMatchScore().doubleValue())
                            .build();
                })
                .collect(Collectors.toList());

        return FaqCandidateListResponse.FaqCandidateItem.builder()
                .clusterId(candidate.getCluster() != null
                        ? candidate.getCluster().getClusterId() : null)
                .clusterLabel(candidate.getCluster() != null
                        ? candidate.getCluster().getClusterLabel() : null)
                .candidateId(candidate.getCandidateId())
                .candidateType(candidate.getCandidateType())
                .qType(candidate.getQType())
                .category(candidate.getCategory())
                .standardQuestion(candidate.getStandardQuestion())
                .similarQuestions(candidate.getSimilarQuestions())
                .answerDraft(candidate.getAnswerDraft())
                .reviewStatus(candidate.getReviewStatus())
                .representativeKeywords(candidate.getRepresentativeKeywords())
                .occurrenceCount(candidate.getOccurrenceCount())
                .synonyms(synonyms)
                .matchedFaqs(matchedFaqs)
                .createdAt(candidate.getCreatedAt())
                .build();
    }

    // 키워드 리스트를 저장용 JSON 문자열로 변환, 없거나 실패하면 null
    private String toKeywordsJson(List<String> keywords) {
        if (keywords == null) return null;
        try {
            return objectMapper.writeValueAsString(keywords);
        } catch (Exception e) {
            log.warn("키워드 직렬화 실패");
            return null;
        }
    }

    // 빈 검색 조건은 미지정으로 취급
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    // JSON 문자열로 저장된 키워드를 리스트로 변환, 실패 시 빈 리스트 반환
    private List<String> parseKeywords(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("키워드 파싱 실패: {}", json);
            return List.of();
        }
    }
}
