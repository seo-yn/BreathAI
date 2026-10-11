import { useState, useEffect, useCallback } from "react";
import { faqApi } from "../api/faq.js";
import { dashApi } from "../api/dashboard.js";
import MatchedFaqBadge from "../components/MatchedFaqBadge.jsx";
import { isAccepted } from "../utils/reviewStatus.js";
import { useAuth } from "../hooks/useAuth.js";
import { useToast } from "../hooks/useToast.js";

const PAGE_SIZE = 10;

// 백엔드 FaqEditHistory.EditType
const EDIT_TYPES = [
  { value: "",       label: "전체" },
  { value: "CREATE", label: "신규 생성" },
  { value: "EXPAND", label: "기존 확장" },
  { value: "MANUAL", label: "수동 수정" },
  { value: "DELETE", label: "삭제" },
];

const EDIT_TYPE_STYLE = {
  CREATE: { background: "#DBEAFE", color: "#1E40AF", border: "#BFDBFE" },
  EXPAND: { background: "#EDE9FE", color: "#5B21B6", border: "#DDD6FE" },
  MANUAL: { background: "#FEF3C7", color: "#92400E", border: "#FDE68A" },
  DELETE: { background: "#FDECEA", color: "#B91C1C", border: "#FECACA" },
};

function EditTypeTag({ editType }) {
  if (!editType) return null;
  const s = EDIT_TYPE_STYLE[editType] || EDIT_TYPE_STYLE.MANUAL;
  const label = EDIT_TYPES.find((t) => t.value === editType)?.label || editType;
  return (
    <span style={{
      fontSize: 10, fontWeight: 600, padding: "2px 8px", borderRadius: 10,
      background: s.background, color: s.color, border: `1px solid ${s.border}`,
    }}>{label}</span>
  );
}

export default function FaqPage() {
  const [tab, setTab]               = useState("list");

  // FAQ 리스트
  const [faqs, setFaqs]             = useState([]);
  const [page, setPage]             = useState(0);
  const [pageInfo, setPageInfo]     = useState({ totalCount: 0, totalPages: 0 });
  const [keywordInput, setKeywordInput]   = useState("");
  const [categoryInput, setCategoryInput] = useState("");
  const [query, setQuery]           = useState({ keyword: "", category: "" });

  // FAQ 후보 추천
  const [candidates, setCandidates] = useState([]);
  const [reviewing, setReviewing]   = useState(null);
  const [latestAnalysisId, setLatestAnalysisId] = useState(null);

  // 변경 이력 (FAQ 단건이 아니라 전역 이력 — 서버가 faqId 필터를 받지 않는다)
  const [histories, setHistories]   = useState([]);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyInfo, setHistoryInfo] = useState({ totalCount: 0, totalPages: 0 });
  const [editType, setEditType]     = useState("");
  const [historyLoaded, setHistoryLoaded] = useState(false);

  const [loading, setLoading]       = useState(true);
  const [modal, setModal]           = useState(null);
  const { user } = useAuth();
  const toast = useToast();

  // 수정·삭제·반영은 백엔드가 @PreAuthorize("hasRole('ADMIN')")로 막는다.
  // USER 계정에 버튼을 보여주면 누를 때마다 403이 나므로 아예 감춘다.
  const isAdmin = user?.role === "ADMIN";

  const loadFaqs = useCallback(async (targetPage, q) => {
    const res = await faqApi.getList({
      page: targetPage,
      size: PAGE_SIZE,
      keyword: q.keyword,
      category: q.category,
    });
    setFaqs(res.data?.faqList || []);
    setPageInfo({
      totalCount: res.data?.totalCount ?? 0,
      totalPages: res.data?.totalPages ?? 0,
    });
  }, []);

  const loadHistories = useCallback(async (targetPage, type) => {
    const res = await faqApi.getHistory({ page: targetPage, size: PAGE_SIZE, editType: type });
    setHistories(res.data?.histories || []);
    setHistoryInfo({
      totalCount: res.data?.totalCount ?? 0,
      totalPages: res.data?.totalPages ?? 0,
    });
  }, []);

  useEffect(() => {
    dashApi.getAnalysisHistory(0, 1).then((histRes) => {
      const latestId = histRes.data?.history?.[0]?.analysisId || null;
      setLatestAnalysisId(latestId);

      const requests = [loadFaqs(0, { keyword: "", category: "" })];
      if (latestId) requests.push(faqApi.getCandidates(latestId));

      Promise.all(requests).then(([, candRes]) => {
        setCandidates(candRes?.data?.recommendations || []);
      }).catch((err) => toast.error(err.message || "FAQ 목록을 불러오지 못했습니다."))
        .finally(() => setLoading(false));
    }).catch((err) => {
      toast.error(err.message || "분석 이력을 불러오지 못했습니다.");
      setLoading(false);
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const runSearch = async (targetPage, q) => {
    try {
      await loadFaqs(targetPage, q);
      setPage(targetPage);
      setQuery(q);
    } catch (err) {
      toast.error(err.message || "FAQ 목록을 불러오지 못했습니다.");
    }
  };

  const runHistory = async (targetPage, type) => {
    try {
      await loadHistories(targetPage, type);
      setHistoryPage(targetPage);
      setEditType(type);
      setHistoryLoaded(true);
    } catch (err) {
      toast.error(err.message || "변경 이력을 불러오지 못했습니다.");
    }
  };

  const openHistoryTab = () => {
    setTab("history");
    if (!historyLoaded) runHistory(0, "");
  };

  const handleReview = async (candidateId, action) => {
    const c = candidates.find((x) => x.candidateId === candidateId);
    if (!c) return;

    if (action === "ACCEPT") {
      if (!c.standardQuestion || !c.answerDraft) {
        toast.error("표준 질문 또는 답변 초안이 비어 있는 후보는 승인할 수 없습니다.");
        return;
      }
      // 백엔드 FaqApplyRequest.keywords가 @NotEmpty라 빈 배열은 400이 된다
      if (!c.representativeKeywords?.length) {
        toast.error("대표 키워드가 없는 후보는 승인할 수 없습니다.");
        return;
      }
      setReviewing(candidateId);
      try {
        const res = await faqApi.apply({
          analysisId: latestAnalysisId,
          clusterId: c.clusterId,
          clusterLabel: c.clusterLabel,
          candidateId: c.candidateId,
          qType: c.qType,
          finalQuestion: c.standardQuestion,
          finalAnswer: c.answerDraft,
          keywords: c.representativeKeywords,
          isManualPatchConfirmed: false,
        });
        // 백엔드가 EXPAND 후보면 기존 FAQ를 확장하고 editType으로 알려준다
        toast.success(res.data?.editType === "EXPAND"
          ? "기존 FAQ를 확장했습니다."
          : "신규 FAQ로 반영되었습니다.");
        await runSearch(0, query);
        if (historyLoaded) runHistory(0, editType);
      } catch (err) {
        toast.error(err.message || "FAQ 반영에 실패했습니다.");
        return;
      } finally {
        setReviewing(null);
      }
    }

    setCandidates((prev) =>
      prev.map((x) =>
        x.candidateId === candidateId
          ? { ...x, reviewStatus: action === "ACCEPT" ? "APPLIED" : "REJECTED" }
          : x
      )
    );
  };

  const handleUpdate = async (faq, editReason) => {
    try {
      await faqApi.update(faq.faqId, {
        standardQuestion: faq.standardQuestion,
        answer: faq.answer,
        keywords: faq.keywords,
        qType: faq.qType,
        editReason: editReason || undefined,
      });
      toast.success("FAQ가 수정되었습니다.");
      setModal(null);
      await runSearch(page, query);
      if (historyLoaded) runHistory(0, editType);
    } catch (err) {
      toast.error(err.message || "FAQ 수정에 실패했습니다.");
    }
  };

  const handleDelete = async (faqId) => {
    if (!confirm("이 FAQ를 삭제하시겠습니까?")) return;
    try {
      await faqApi.remove(faqId);
      toast.success("FAQ가 삭제되었습니다.");
      // 마지막 페이지의 마지막 항목을 지웠으면 빈 페이지가 되니 한 칸 앞으로
      const target = faqs.length === 1 && page > 0 ? page - 1 : page;
      await runSearch(target, query);
      if (historyLoaded) runHistory(0, editType);
    } catch (err) {
      toast.error(err.message || "FAQ 삭제에 실패했습니다.");
    }
  };

  const pendingCount = candidates.filter((c) => c.reviewStatus === "PENDING").length;

  if (loading) return <div style={{ padding: 40, color: "#6B7280" }}>불러오는 중...</div>;

  return (
    <div style={S.page}>
      <div style={S.pageTitle}>FAQ 후보 관리</div>
      <div style={S.pageSub}>현재 등록된 FAQ를 확인하고 AI 추천 후보를 검토합니다.</div>

      <div style={S.tabs}>
        {[
          { key: "list",      label: `FAQ 리스트 (${pageInfo.totalCount})` },
          { key: "recommend", label: `FAQ 후보 추천${pendingCount > 0 ? ` (검토 필요 ${pendingCount})` : ""}` },
          { key: "history",   label: "변경 이력" },
        ].map((t) => (
          <div key={t.key}
            style={{ ...S.tab, ...(tab === t.key ? S.tabActive : {}) }}
            onClick={() => (t.key === "history" ? openHistoryTab() : setTab(t.key))}>
            {t.label}
          </div>
        ))}
      </div>

      {/* ── FAQ 리스트 ── */}
      {tab === "list" && (
        <div>
          <div style={{ marginBottom: 14, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <input style={S.searchInput} placeholder="질문·답변·키워드 검색"
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") runSearch(0, { keyword: keywordInput.trim(), category: categoryInput.trim() }); }} />
            <input style={{ ...S.searchInput, width: 160 }} placeholder="카테고리명"
              value={categoryInput}
              onChange={(e) => setCategoryInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") runSearch(0, { keyword: keywordInput.trim(), category: categoryInput.trim() }); }} />
            <button style={S.btnSearch}
              onClick={() => runSearch(0, { keyword: keywordInput.trim(), category: categoryInput.trim() })}>
              검색
            </button>
            {(query.keyword || query.category) && (
              <button style={S.btnSm}
                onClick={() => { setKeywordInput(""); setCategoryInput(""); runSearch(0, { keyword: "", category: "" }); }}>
                초기화
              </button>
            )}
            <span style={{ fontSize: 12, color: "#6B7280" }}>
              총 {pageInfo.totalCount.toLocaleString()}개
              {pageInfo.totalPages > 1 && ` · ${page + 1}/${pageInfo.totalPages} 페이지`}
            </span>
          </div>

          {faqs.length === 0 ? (
            <div style={S.empty}>
              <div style={{ fontSize: 32, marginBottom: 8 }}>🔍</div>
              <div>{query.keyword || query.category ? "검색 결과가 없습니다." : "등록된 FAQ가 없습니다."}</div>
            </div>
          ) : (
            faqs.map((f) => (
              <div key={f.faqId} style={S.faqCard}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 4 }}>
                      {f.category && <span style={S.categoryTag}>{f.category}</span>}
                      <span style={{ fontSize: 11, fontFamily: "monospace", color: "#C4C8CE" }}>#{f.faqId}</span>
                    </div>
                    <div style={S.faqQ}>{f.standardQuestion}</div>
                    <div style={S.faqA}>{f.answer}</div>
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 5, marginTop: 8 }}>
                      {f.keywords?.map((k) => <span key={k} style={S.tag}>{k}</span>)}
                    </div>
                  </div>
                  <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 8, marginLeft: 16 }}>
                    {f.createdAt && <span style={S.meta}>{f.createdAt} 등록</span>}
                    {isAdmin && (
                      <div style={{ display: "flex", gap: 6 }}>
                        <button style={S.btnSm} onClick={() => setModal({ type: "edit", data: f })}>수정</button>
                        <button style={S.btnSmDanger} onClick={() => handleDelete(f.faqId)}>삭제</button>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))
          )}

          {pageInfo.totalPages > 1 && (
            <div style={S.pager}>
              <button style={S.btnSm} disabled={page === 0} onClick={() => runSearch(page - 1, query)}>← 이전</button>
              <span style={{ fontSize: 12, color: "#6B7280" }}>{page + 1} / {pageInfo.totalPages}</span>
              <button style={S.btnSm} disabled={page >= pageInfo.totalPages - 1} onClick={() => runSearch(page + 1, query)}>다음 →</button>
            </div>
          )}
        </div>
      )}

      {/* ── FAQ 후보 추천 ── */}
      {tab === "recommend" && (
        <div>
          <div style={S.infoBanner}>
            🤖 LLM API가 미답변 로그를 분석하여 신규 FAQ 후보를 추천합니다. 승인하면 FAQ DB에 자동 반영됩니다.
            {!isAdmin && " (반영은 관리자 권한이 필요합니다)"}
          </div>

          {candidates.length === 0 ? (
            <div style={S.empty}><div style={{ fontSize: 32, marginBottom: 8 }}>✨</div><div>추천 후보가 없습니다.</div></div>
          ) : (
            candidates.map((c) => (
              <div key={c.candidateId} style={{
                ...S.candCard,
                borderLeft: `4px solid ${isAccepted(c.reviewStatus) ? "#43A047" : c.reviewStatus === "REJECTED" ? "#E53935" : "#1565C0"}`,
              }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 10 }}>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 4, flexWrap: "wrap" }}>
                      <span style={{
                        fontSize: 10, fontWeight: 600, padding: "2px 8px", borderRadius: 10,
                        background: c.candidateType === "NEW" ? "#DBEAFE" : "#EDE9FE",
                        color: c.candidateType === "NEW" ? "#1E40AF" : "#5B21B6",
                      }}>
                        {c.candidateType === "NEW" ? "신규 FAQ" : "키워드 확장"}
                      </span>
                      {c.category && <span style={S.categoryTag}>{c.category}</span>}
                      <span style={{ fontSize: 11, color: "#9CA3AF" }}>매칭 {c.occurrenceCount}건</span>
                      <MatchedFaqBadge matches={c.matchedFaqs} candidateType={c.candidateType} />
                    </div>
                    <div style={S.faqQ}>{c.standardQuestion}</div>
                  </div>

                  {c.reviewStatus === "PENDING" ? (
                    <div style={{ display: "flex", gap: 6, marginLeft: 12 }}>
                      <button style={S.btnSm} onClick={() => setModal({ type: "detail", data: c })}>상세보기</button>
                      {isAdmin && (
                        <>
                          <button style={S.btnAccept} disabled={reviewing === c.candidateId}
                            onClick={() => handleReview(c.candidateId, "ACCEPT")}>
                            {reviewing === c.candidateId ? "반영 중..." : "✓ 승인"}
                          </button>
                          <button style={S.btnReject} disabled={reviewing === c.candidateId}
                            onClick={() => handleReview(c.candidateId, "REJECT")}>✗ 반려</button>
                        </>
                      )}
                    </div>
                  ) : (
                    <span style={{
                      fontSize: 12, fontWeight: 500, padding: "4px 14px", borderRadius: 20, marginLeft: 12,
                      background: isAccepted(c.reviewStatus) ? "#D1FAE5" : "#FDECEA",
                      color: isAccepted(c.reviewStatus) ? "#065F46" : "#B91C1C",
                    }}>
                      {isAccepted(c.reviewStatus) ? "✅ 승인됨" : "❌ 반려됨"}
                    </span>
                  )}
                </div>

                <div style={S.faqA}>{c.answerDraft}</div>

                {/* 기존 학습데이터와 겹치는 항목 — 중복 반영을 막기 위해 질문 원문까지 보여준다 */}
                {c.matchedFaqs?.length > 0 && (
                  <div style={S.matchedBox}>
                    <div style={{ fontSize: 11, fontWeight: 600, color: "#6B7280", marginBottom: 6 }}>
                      유사한 기존 FAQ
                    </div>
                    {c.matchedFaqs.map((m, i) => (
                      <div key={i} style={{ display: "flex", justifyContent: "space-between", gap: 10, fontSize: 12, padding: "3px 0" }}>
                        <span style={{ color: "#374151", flex: 1 }}>
                          {m.question || <em style={{ color: "#9CA3AF" }}>운영 FAQ에 없음 (seq {m.matchedFaqSeqNum})</em>}
                        </span>
                        <span style={{ color: "#1565C0", fontWeight: 600, whiteSpace: "nowrap" }}>
                          {(m.matchScore * 100).toFixed(0)}%
                        </span>
                      </div>
                    ))}
                  </div>
                )}

                <div style={{ marginTop: 8, marginBottom: 4, fontSize: 11, color: "#6B7280" }}>대표 키워드</div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 5, marginBottom: 10 }}>
                  {c.representativeKeywords?.map((k) => (
                    <span key={k} style={{ ...S.tag, background: "#EEF2FF", color: "#4338CA", borderColor: "#C7D2FE" }}>{k}</span>
                  ))}
                </div>

                <div style={{ fontSize: 11, color: "#6B7280", marginBottom: 4 }}>유사어 / 오타</div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 5 }}>
                  {c.synonyms?.map((s, i) => (
                    <span key={i} style={{
                      fontSize: 11, padding: "2px 8px", borderRadius: 12,
                      background: s.type === "TYPO" ? "#FEF3C7" : s.type === "ABBR" ? "#F0FDF4" : "#EFF8FF",
                      color: s.type === "TYPO" ? "#92400E" : s.type === "ABBR" ? "#166534" : "#1565C0",
                      border: `1px solid ${s.type === "TYPO" ? "#FDE68A" : s.type === "ABBR" ? "#BBF7D0" : "#BFDBFE"}`,
                    }}>
                      {s.text}
                      <span style={{ fontSize: 9, marginLeft: 3, opacity: 0.6 }}>
                        {s.type === "TYPO" ? "오타" : s.type === "ABBR" ? "약어" : "유사어"}
                      </span>
                    </span>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {/* ── 변경 이력 (전역) ── */}
      {tab === "history" && (
        <div>
          <div style={{ marginBottom: 14, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            {EDIT_TYPES.map((t) => (
              <button key={t.value}
                style={{ ...S.filterChip, ...(editType === t.value ? S.filterChipActive : {}) }}
                onClick={() => runHistory(0, t.value)}>
                {t.label}
              </button>
            ))}
            <span style={{ fontSize: 12, color: "#6B7280" }}>
              총 {historyInfo.totalCount.toLocaleString()}건
              {historyInfo.totalPages > 1 && ` · ${historyPage + 1}/${historyInfo.totalPages} 페이지`}
            </span>
          </div>

          {histories.length === 0 ? (
            <div style={S.empty}><div style={{ fontSize: 32, marginBottom: 8 }}>🗒️</div><div>변경 이력이 없습니다.</div></div>
          ) : (
            histories.map((h) => (
              <div key={h.historyId} style={S.faqCard}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8, gap: 8 }}>
                  <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                    <EditTypeTag editType={h.editType} />
                    <span style={{ fontSize: 11, fontFamily: "monospace", color: "#6B7280" }}>FAQ #{h.faqId}</span>
                    {h.versionId
                      ? <span style={S.meta}>버전 #{h.versionId}</span>
                      : <span style={{ ...S.meta, color: "#9CA3AF" }}>버전 미포함</span>}
                    {h.analysisId && <span style={S.meta}>분석 #{h.analysisId}</span>}
                  </div>
                  <span style={{ fontSize: 11, color: "#9CA3AF", whiteSpace: "nowrap" }}>{h.editedBy || "-"}</span>
                </div>

                {h.beforeQuestion && h.beforeQuestion !== h.afterQuestion && (
                  <div style={{ fontSize: 12, marginBottom: 4 }}>
                    <span style={{ color: "#B91C1C", textDecoration: "line-through" }}>{h.beforeQuestion}</span>
                    {" → "}
                    <span style={{ color: "#065F46" }}>{h.afterQuestion}</span>
                  </div>
                )}
                {(!h.beforeQuestion || h.beforeQuestion === h.afterQuestion) && (
                  <div style={S.faqQ}>{h.afterQuestion || h.beforeQuestion}</div>
                )}
                {h.afterAnswer && <div style={S.faqA}>{h.afterAnswer}</div>}

                {h.editReason && (
                  <div style={{ fontSize: 11, color: "#6B7280", marginTop: 6 }}>사유: {h.editReason}</div>
                )}
                <div style={{ fontSize: 11, color: "#C4C8CE", marginTop: 6 }}>{h.createdAt}</div>
              </div>
            ))
          )}

          {historyInfo.totalPages > 1 && (
            <div style={S.pager}>
              <button style={S.btnSm} disabled={historyPage === 0} onClick={() => runHistory(historyPage - 1, editType)}>← 이전</button>
              <span style={{ fontSize: 12, color: "#6B7280" }}>{historyPage + 1} / {historyInfo.totalPages}</span>
              <button style={S.btnSm} disabled={historyPage >= historyInfo.totalPages - 1} onClick={() => runHistory(historyPage + 1, editType)}>다음 →</button>
            </div>
          )}
        </div>
      )}

      {/* ── 상세보기 모달 ── */}
      {modal?.type === "detail" && (
        <div style={S.modalBg} onClick={() => setModal(null)}>
          <div style={S.modal} onClick={(e) => e.stopPropagation()}>
            <div style={S.modalTitle}>FAQ 후보 상세</div>
            <div style={{ display: "flex", flexDirection: "column", gap: 14, fontSize: 13 }}>
              <div>
                <div style={S.modalLabel}>표준 질문</div>
                <div style={S.modalValue}>{modal.data.standardQuestion}</div>
              </div>
              <div>
                <div style={S.modalLabel}>답변 초안</div>
                <div style={{ ...S.modalValue, lineHeight: 1.7 }}>{modal.data.answerDraft}</div>
              </div>
              {modal.data.similarQuestions?.length > 0 && (
                <div>
                  <div style={S.modalLabel}>유사 질문 ({modal.data.similarQuestions.length})</div>
                  <ul style={{ margin: "6px 0 0", paddingLeft: 18, color: "#6B7280", lineHeight: 1.7 }}>
                    {modal.data.similarQuestions.map((q, i) => <li key={i}>{q}</li>)}
                  </ul>
                </div>
              )}
              <div>
                <div style={S.modalLabel}>유사어</div>
                <div style={{ display: "flex", flexWrap: "wrap", gap: 5, marginTop: 6 }}>
                  {modal.data.synonyms?.map((s, i) => (
                    <span key={i} style={{
                      fontSize: 11, padding: "2px 8px", borderRadius: 12,
                      background: s.type === "TYPO" ? "#FEF3C7" : "#EFF8FF",
                      color: s.type === "TYPO" ? "#92400E" : "#1565C0",
                      border: `1px solid ${s.type === "TYPO" ? "#FDE68A" : "#BFDBFE"}`,
                    }}>
                      {s.text}
                      <span style={{ fontSize: 9, marginLeft: 3, opacity: 0.6 }}>
                        {s.type === "TYPO" ? "오타" : s.type === "ABBR" ? "약어" : "유사어"}
                      </span>
                    </span>
                  ))}
                </div>
              </div>
              <div style={{ display: "flex", gap: 20 }}>
                <div>
                  <div style={S.modalLabel}>매칭 건수</div>
                  <div style={{ fontWeight: 600, fontSize: 16 }}>{modal.data.occurrenceCount}건</div>
                </div>
              </div>
            </div>
            <div style={S.modalActions}>
              <button style={S.btnSm} onClick={() => setModal(null)}>닫기</button>
              {isAdmin && (
                <>
                  <button style={S.btnReject} onClick={() => { handleReview(modal.data.candidateId, "REJECT"); setModal(null); }}>✗ 반려</button>
                  <button style={S.btnAccept} onClick={() => { handleReview(modal.data.candidateId, "ACCEPT"); setModal(null); }}>✓ 승인</button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* ── 수정 모달 ── */}
      {modal?.type === "edit" && (
        <div style={S.modalBg} onClick={() => setModal(null)}>
          <div style={S.modal} onClick={(e) => e.stopPropagation()}>
            <div style={S.modalTitle}>FAQ 수정</div>
            <EditFaqForm faq={modal.data} onSave={handleUpdate} onClose={() => setModal(null)} />
          </div>
        </div>
      )}
    </div>
  );
}

function EditFaqForm({ faq, onSave, onClose }) {
  const [q, setQ]       = useState(faq.standardQuestion || "");
  const [a, setA]       = useState(faq.answer || "");
  const [tags, setTags] = useState(faq.keywords?.join(", ") || "");
  const [reason, setReason] = useState("");

  return (
    <div>
      <div style={{ display: "flex", flexDirection: "column", gap: 12, marginTop: 8 }}>
        <div>
          <label style={S.modalLabel}>표준 질문</label>
          <input style={S.input} value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
        <div>
          <label style={S.modalLabel}>답변</label>
          <textarea style={{ ...S.input, height: 100, resize: "vertical", paddingTop: 10 }}
            value={a} onChange={(e) => setA(e.target.value)} />
        </div>
        <div>
          <label style={S.modalLabel}>키워드 (쉼표로 구분)</label>
          <input style={S.input} value={tags} onChange={(e) => setTags(e.target.value)} />
        </div>
        <div>
          <label style={S.modalLabel}>수정 사유 (선택)</label>
          <input style={S.input} placeholder="변경 이력에 함께 기록됩니다"
            value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
      </div>
      <div style={S.modalActions}>
        <button style={S.btnSm} onClick={onClose}>취소</button>
        <button style={{ ...S.btnAccept, padding: "8px 20px" }} onClick={() =>
          onSave(
            {
              ...faq,
              standardQuestion: q,
              answer: a,
              keywords: tags.split(",").map((t) => t.trim()).filter(Boolean),
            },
            reason.trim()
          )
        }>저장</button>
      </div>
    </div>
  );
}

const S = {
  page:        { padding: 28 },
  pageTitle:   { fontSize: 20, fontWeight: 700, color: "#111827", marginBottom: 4 },
  pageSub:     { fontSize: 13, color: "#6B7280", marginBottom: 20 },
  tabs:        { display: "flex", gap: 2, background: "#F3F4F6", borderRadius: 10, padding: 4, width: "fit-content", marginBottom: 20 },
  tab:         { padding: "8px 20px", borderRadius: 8, fontSize: 13, cursor: "pointer", color: "#6B7280" },
  tabActive:   { background: "#fff", color: "#1565C0", fontWeight: 600, boxShadow: "0 1px 3px rgba(0,0,0,.08)" },
  searchInput: { height: 38, padding: "0 14px", border: "1.5px solid #D1D5DB", borderRadius: 8, fontSize: 13, fontFamily: "inherit", outline: "none", width: 280, boxSizing: "border-box" },
  btnSearch:   { height: 38, padding: "0 18px", background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  filterChip:  { padding: "5px 14px", background: "#F3F4F6", color: "#6B7280", border: "none", borderRadius: 20, fontSize: 12, cursor: "pointer", fontFamily: "inherit" },
  filterChipActive: { background: "#1565C0", color: "#fff", fontWeight: 600 },
  faqCard:     { background: "#fff", borderRadius: 12, padding: 18, marginBottom: 10, boxShadow: "0 1px 3px rgba(0,0,0,.06)", border: "1px solid rgba(0,0,0,.06)" },
  candCard:    { background: "#fff", borderRadius: 12, padding: 18, marginBottom: 12, boxShadow: "0 1px 3px rgba(0,0,0,.06)", border: "1px solid rgba(0,0,0,.06)" },
  faqQ:        { fontSize: 14, fontWeight: 600, color: "#111827", marginBottom: 4 },
  faqA:        { fontSize: 12, color: "#6B7280", lineHeight: 1.65 },
  tag:         { fontSize: 11, padding: "2px 9px", background: "#EFF8FF", color: "#1565C0", borderRadius: 12, border: "1px solid #BFDBFE" },
  categoryTag: { fontSize: 10, fontWeight: 600, padding: "2px 8px", borderRadius: 10, background: "#F0FDF4", color: "#166534", border: "1px solid #BBF7D0" },
  meta:        { fontSize: 11, color: "#6B7280", background: "#F3F4F6", padding: "2px 8px", borderRadius: 10 },
  matchedBox:  { marginTop: 10, padding: "10px 12px", background: "#F9FAFB", borderRadius: 8, border: "1px solid #F3F4F6" },
  infoBanner:  { background: "#EFF8FF", border: "1px solid #BFDBFE", borderRadius: 10, padding: "12px 16px", fontSize: 13, color: "#1E40AF", marginBottom: 16 },
  empty:       { background: "#fff", borderRadius: 12, padding: 48, textAlign: "center", color: "#9CA3AF", fontSize: 14 },
  btnSm:       { padding: "6px 14px", background: "#F3F4F6", color: "#374151", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 500, cursor: "pointer", fontFamily: "inherit" },
  btnSmDanger: { padding: "6px 14px", background: "#FDECEA", color: "#B91C1C", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 500, cursor: "pointer", fontFamily: "inherit" },
  btnAccept:   { padding: "6px 14px", background: "#D1FAE5", color: "#065F46", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  btnReject:   { padding: "6px 14px", background: "#FDECEA", color: "#B91C1C", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  input:       { width: "100%", height: 42, padding: "0 14px", border: "1.5px solid #D1D5DB", borderRadius: 8, fontSize: 13, fontFamily: "inherit", outline: "none", boxSizing: "border-box" },
  modalBg:     { position: "fixed", inset: 0, background: "rgba(0,0,0,.45)", zIndex: 1000, display: "flex", alignItems: "center", justifyContent: "center", padding: 20 },
  modal:       { background: "#fff", borderRadius: 16, padding: 28, maxWidth: 540, width: "100%", maxHeight: "86vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,.2)" },
  modalTitle:  { fontSize: 17, fontWeight: 700, color: "#111827", marginBottom: 16 },
  modalLabel:  { fontSize: 11, fontWeight: 600, color: "#6B7280", textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 5, display: "block" },
  modalValue:  { fontSize: 13, color: "#111827" },
  modalActions:{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 20 },
  pager:       { display: "flex", alignItems: "center", justifyContent: "center", gap: 14, marginTop: 16 },
};
