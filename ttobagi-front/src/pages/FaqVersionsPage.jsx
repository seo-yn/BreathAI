import { useEffect, useState } from "react";
import { faqApi } from "../api/faq.js";
import { useToast } from "../hooks/useToast.js";

// API 명세서(Notion)에는 있지만 백엔드 FaqController에는 아직 구현되지 않은 기능.
// 엔드포인트는 api/faq.js에 이미 정의해뒀으니, 백엔드가 배포되면 이 페이지만 자연스럽게 정상 동작한다.
// 상세/다운로드 응답 필드명은 명세서에 정확히 없어서 흔한 이름 몇 가지를 방어적으로 시도한다.
export default function FaqVersionsPage() {
  const [state, setState] = useState("loading"); // loading | ready | not-implemented
  const [versions, setVersions] = useState([]);
  const [errorMessage, setErrorMessage] = useState("");
  const [detail, setDetail] = useState(null); // { versionId, loading, items }
  const [downloadingId, setDownloadingId] = useState(null);
  const toast = useToast();

  useEffect(() => {
    faqApi.getVersions().then((res) => {
      setVersions(res.data?.versions || res.data?.content || []);
      setState("ready");
    }).catch((err) => {
      // 참고: 이 백엔드는 존재하지 않는 라우트도 404가 아니라 500(서버 내부 오류)으로 응답한다
      // (GlobalExceptionHandler의 catch-all Exception 핸들러가 라우팅 실패까지 삼킴).
      // 그래서 상태 코드로 "미구현"과 "진짜 500"을 구분할 수 없어, 이 페이지에선 실패를 전부
      // "아직 준비되지 않은 기능"으로 안내한다. 백엔드가 배포되면 이 catch에 안 걸리고 정상 목록이 뜬다.
      setErrorMessage(err.message || "");
      setState("not-implemented");
    });
  }, []);

  const openDetail = async (versionId) => {
    setDetail({ versionId, loading: true, items: [] });
    try {
      const res = await faqApi.getVersionDetail(versionId);
      const items = res.data?.faqList || res.data?.items || res.data?.content || [];
      setDetail({ versionId, loading: false, items });
    } catch (err) {
      toast.error(err.message || "버전 상세를 불러오지 못했습니다.");
      setDetail(null);
    }
  };

  const handleDownload = async (versionId) => {
    setDownloadingId(versionId);
    try {
      const res = await faqApi.downloadExcel(versionId);
      const url = res.data?.downloadUrl || res.data?.fileUrl || res.data?.url;
      if (url) {
        window.open(url, "_blank", "noopener");
      } else {
        toast.info("다운로드 요청은 성공했지만 파일 위치를 찾지 못했습니다.");
      }
    } catch (err) {
      toast.error(err.message || "엑셀 다운로드에 실패했습니다.");
    } finally {
      setDownloadingId(null);
    }
  };

  return (
    <div style={S.page}>
      <div style={S.pageTitle}>FAQ 버전 관리</div>
      <div style={S.pageSub}>FAQ 반영 이력을 버전 단위로 확인하고 엑셀로 내려받습니다.</div>

      {state === "loading" && <div style={S.empty}>불러오는 중...</div>}

      {state === "not-implemented" && (
        <div style={S.empty}>
          <div style={{ fontSize: 32, marginBottom: 10 }}>🚧</div>
          <div style={{ fontWeight: 600, marginBottom: 6 }}>백엔드 준비 중인 기능입니다</div>
          <div style={{ fontSize: 12, color: "#9CA3AF" }}>
            FAQ 버전 목록/상세 조회, 엑셀 다운로드 API가 배포되면 이 화면에서 바로 확인할 수 있습니다.
          </div>
          {errorMessage && (
            <div style={{ fontSize: 11, color: "#D1D5DB", marginTop: 10 }}>({errorMessage})</div>
          )}
        </div>
      )}

      {state === "ready" && (
        versions.length === 0 ? (
          <div style={S.empty}>등록된 버전이 없습니다.</div>
        ) : (
          versions.map((v) => (
            <div key={v.versionId} style={{ ...S.card, display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div>
                <div style={{ fontWeight: 600, fontSize: 14, color: "#111827" }}>버전 {v.versionId}</div>
                <div style={{ fontSize: 12, color: "#6B7280", marginTop: 4 }}>{v.createdAt}</div>
              </div>
              <div style={{ display: "flex", gap: 6 }}>
                <button style={S.btnSm} onClick={() => openDetail(v.versionId)}>상세보기</button>
                <button style={S.btnSm} onClick={() => handleDownload(v.versionId)} disabled={downloadingId === v.versionId}>
                  {downloadingId === v.versionId ? "다운로드 중..." : "엑셀 다운로드"}
                </button>
              </div>
            </div>
          ))
        )
      )}

      {/* ── 버전 상세 모달 ── */}
      {detail && (
        <div style={S.modalBg} onClick={() => setDetail(null)}>
          <div style={S.modal} onClick={(e) => e.stopPropagation()}>
            <div style={S.modalTitle}>버전 {detail.versionId} 상세</div>
            {detail.loading ? (
              <div style={{ fontSize: 13, color: "#9CA3AF", textAlign: "center", padding: 20 }}>불러오는 중...</div>
            ) : detail.items.length === 0 ? (
              <div style={{ fontSize: 13, color: "#9CA3AF", textAlign: "center", padding: 20 }}>표시할 항목이 없습니다.</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: 10, maxHeight: 420, overflowY: "auto" }}>
                {detail.items.map((item, i) => (
                  <div key={item.faqId || i} style={S.detailItem}>
                    <div style={{ fontSize: 13, fontWeight: 600, color: "#111827" }}>{item.question || item.title || `항목 ${i + 1}`}</div>
                    {item.answer && <div style={{ fontSize: 12, color: "#6B7280", marginTop: 4 }}>{item.answer}</div>}
                  </div>
                ))}
              </div>
            )}
            <div style={S.modalActions}>
              <button style={S.btnSm} onClick={() => setDetail(null)}>닫기</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const S = {
  page:      { padding: 28 },
  pageTitle: { fontSize: 20, fontWeight: 700, color: "#111827", marginBottom: 4 },
  pageSub:   { fontSize: 13, color: "#6B7280", marginBottom: 20 },
  empty:     { background: "#fff", borderRadius: 12, padding: 48, textAlign: "center", color: "#9CA3AF", fontSize: 14, boxShadow: "0 1px 3px rgba(0,0,0,.06)", border: "1px solid rgba(0,0,0,.06)" },
  card:      { background: "#fff", borderRadius: 12, padding: 18, marginBottom: 10, boxShadow: "0 1px 3px rgba(0,0,0,.06)", border: "1px solid rgba(0,0,0,.06)" },
  btnSm:     { padding: "6px 14px", background: "#F3F4F6", color: "#374151", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 500, cursor: "pointer", fontFamily: "inherit" },
  modalBg:   { position: "fixed", inset: 0, background: "rgba(0,0,0,.45)", zIndex: 1000, display: "flex", alignItems: "center", justifyContent: "center", padding: 20 },
  modal:     { background: "#fff", borderRadius: 16, padding: 28, maxWidth: 540, width: "100%", boxShadow: "0 20px 60px rgba(0,0,0,.2)" },
  modalTitle:{ fontSize: 17, fontWeight: 700, color: "#111827", marginBottom: 16 },
  modalActions: { display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 20 },
  detailItem: { padding: "12px 14px", background: "#F9FAFB", borderRadius: 8, border: "1px solid #F3F4F6" },
};
