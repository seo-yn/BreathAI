import { useCallback, useEffect, useState } from "react";
import { faqApi } from "../api/faq.js";
import { saveBlob } from "../api/client.js";
import { useToast } from "../hooks/useToast.js";

const PAGE_SIZE = 10;

// 백엔드 FaqEditHistory.EditType — 버전 상세의 changes[]에 실려온다
const CHANGE_LABEL = {
  CREATE: { text: "신규", background: "#DBEAFE", color: "#1E40AF" },
  EXPAND: { text: "확장", background: "#EDE9FE", color: "#5B21B6" },
  MANUAL: { text: "수정", background: "#FEF3C7", color: "#92400E" },
  DELETE: { text: "삭제", background: "#FDECEA", color: "#B91C1C" },
};

// 백엔드 FaqVersion.VersionType
const VERSION_TYPE_LABEL = { UPLOAD: "분석 반영", DOWNLOAD: "다운로드 시점" };

export default function FaqVersionsPage() {
  const [versions, setVersions] = useState([]);
  const [page, setPage] = useState(0);
  const [pageInfo, setPageInfo] = useState({ totalCount: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState(null); // { loading, data }
  const [downloadingId, setDownloadingId] = useState(null);
  const toast = useToast();

  const applyList = useCallback((res) => {
    setVersions(res.data?.versions || []);
    setPageInfo({
      totalCount: res.data?.totalCount ?? 0,
      totalPages: res.data?.totalPages ?? 0,
    });
  }, []);

  useEffect(() => {
    faqApi.getVersions(0, PAGE_SIZE)
      .then((res) => {
        applyList(res);
        setLoading(false);
      })
      .catch((err) => {
        toast.error(err.message || "버전 목록을 불러오지 못했습니다.");
        setLoading(false);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [applyList]);

  const goToPage = async (next) => {
    try {
      const res = await faqApi.getVersions(next, PAGE_SIZE);
      applyList(res);
      setPage(next);
    } catch (err) {
      toast.error(err.message || "버전 목록을 불러오지 못했습니다.");
    }
  };

  const openDetail = async (versionId) => {
    setDetail({ loading: true, data: null });
    try {
      const res = await faqApi.getVersionDetail(versionId);
      setDetail({ loading: false, data: res.data });
    } catch (err) {
      toast.error(err.message || "버전 상세를 불러오지 못했습니다.");
      setDetail(null);
    }
  };

  // versionId를 생략하면 서버가 최신 내용으로 새 버전(VersionType.DOWNLOAD)을 만들고 내려준다.
  // 그래서 "최신 내용 다운로드" 후에는 목록을 다시 받아야 새 버전이 보인다.
  const handleDownload = async (versionId) => {
    setDownloadingId(versionId ?? "latest");
    try {
      const { blob, fileName } = await faqApi.downloadCsv(versionId);
      saveBlob(blob, fileName);
      toast.success(`${fileName} 다운로드를 시작했습니다.`);
      if (versionId == null) {
        const res = await faqApi.getVersions(0, PAGE_SIZE);
        applyList(res);
        setPage(0);
      }
    } catch (err) {
      toast.error(err.message || "CSV 다운로드에 실패했습니다.");
    } finally {
      setDownloadingId(null);
    }
  };

  if (loading) return <div style={{ padding: 40, color: "#6B7280" }}>불러오는 중...</div>;

  return (
    <div style={S.page}>
      <div style={S.pageTitle}>FAQ 버전 관리</div>
      <div style={S.pageSub}>
        분석 반영과 다운로드 시점마다 FAQ 스냅샷이 버전으로 쌓입니다. 버전별로 CSV를 내려받을 수 있습니다.
      </div>

      <div style={{ marginBottom: 14, display: "flex", gap: 10, alignItems: "center" }}>
        <button style={S.btnPrimary} disabled={downloadingId === "latest"}
          onClick={() => handleDownload(undefined)}>
          {downloadingId === "latest" ? "내려받는 중..." : "최신 내용 CSV 다운로드"}
        </button>
        <span style={{ fontSize: 12, color: "#6B7280" }}>
          총 {pageInfo.totalCount.toLocaleString()}개 버전
          {pageInfo.totalPages > 1 && ` · ${page + 1}/${pageInfo.totalPages} 페이지`}
        </span>
      </div>
      <div style={{ fontSize: 11, color: "#9CA3AF", marginBottom: 16 }}>
        최신 내용을 내려받으면 그 시점의 스냅샷이 새 버전으로 기록됩니다.
      </div>

      {versions.length === 0 ? (
        <div style={S.empty}>
          <div style={{ fontSize: 32, marginBottom: 8 }}>🗂️</div>
          <div>아직 버전이 없습니다.</div>
          <div style={{ fontSize: 12, marginTop: 6 }}>분석 결과를 반영하거나 CSV를 내려받으면 생성됩니다.</div>
        </div>
      ) : (
        versions.map((v) => (
          <div key={v.versionId} style={S.card}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
              <div style={{ flex: 1 }}>
                <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 6, flexWrap: "wrap" }}>
                  <span style={S.versionName}>{v.versionName || `버전 #${v.versionId}`}</span>
                  {v.isLatest && <span style={S.latestTag}>최신</span>}
                  <span style={S.typeTag}>{VERSION_TYPE_LABEL[v.versionType] || v.versionType}</span>
                </div>
                <div style={{ display: "flex", gap: 14, fontSize: 12, color: "#6B7280", flexWrap: "wrap" }}>
                  <span>전체 <strong style={{ color: "#111827" }}>{(v.totalFaqCount ?? 0).toLocaleString()}</strong>건</span>
                  <span>신규 <strong style={{ color: "#1565C0" }}>{v.createdCount ?? 0}</strong></span>
                  <span>확장 <strong style={{ color: "#5B21B6" }}>{v.expandedCount ?? 0}</strong></span>
                </div>
                <div style={{ fontSize: 11, color: "#9CA3AF", marginTop: 6 }}>
                  {v.createdAt} · {v.createdBy || "-"}
                </div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button style={S.btnSm} onClick={() => openDetail(v.versionId)}>상세</button>
                <button style={S.btnSm} disabled={downloadingId === v.versionId}
                  onClick={() => handleDownload(v.versionId)}>
                  {downloadingId === v.versionId ? "..." : "CSV"}
                </button>
              </div>
            </div>
          </div>
        ))
      )}

      {pageInfo.totalPages > 1 && (
        <div style={S.pager}>
          <button style={S.btnSm} disabled={page === 0} onClick={() => goToPage(page - 1)}>← 이전</button>
          <span style={{ fontSize: 12, color: "#6B7280" }}>{page + 1} / {pageInfo.totalPages}</span>
          <button style={S.btnSm} disabled={page >= pageInfo.totalPages - 1} onClick={() => goToPage(page + 1)}>다음 →</button>
        </div>
      )}

      {/* ── 버전 상세 모달 ── */}
      {detail && (
        <div style={S.modalBg} onClick={() => setDetail(null)}>
          <div style={S.modal} onClick={(e) => e.stopPropagation()}>
            {detail.loading ? (
              <div style={{ padding: 20, textAlign: "center", color: "#9CA3AF", fontSize: 13 }}>불러오는 중...</div>
            ) : (
              <>
                <div style={S.modalTitle}>
                  {detail.data?.versionName || `버전 #${detail.data?.versionId}`}
                </div>

                <div style={S.statRow}>
                  {[
                    { label: "전체", value: detail.data?.totalFaqCount ?? 0, color: "#111827" },
                    { label: "신규", value: detail.data?.createdCount ?? 0, color: "#1565C0" },
                    { label: "확장", value: detail.data?.expandedCount ?? 0, color: "#5B21B6" },
                    { label: "수정", value: detail.data?.manualCount ?? 0, color: "#92400E" },
                    { label: "삭제", value: detail.data?.deletedCount ?? 0, color: "#B91C1C" },
                  ].map((s) => (
                    <div key={s.label} style={S.statBox}>
                      <div style={{ fontSize: 11, color: "#6B7280", marginBottom: 4 }}>{s.label}</div>
                      <div style={{ fontSize: 18, fontWeight: 700, color: s.color }}>{s.value.toLocaleString()}</div>
                    </div>
                  ))}
                </div>

                <div style={{ fontSize: 11, color: "#9CA3AF", marginBottom: 14 }}>
                  {detail.data?.createdAt} · {detail.data?.createdBy || "-"}
                  {detail.data?.previousVersionId && ` · 이전 버전 #${detail.data.previousVersionId}`}
                </div>

                <div style={S.modalLabel}>이 버전의 변경 내역 ({detail.data?.changes?.length ?? 0})</div>
                {!detail.data?.changes?.length ? (
                  <div style={{ fontSize: 13, color: "#9CA3AF", padding: "16px 0" }}>변경 내역이 없습니다.</div>
                ) : (
                  <div style={{ display: "flex", flexDirection: "column", gap: 8, maxHeight: 360, overflowY: "auto" }}>
                    {detail.data.changes.map((c) => {
                      const tag = CHANGE_LABEL[c.editType] || CHANGE_LABEL.MANUAL;
                      return (
                        <div key={c.historyId} style={S.changeItem}>
                          <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 4 }}>
                            <span style={{
                              fontSize: 10, fontWeight: 600, padding: "2px 8px", borderRadius: 10,
                              background: tag.background, color: tag.color,
                            }}>{tag.text}</span>
                            <span style={{ fontSize: 11, fontFamily: "monospace", color: "#9CA3AF" }}>FAQ #{c.faqId}</span>
                          </div>
                          <div style={{ fontSize: 13, color: "#111827" }}>{c.standardQuestion}</div>
                        </div>
                      );
                    })}
                  </div>
                )}

                <div style={S.modalActions}>
                  <button style={S.btnSm} onClick={() => setDetail(null)}>닫기</button>
                  <button style={S.btnPrimarySm} disabled={downloadingId === detail.data?.versionId}
                    onClick={() => handleDownload(detail.data?.versionId)}>
                    CSV 다운로드
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

const S = {
  page:        { padding: 28 },
  pageTitle:   { fontSize: 20, fontWeight: 700, color: "#111827", marginBottom: 4 },
  pageSub:     { fontSize: 13, color: "#6B7280", marginBottom: 20, lineHeight: 1.6 },
  card:        { background: "#fff", borderRadius: 12, padding: 18, marginBottom: 10, boxShadow: "0 1px 3px rgba(0,0,0,.06)", border: "1px solid rgba(0,0,0,.06)" },
  versionName: { fontSize: 14, fontWeight: 600, color: "#111827" },
  latestTag:   { fontSize: 10, fontWeight: 600, padding: "2px 8px", borderRadius: 10, background: "#D1FAE5", color: "#065F46", border: "1px solid #A7F3D0" },
  typeTag:     { fontSize: 10, fontWeight: 600, padding: "2px 8px", borderRadius: 10, background: "#F3F4F6", color: "#6B7280" },
  empty:       { background: "#fff", borderRadius: 12, padding: 48, textAlign: "center", color: "#9CA3AF", fontSize: 14 },
  btnPrimary:  { height: 38, padding: "0 18px", background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  btnPrimarySm:{ padding: "6px 16px", background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  btnSm:       { padding: "6px 14px", background: "#F3F4F6", color: "#374151", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 500, cursor: "pointer", fontFamily: "inherit" },
  pager:       { display: "flex", alignItems: "center", justifyContent: "center", gap: 14, marginTop: 16 },
  modalBg:     { position: "fixed", inset: 0, background: "rgba(0,0,0,.45)", zIndex: 1000, display: "flex", alignItems: "center", justifyContent: "center", padding: 20 },
  modal:       { background: "#fff", borderRadius: 16, padding: 28, maxWidth: 560, width: "100%", maxHeight: "86vh", overflowY: "auto", boxShadow: "0 20px 60px rgba(0,0,0,.2)" },
  modalTitle:  { fontSize: 17, fontWeight: 700, color: "#111827", marginBottom: 16 },
  modalLabel:  { fontSize: 11, fontWeight: 600, color: "#6B7280", textTransform: "uppercase", letterSpacing: ".06em", marginBottom: 8, display: "block" },
  modalActions:{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 20 },
  statRow:     { display: "grid", gridTemplateColumns: "repeat(5, 1fr)", gap: 8, marginBottom: 12 },
  statBox:     { textAlign: "center", padding: "10px 6px", background: "#F9FAFB", borderRadius: 8 },
  changeItem:  { padding: "10px 12px", background: "#F9FAFB", borderRadius: 8, border: "1px solid #F3F4F6" },
};
