package com.breathAI.ttobagi_server.domain.dashboard.service;

import com.breathAI.ttobagi_server.domain.auth.entity.User;
import com.breathAI.ttobagi_server.domain.auth.repository.UserRepository;
import com.breathAI.ttobagi_server.domain.dashboard.dto.*;
import com.breathAI.ttobagi_server.domain.dashboard.entity.*;
import com.breathAI.ttobagi_server.domain.dashboard.repository.*;
import com.breathAI.ttobagi_server.domain.faq.entity.*;
import com.breathAI.ttobagi_server.domain.faq.repository.*;
import com.breathAI.ttobagi_server.global.enums.UnansweredReason;
import com.breathAI.ttobagi_server.global.exception.CustomException;
import com.breathAI.ttobagi_server.global.exception.ErrorCode;
import com.breathAI.ttobagi_server.global.util.WebClientUtil;
import com.breathAI.ttobagi_server.global.util.SseEmitterManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.codec.digest.DigestUtils;
import java.io.IOException;
import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// 파일 업로드, AI 분석 실행 및 콜백 수신, 대시보드 지표 조회
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UploadFileRepository uploadFileRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final UsageStatRepository usageStatRepository;
    private final UserRepository userRepository;
    private final WebClientUtil webClientUtil;
    private final ClusterRepository clusterRepository;
    private final ClusterLogMapRepository clusterLogMapRepository;
    private final FaqCandidateRepository faqCandidateRepository;
    private final SynonymCandidateRepository synonymCandidateRepository;
    private final FaqCandidateMatchRepository faqCandidateMatchRepository;
    private final UnansweredStatRepository unansweredStatRepository;
    private final SurgeDetectionRepository surgeDetectionRepository;
    private final SurgeStatRepository surgeStatRepository;
    private final PerformanceComparisonRepository performanceComparisonRepository;
    private final StatDateRepository statDateRepository;
    private final ObjectMapper objectMapper;
    private final SseEmitterManager sseEmitterManager;

    // 월 단위 챗봇 사용량 조회, 전월 대비 증가율 포함
    @Transactional(readOnly = true)
    public UsageResponse getUsage(int year, int month) {
        List<UsageStat> currentMonthStats = usageStatRepository.findByYearAndMonth(year, month);

        long totalUsageCount = currentMonthStats.stream()
                .mapToLong(UsageStat::getTotalCnt)
                .sum();

        LocalDate lastMonthDate = LocalDate.of(year, month, 1).minusMonths(1);
        List<UsageStat> lastMonthStats = usageStatRepository.findByYearAndMonth(
                lastMonthDate.getYear(), lastMonthDate.getMonthValue());

        long lastMonthTotal = lastMonthStats.stream()
                .mapToLong(UsageStat::getTotalCnt)
                .sum();

        double increaseRate = 0.0;
        if (lastMonthTotal > 0) {
            increaseRate = ((double) (totalUsageCount - lastMonthTotal) / lastMonthTotal) * 100;
        }

        List<UsageResponse.DailyUsage> dailyUsage = currentMonthStats.stream()
                .map(s -> UsageResponse.DailyUsage.builder()
                        .date(s.getStatDate().getStatDate())
                        .count(s.getTotalCnt())
                        .build())
                .collect(Collectors.toList());

        return UsageResponse.builder()
                .totalUsageCount(totalUsageCount)
                .increaseRate(Math.round(increaseRate * 100) / 100.0)
                .dailyUsage(dailyUsage)
                .build();
    }

    // 기간 내 완료된 분석을 합산해 조회, 기간 미지정 시 최신 분석 1건 사용
    @Transactional(readOnly = true)
    public AnalyzeResultResponse getAnalyzeResult(LocalDate startDate, LocalDate endDate) {
        List<AnalysisJob> jobs;

        if (startDate != null && endDate != null) {
                jobs = analysisJobRepository.findByStatusAndPeriod(
                        AnalysisJob.Status.COMPLETED, startDate, endDate);
                if (jobs.isEmpty()) throw new CustomException(ErrorCode.ANALYSIS_NOT_FOUND);
        } else {
                AnalysisJob latest = analysisJobRepository
                        .findFirstByStatusOrderByFinishedAtDesc(AnalysisJob.Status.COMPLETED)
                        .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_FOUND));
                jobs = List.of(latest);
                startDate = latest.getPeriodStartDate();
                endDate = latest.getPeriodEndDate();
        }

        return buildAnalyzeResultResponse(jobs, startDate, endDate);
    }

    // 분석 ID 단위 결과 조회
    @Transactional(readOnly = true)
    public FileAnalyzeResultResponse getFileAnalyzeResult(Long analysisId) {
        AnalysisJob job = analysisJobRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_FOUND));

        AnalyzeResultResponse commonData = buildAnalyzeResultResponse(
                List.of(job), job.getPeriodStartDate(), job.getPeriodEndDate());

        return FileAnalyzeResultResponse.builder()
                .analysisId(job.getAnalysisId())
                .fileName(job.getFileName())
                .status(job.getStatus().name())
                .periodStartDate(job.getPeriodStartDate() != null ? job.getPeriodStartDate().toString() : null)
                .periodEndDate(job.getPeriodEndDate() != null ? job.getPeriodEndDate().toString() : null)
                .systemStatus(commonData.getSystemStatus())
                .performanceMetrics(commonData.getPerformanceMetrics())
                .trend(commonData.getTrend())
                .burstKeywords(commonData.getBurstKeywords())
                .unanswerAnalysis(commonData.getUnanswerAnalysis())
                .clusteringView(commonData.getClusteringView())
                .faqCandidates(commonData.getFaqCandidates())
                .evaluation(commonData.getEvaluation())
                .surgeDetection(commonData.getSurgeDetection())
                .build();
    }

    // 분석 결과 응답 조립, 조회 API 두 곳에서 공용으로 사용
    private AnalyzeResultResponse buildAnalyzeResultResponse(
        List<AnalysisJob> jobs, LocalDate startDate, LocalDate endDate) {

        // 1. 시스템 상태 - 모든 job의 logs 합산
        List<ClusterLogMap> allLogs = jobs.stream()
                .flatMap(job -> clusterLogMapRepository.findAllByCluster_AnalysisJob(job).stream())
                .collect(Collectors.toList());

        int totalLogCount = allLogs.size();
        int correctCount = (int) allLogs.stream()
                .filter(l -> l.getLogType() == ClusterLogMap.LogType.CORRECT).count();
        int lowQualityCount = (int) allLogs.stream()
                .filter(l -> l.getLogType() == ClusterLogMap.LogType.LOW_QUALITY).count();
        int unansweredCount = (int) allLogs.stream()
                .filter(l -> l.getLogType() == ClusterLogMap.LogType.UNANSWER).count();

        AnalyzeResultResponse.SystemStatus systemStatus = AnalyzeResultResponse.SystemStatus.builder()
                .totalLogCount(totalLogCount)
                .samplingStatus(AnalyzeResultResponse.SystemStatus.SamplingStatus.builder()
                        .correct(correctCount)
                        .lowQuality(lowQualityCount)
                        .unanswer(unansweredCount)
                        .build())
                .build();

        // 2. 성능 지표 - 마지막 job 기준
        AnalysisJob latestJob = jobs.get(jobs.size() - 1);
        Long latestAnalysisId = latestJob.getAnalysisId();

        PerformanceComparison comparison = performanceComparisonRepository
                .findByAnalysisJob_AnalysisId(latestAnalysisId).orElse(null);

        AnalyzeResultResponse.PerformanceMetrics performanceMetrics = null;
        if (comparison != null) {
                performanceMetrics = AnalyzeResultResponse.PerformanceMetrics.builder()
                        .currentUnanswerRate(comparison.getAfterUnanswerRate() != null
                                ? comparison.getAfterUnanswerRate().doubleValue() : 0.0)
                        .predictedAccuracyGain(comparison.getAccuracyGain() != null
                                ? comparison.getAccuracyGain().doubleValue() : 0.0)
                        .resolvedCountByAI(comparison.getResolvedCountByAi() != null
                                ? comparison.getResolvedCountByAi() : 0)
                        .build();
        }

        // 3. 트렌드
        List<UsageStat> usageStats = (startDate != null && endDate != null)
                ? usageStatRepository.findByStatDateBetween(startDate, endDate)
                : List.of();
        List<AnalyzeResultResponse.TrendItem> trend = usageStats.stream()
                .map(s -> AnalyzeResultResponse.TrendItem.builder()
                        .date(s.getStatDate().getStatDate().toString())
                        .unanswerCount(s.getUnanswerCnt())
                        .lowQualityCount(s.getLowQualityCount())
                        .build())
                .collect(Collectors.toList());

        // 4. 급증 키워드 - 모든 job 합산
        List<SurgeDetection> surgeDetections = jobs.stream()
                .flatMap(job -> surgeDetectionRepository
                        .findByAnalysisJobAnalysisId(job.getAnalysisId()).stream())
                .collect(Collectors.toList());

        List<AnalyzeResultResponse.BurstKeyword> burstKeywords = surgeDetections.stream()
                .map(s -> AnalyzeResultResponse.BurstKeyword.builder()
                        .keyword(s.getKeyword())
                        .increasedRate(s.getIncreasedRate() != null
                                ? s.getIncreasedRate().doubleValue() : 0.0)
                        .count(s.getKeywordCount())
                        .build())
                .collect(Collectors.toList());

        // 5. 미답변 사유별 분석 - 모든 job 합산
        List<UnansweredStat> unansweredStats = jobs.stream()
                .flatMap(job -> unansweredStatRepository
                        .findByAnalysisJob_AnalysisId(job.getAnalysisId()).stream())
                .collect(Collectors.toList());

        List<AnalyzeResultResponse.UnansweredAnalysis.ReasonItem> reasonItems = unansweredStats.stream()
                .map(u -> AnalyzeResultResponse.UnansweredAnalysis.ReasonItem.builder()
                        .reason(u.getReason().getDescription())
                        .count(u.getUnanswerCount())
                        .build())
                .collect(Collectors.toList());

        AnalyzeResultResponse.UnansweredAnalysis unansweredAnalysis =
                AnalyzeResultResponse.UnansweredAnalysis.builder()
                        .byReason(reasonItems)
                        .build();

        // 6. 클러스터링 뷰 - 모든 job 합산
        List<Cluster> clusters = jobs.stream()
                .flatMap(job -> clusterRepository
                        .findByAnalysisJobAnalysisId(job.getAnalysisId()).stream())
                .collect(Collectors.toList());

        List<AnalyzeResultResponse.ClusteringView.Point> points = allLogs.stream()
                .map(this::buildPoint)
                .collect(Collectors.toList());

        List<AnalyzeResultResponse.ClusteringView.ClusterName> clusterNames = clusters.stream()
                .map(this::buildClusterName)
                .collect(Collectors.toList());

        AnalyzeResultResponse.ClusteringView clusteringView =
                AnalyzeResultResponse.ClusteringView.builder()
                        .points(points)
                        .clusterNames(clusterNames)
                        .build();

        // 7. FAQ 후보 - 모든 job 합산
        List<FaqCandidate> candidates = jobs.stream()
                .flatMap(job -> faqCandidateRepository
                        .findByAnalysisJobAnalysisId(job.getAnalysisId()).stream())
                .collect(Collectors.toList());

        List<AnalyzeResultResponse.FaqCandidate> faqCandidates = candidates.stream()
                .map(this::buildFaqCandidateItem)
                .collect(Collectors.toList());

        // 8. 평가 지표 - 마지막 job 기준
        AnalyzeResultResponse.Evaluation evaluation = null;
        if (comparison != null) {
                evaluation = AnalyzeResultResponse.Evaluation.builder()
                        .beforeStatDate(comparison.getUsageStat() != null
                                ? comparison.getUsageStat().getStatDate().getStatDate().toString() : null)
                        .afterUnanswerCount(comparison.getAfterUnanswerCnt() != null
                                ? comparison.getAfterUnanswerCnt() : 0)
                        .afterUnanswerRate(comparison.getAfterUnanswerRate() != null
                                ? comparison.getAfterUnanswerRate().doubleValue() : 0.0)
                        .accuracyGain(comparison.getAccuracyGain() != null
                                ? comparison.getAccuracyGain().doubleValue() : 0.0)
                        .resolvedCountByAI(comparison.getResolvedCountByAi() != null
                                ? comparison.getResolvedCountByAi() : 0)
                        .falsePositiveRate(comparison.getFalsePositiveRate() != null
                                ? comparison.getFalsePositiveRate().doubleValue() : 0.0)
                        .evalThreshold(comparison.getEvalThreshold() != null
                                ? comparison.getEvalThreshold().doubleValue() : 0.0)
                        .build();
        }

        // 9. 급증 탐지 상세
        List<AnalyzeResultResponse.SurgeDetection> surgeDetectionItems = surgeDetections.stream()
                .map(s -> {
                        List<SurgeStat> surgeStats = surgeStatRepository
                                .findBySurgeDetectionSurgeId(s.getSurgeId());

                        List<AnalyzeResultResponse.SurgeDetection.PeriodStat> periodStats = surgeStats.stream()
                                .map(ss -> AnalyzeResultResponse.SurgeDetection.PeriodStat.builder()
                                        .statDate(ss.getStatDate().getStatDate().toString())
                                        .periodType(ss.getPeriodType().name())
                                        .count(ss.getCount())
                                        .build())
                                .collect(Collectors.toList());

                        return AnalyzeResultResponse.SurgeDetection.builder()
                                .keyword(s.getKeyword())
                                .isNew(s.getIsNew())
                                .relatedClusterLabel(s.getRelatedCluster() != null
                                        ? s.getRelatedCluster().getClusterLabel() : -1)
                                .periodStats(periodStats)
                                .build();
                })
                .collect(Collectors.toList());

        return AnalyzeResultResponse.builder()
                .periodStartDate(startDate != null ? startDate.toString() : null)
                .periodEndDate(endDate != null ? endDate.toString() : null)
                .systemStatus(systemStatus)
                .performanceMetrics(performanceMetrics)
                .trend(trend)
                .burstKeywords(burstKeywords)
                .unanswerAnalysis(unansweredAnalysis)
                .clusteringView(clusteringView)
                .faqCandidates(faqCandidates)
                .evaluation(evaluation)
                .surgeDetection(surgeDetectionItems)
                .build();
        }

    // 파일 업로드 후 AI 분석 파이프라인 시작, 동일 해시 파일은 기존 결과 반환
    @Transactional
    public NewAnalyzeResponse uploadAndStartAnalysis(
            MultipartFile file,
            String email,
            String periodStartDate,
            String periodEndDate,
            boolean isMaskingEnabled,
            boolean isTranslationEnabled) {
    
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    
        String fileHash;
        try {
            fileHash = DigestUtils.sha256Hex(file.getInputStream());
        } catch (IOException e) {
            throw new CustomException(ErrorCode.FILE_READ_ERROR);
        }
    
        LocalDate parsedStartDate = periodStartDate != null ? LocalDate.parse(periodStartDate) : null;
        LocalDate parsedEndDate = periodEndDate != null ? LocalDate.parse(periodEndDate) : null;
    
        Optional<UploadFile> existingUpload = uploadFileRepository.findByFileHash(fileHash);
        if (existingUpload.isPresent()) {
                Optional<AnalysisJob> existingJob = analysisJobRepository
                        .findFirstByUploadFileOrderByCreatedAtDesc(existingUpload.get());
                if (existingJob.isPresent()) {
                AnalysisJob job = existingJob.get();
                log.info("중복 파일 감지 - 기존 분석 결과 반환: analysisId={}", job.getAnalysisId());
                return NewAnalyzeResponse.builder()
                        .analysisId(job.getAnalysisId())
                        .uploadId(existingUpload.get().getUploadId())
                        .fileName(job.getFileName())
                        .status(job.getStatus().name())
                        .message("동일한 파일의 분석 결과가 이미 존재합니다.")
                        .submittedAt(job.getCreatedAt())
                        .build();
                }
        }

        String uploadId = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String uniqueFileName = uploadId + "_" + originalFileName;
    
        UploadFile uploadFile = UploadFile.builder()
                .uploadId(uploadId)
                .uploadedBy(user)
                .originalFileName(originalFileName)
                .fileName(uniqueFileName)
                .filePath("")
                .fileSize(file.getSize())
                .fileHash(fileHash)
                .build();
        uploadFileRepository.save(uploadFile);
    
        AnalysisJob analysisJob = AnalysisJob.builder()
                .uploadFile(uploadFile)
                .fileName(originalFileName)
                .isMaskingEnabled(isMaskingEnabled)
                .isTranslationEnabled(isTranslationEnabled)
                .periodStartDate(parsedStartDate)
                .periodEndDate(parsedEndDate)
                .build();
        analysisJobRepository.save(analysisJob);
    
        webClientUtil.sendPipeline(
                uploadId,
                analysisJob.getAnalysisId(),
                file,
                isMaskingEnabled,
                isTranslationEnabled,
                periodStartDate != null ? periodStartDate : "",
                periodEndDate != null ? periodEndDate : "");
    
        log.info("파일 업로드 및 분석 시작: analysisId={}, uploadId={}", analysisJob.getAnalysisId(), uploadId);
    
        return NewAnalyzeResponse.builder()
                .analysisId(analysisJob.getAnalysisId())
                .uploadId(uploadId)
                .fileName(originalFileName)
                .status(analysisJob.getStatus().name())
                .message("파일 업로드 및 AI 분석 파이프라인을 시작합니다.")
                .submittedAt(analysisJob.getCreatedAt())
                .build();
    }

    // 분석 진행 상태 조회, SSE 끊김 시 폴링 용도
    @Transactional(readOnly = true)
    public AnalyzeStatusResponse getAnalysisStatus(Long analysisId) {
        AnalysisJob analysisJob = analysisJobRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_FOUND));

        return AnalyzeStatusResponse.builder()
                .uploadId(analysisJob.getUploadFile().getUploadId())
                .analysisId(analysisJob.getAnalysisId())
                .status(analysisJob.getStatus().name())
                .message(getStatusMessage(analysisJob.getStatus()))
                .updatedAt(analysisJob.getStartedAt() != null
                        ? analysisJob.getStartedAt()
                        : analysisJob.getCreatedAt())
                .build();
    }

    // 분석 진행 상태 실시간 구독(SSE)
    // 연결 즉시 현재 상태를 보내 클라이언트가 연결 여부와 진행 상황을 바로 알 수 있게 한다
    // 이미 끝난 분석이면 현재 상태를 보낸 뒤 연결이 닫힌다
    public SseEmitter subscribeAnalysisStatus(Long analysisId) {
        if (!analysisJobRepository.existsById(analysisId)) {
            throw new CustomException(ErrorCode.ANALYSIS_NOT_FOUND);
        }

        // 구독을 먼저 등록한 뒤 상태를 읽어야 그 사이에 도착한 콜백을 놓치지 않는다
        SseEmitter emitter = sseEmitterManager.create(analysisId);
        AnalysisJob.Status status = analysisJobRepository.findById(analysisId)
                .map(AnalysisJob::getStatus)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_FOUND));
        sseEmitterManager.send(emitter, analysisId, status.name(), getStatusMessage(status));
        return emitter;
    }

    // 분석 이력 목록 조회, 최신순 페이징
    @Transactional(readOnly = true)
    public AnalyzeHistoryResponse getAnalysisHistory(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<AnalysisJob> jobPage = analysisJobRepository.findAllByOrderByCreatedAtDesc(pageRequest);

        List<AnalyzeHistoryResponse.HistoryItem> history = jobPage.getContent().stream()
                .map(job -> AnalyzeHistoryResponse.HistoryItem.builder()
                        .analysisId(job.getAnalysisId())
                        .fileName(job.getFileName())
                        .status(job.getStatus().name())
                        .period(job.getPeriodStartDate() + " ~ " + job.getPeriodEndDate())
                        .submittedAt(job.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AnalyzeHistoryResponse.builder()
                .history(history)
                .totalCount(jobPage.getTotalElements())
                .totalPages(jobPage.getTotalPages())
                .currentPage(page)
                .build();
    }

    // AI 서버 콜백 수신, 진행 상태 갱신 또는 최종 분석 결과 적재
    // 중간 상태(PREPROCESSING, ANALYZING)와 실패는 상태만 갱신하고 종료한다
    @Transactional
    public void handleAnalysisCallback(Long analysisId, AnalysisCallbackRequest request) {
        AnalysisJob analysisJob = analysisJobRepository.findById(analysisId)
                .orElseThrow(() -> new CustomException(ErrorCode.ANALYSIS_NOT_FOUND));

        // 중간 상태 콜백 (PREPROCESSING, ANALYZING)
        if ("PREPROCESSING".equals(request.getStatus())) {
            analysisJob.startPreprocessing();
            analysisJobRepository.save(analysisJob);
            sseEmitterManager.send(analysisId, "PREPROCESSING", "데이터 전처리 중입니다.");
            return;
        }

        if ("ANALYZING".equals(request.getStatus())) {
            analysisJob.startAnalyzing();
            analysisJobRepository.save(analysisJob);
            sseEmitterManager.send(analysisId, "ANALYZING", "AI 클러스터링 진행 중입니다."); 
            return;
        }

        // AI 서버는 실패 시 "FAILED"를 보내고 내부 상태값은 "FAIL"이므로 두 값을 모두 수용한다
        if ("FAIL".equals(request.getStatus()) || "FAILED".equals(request.getStatus())) {
            analysisJob.fail(request.getErrorMessage());
            analysisJobRepository.save(analysisJob);
            sseEmitterManager.send(analysisId, "FAIL", "분석에 실패했습니다.");
            log.error("분석 실패 콜백 수신: analysisId={}, error={}", analysisId, request.getErrorMessage());
            return;
        }

        // COMPLETED: 전체 데이터 적재
        // 1. 사용량 통계 적재
        if (request.getTrend() != null) {
                for (AnalysisCallbackRequest.TrendItemResult trendItem : request.getTrend()) {
                    LocalDate statDate = parseTrendDate(trendItem.getDate(), analysisJob);
            
                    StatDate statDateEntity = statDateRepository.findById(statDate)
                            .orElseGet(() -> statDateRepository.save(
                                    StatDate.builder().statDate(statDate).build()));
            
                    UsageStat usageStatEntity = usageStatRepository
                            .findByStatDate_StatDate(statDate)
                            .orElse(UsageStat.builder()
                                    .statDate(statDateEntity)
                                    .totalCnt(0)
                                    .answerCnt(0)
                                    .lowQualityCount(0)
                                    .unanswerCnt(0)
                                    .build());
            
                    // total_cnt와 answer_cnt는 bronze_chatting_log_day 집계가 필요하나
                    // 현재 해당 데이터가 적재되지 않아 0으로 둔다 (사용량 통계 기능 보류 중)
                    usageStatEntity.update(
                            0,
                            0,
                            trendItem.getLowQualityCount() != null ? trendItem.getLowQualityCount() : 0,
                            trendItem.getUnanswerCount() != null ? trendItem.getUnanswerCount() : 0);
                    usageStatRepository.save(usageStatEntity);
                }
            }

        // 2. 클러스터링 결과 적재
        if (request.getClusteringView() != null && request.getClusteringView().getClusterNames() != null) {
            for (AnalysisCallbackRequest.ClusteringViewResult.ClusterNameResult clusterResult
                    : request.getClusteringView().getClusterNames()) {
                Cluster cluster = Cluster.builder()
                        .analysisJob(analysisJob)
                        .clusterLabel(clusterResult.getClusterLabel())
                        .clusterName(clusterResult.getName())
                        .topKeywords(clusterResult.getTopKeywords())
                        .clusterSize(clusterResult.getSize())
                        .build();
                clusterRepository.save(cluster);
            }

            // points → ClusterLogMap 적재
            if (request.getClusteringView().getPoints() != null) {
                for (AnalysisCallbackRequest.ClusteringViewResult.PointResult point
                        : request.getClusteringView().getPoints()) {
                    Cluster cluster = clusterRepository
                            .findByAnalysisJobAndClusterLabel(analysisJob, point.getClusterLabel())
                            .orElse(null);

                    // cluster_id는 NOT NULL이므로 매칭 실패 시 적재하면 제약 위반으로 전체가 롤백된다.
                    // 해당 포인트만 건너뛰고 나머지 적재는 계속 진행한다
                    if (cluster == null) {
                        log.warn("클러스터 미매칭 포인트 제외: analysisId={}, clusterLabel={}",
                                analysisId, point.getClusterLabel());
                        continue;
                    }

                    ClusterLogMap clusterLogMap = ClusterLogMap.builder()
                            .cluster(cluster)
                            .sourceLogSeqNum(point.getSourceLogSeqNum())
                            .logType(ClusterLogMap.LogType.valueOf(point.getLogType()))
                            .umapX(point.getX() != null ? point.getX().floatValue() : null)
                            .umapY(point.getY() != null ? point.getY().floatValue() : null)
                            .logText(point.getText())
                            .build();
                    clusterLogMapRepository.save(clusterLogMap);
                }
            }
        }

        // 3. FAQ 후보 적재
        if (request.getFaqCandidates() != null) {
            for (AnalysisCallbackRequest.FaqCandidateResult candidateResult : request.getFaqCandidates()) {
                Cluster relatedCluster = null;
                if (candidateResult.getClusterLabel() != null) {
                    relatedCluster = clusterRepository
                            .findByAnalysisJobAndClusterLabel(analysisJob, candidateResult.getClusterLabel())
                            .orElse(null);
                }

                FaqCandidate candidate = FaqCandidate.builder()
                        .analysisJob(analysisJob)
                        .cluster(relatedCluster)
                        .candidateType(FaqCandidate.CandidateType.valueOf(candidateResult.getCandidateType()))
                        .standardQuestion(candidateResult.getStandardQuestion())
                        .answerDraft(candidateResult.getAnswerDraft())
                        .qType(candidateResult.getQType())
                        .category(candidateResult.getCategory())
                        .similarQuestions(candidateResult.getSimilarQuestions())
                        .occurrenceCount(candidateResult.getOccurrenceCount())
                        .representativeKeywords(candidateResult.getRepresentativeKeywords())
                        .build();
                faqCandidateRepository.save(candidate);

                if (candidateResult.getSynonyms() != null) {
                        for (AnalysisCallbackRequest.SynonymResult synonymResult : candidateResult.getSynonyms()) {
                            if (synonymCandidateRepository.existsByCandidateCandidateIdAndSynonymText(
                                    candidate.getCandidateId(), synonymResult.getText())) {
                                continue;
                            }
                            SynonymCandidate synonym = SynonymCandidate.builder()
                                    .candidate(candidate)
                                    .synonymText(synonymResult.getText())
                                    .synonymType(synonymResult.getType() != null
                                            ? SynonymCandidate.SynonymType.valueOf(synonymResult.getType())
                                            : null)
                                    .build();
                            synonymCandidateRepository.save(synonym);
                        }
                    }

                if (candidateResult.getMatchedFaqs() != null) {
                    for (AnalysisCallbackRequest.MatchedFaqResult matchedFaq : candidateResult.getMatchedFaqs()) {
                        FaqCandidateMatch match = FaqCandidateMatch.builder()
                                .candidate(candidate)
                                .matchedFaqSeqNum(matchedFaq.getMatchedFaqSeqNum())
                                .matchScore(BigDecimal.valueOf(matchedFaq.getMatchScore()))
                                .build();
                        faqCandidateMatchRepository.save(match);
                    }
                }
            }
        }

        // 4. 미답변 사유별 통계 적재
        if (request.getUnanswerAnalysis() != null && request.getUnanswerAnalysis().getByReason() != null) {
            for (AnalysisCallbackRequest.UnanswerAnalysisResult.ReasonItemResult item
                    : request.getUnanswerAnalysis().getByReason()) {
                UnansweredReason reason = UnansweredReason.fromDescription(item.getReason());

                UnansweredStat stat = UnansweredStat.builder()
                        .analysisJob(analysisJob)
                        .reason(reason)
                        .unanswerCount(item.getCount())
                        .build();
                unansweredStatRepository.save(stat);
            }
        }

        // 5. 급증 탐지 적재
        if (request.getSurgeDetection() != null) {
            for (AnalysisCallbackRequest.SurgeDetectionResult surgeResult : request.getSurgeDetection()) {
                Cluster relatedCluster = null;
                if (surgeResult.getRelatedClusterLabel() != null) {
                    relatedCluster = clusterRepository
                            .findByAnalysisJobAndClusterLabel(analysisJob, surgeResult.getRelatedClusterLabel())
                            .orElse(null);
                }

                SurgeDetection surgeDetection = SurgeDetection.builder()
                        .analysisJob(analysisJob)
                        .keyword(surgeResult.getKeyword())
                        .isNew(surgeResult.getIsNew())
                        .increasedRate(surgeResult.getIncreasedRate() != null
                                ? BigDecimal.valueOf(surgeResult.getIncreasedRate()) : null)
                        .keywordCount(surgeResult.getKeywordCount())
                        .relatedCluster(relatedCluster)
                        .build();
                surgeDetectionRepository.save(surgeDetection);

                if (surgeResult.getPeriodStats() != null) {
                    for (AnalysisCallbackRequest.SurgeDetectionResult.SurgeStatResult surgeStatResult
                            : surgeResult.getPeriodStats()) {
                        LocalDate statDate = LocalDate.parse(surgeStatResult.getStatDate());

                        StatDate statDateEntity = statDateRepository.findById(statDate)
                                .orElseGet(() -> statDateRepository.save(
                                        StatDate.builder().statDate(statDate).build()));

                        SurgeStat surgeStat = SurgeStat.builder()
                                .surgeDetection(surgeDetection)
                                .statDate(statDateEntity)
                                .periodType(SurgeStat.PeriodType.valueOf(surgeStatResult.getPeriodType()))
                                .count(surgeStatResult.getCount())
                                .build();
                        surgeStatRepository.save(surgeStat);
                    }
                }
            }
        }

        // 6. 성능 비교 적재
        if (request.getEvaluation() != null) {
                AnalysisCallbackRequest.EvaluationResult eval = request.getEvaluation();
                LocalDate beforeStatDate = LocalDate.parse(eval.getBeforeStatDate());
            
                UsageStat beforeUsageStat = usageStatRepository
                        .findByStatDate_StatDate(beforeStatDate)
                        .orElse(null);
            
                if (beforeUsageStat == null) {
                    log.warn("beforeStatDate에 해당하는 사용량 통계 없음: {}", beforeStatDate);
                } else {
                    PerformanceComparison comparison = PerformanceComparison.builder()
                            .analysisJob(analysisJob)
                            .usageStat(beforeUsageStat)
                            .afterUnanswerCnt(eval.getAfterUnanswerCount())
                            .afterUnanswerRate(eval.getAfterUnanswerRate() != null
                                    ? BigDecimal.valueOf(eval.getAfterUnanswerRate()) : null)
                            .accuracyGain(eval.getAccuracyGain() != null
                                    ? BigDecimal.valueOf(eval.getAccuracyGain()) : null)
                            .falsePositiveRate(eval.getFalsePositiveRate() != null
                                    ? BigDecimal.valueOf(eval.getFalsePositiveRate()) : null)
                            .evalThreshold(eval.getEvalThreshold() != null
                                    ? BigDecimal.valueOf(eval.getEvalThreshold()) : null)
                            .resolvedCountByAi(eval.getResolvedCountByAI())
                            .build();
                    performanceComparisonRepository.save(comparison);
                }
            }
            
            // 7. 완료 처리
            analysisJob.finish();
            analysisJobRepository.save(analysisJob);
            sseEmitterManager.send(analysisId, "COMPLETED", "분석이 완료되었습니다.");
            log.info("분석 완료 콜백 처리 완료: analysisId={}", analysisId);
    }

    // FAQ 후보를 응답 DTO로 변환, 유사어와 매칭 정보 포함
    private AnalyzeResultResponse.FaqCandidate buildFaqCandidateItem(FaqCandidate candidate) {
        List<AnalyzeResultResponse.FaqCandidate.SynonymItem> synonyms =
                synonymCandidateRepository
                        .findByCandidateCandidateId(candidate.getCandidateId())
                        .stream()
                        .map(s -> AnalyzeResultResponse.FaqCandidate.SynonymItem.builder()
                                .text(s.getSynonymText())
                                .type(s.getSynonymType() != null ? s.getSynonymType().name() : null)
                                .build())
                        .collect(Collectors.toList());
    
        List<AnalyzeResultResponse.FaqCandidate.MatchedFaq> matchedFaqs =
                faqCandidateMatchRepository
                        .findByCandidateCandidateIdOrderByMatchScoreDesc(candidate.getCandidateId())
                        .stream()
                        .map(m -> AnalyzeResultResponse.FaqCandidate.MatchedFaq.builder()
                                .matchedFaqSeqNum(m.getMatchedFaqSeqNum())
                                .matchScore(m.getMatchScore().doubleValue())
                                .build())
                        .collect(Collectors.toList());
    
        return AnalyzeResultResponse.FaqCandidate.builder()
                .candidateId(candidate.getCandidateId())
                .clusterLabel(candidate.getCluster() != null
                        ? candidate.getCluster().getClusterLabel() : -1)
                .candidateType(candidate.getCandidateType().name())
                .qType(candidate.getQType())
                .category(candidate.getCategory())
                .standardQuestion(candidate.getStandardQuestion())
                .similarQuestions(candidate.getSimilarQuestions())
                
                .representativeKeywords(candidate.getRepresentativeKeywords()) 
                
                .answerDraft(candidate.getAnswerDraft())
                .synonyms(synonyms)
                .occurrenceCount(candidate.getOccurrenceCount() != null
                        ? candidate.getOccurrenceCount() : 0)
                .matchedFaqs(matchedFaqs)
                .build();
    }

    // 클러스터 로그를 산점도 좌표로 변환
    private AnalyzeResultResponse.ClusteringView.Point buildPoint(ClusterLogMap l) {
        return AnalyzeResultResponse.ClusteringView.Point.builder()
                .x(l.getUmapX() != null ? l.getUmapX() : 0.0)
                .y(l.getUmapY() != null ? l.getUmapY() : 0.0)
                .clusterLabel(l.getCluster() != null ? l.getCluster().getClusterLabel() : -1)
                .text(l.getLogText())
                .sourceLogSeqNum(l.getSourceLogSeqNum())
                .logType(l.getLogType().name())
                .build();
    }

    // 클러스터 메타 정보를 응답 DTO로 변환
    private AnalyzeResultResponse.ClusteringView.ClusterName buildClusterName(Cluster c) {
        return AnalyzeResultResponse.ClusteringView.ClusterName.builder()
                .clusterId(c.getClusterId())
                .clusterLabel(c.getClusterLabel())
                .name(c.getClusterName())
                .topKeywords(c.getTopKeywords()) 
                .size(c.getClusterSize() != null ? c.getClusterSize() : 0)
                .build();
    }

    // 상태별 사용자 안내 문구 반환
    private String getStatusMessage(AnalysisJob.Status status) {
        return switch (status) {
            case PENDING -> "분석 대기 중입니다.";
            case PREPROCESSING -> "데이터 전처리 중입니다.";
            case ANALYZING -> "현재 AI 클러스터링을 진행 중입니다. 잠시만 기다려 주세요.";
            case COMPLETED -> "분석이 완료되었습니다.";
            case FAIL -> "분석에 실패했습니다.";
        };
    }

    // 콜백 트렌드 날짜 파싱
    // "MM-DD" 형식으로 연도가 없으면 분석 대상 기간의 연도를 사용한다.
    // 현재 연도를 쓰면 과거 기간을 분석할 때 통계가 엉뚱한 날짜에 적재된다
    private LocalDate parseTrendDate(String rawDate, AnalysisJob analysisJob) {
        String[] parts = rawDate.split("-");

        if (parts.length == 3) {
            return LocalDate.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }

        int year = analysisJob.getPeriodStartDate() != null
                ? analysisJob.getPeriodStartDate().getYear()
                : LocalDate.now().getYear();

        return LocalDate.of(year, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
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
