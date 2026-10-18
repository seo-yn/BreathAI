import { useCallback, useEffect, useState } from "react";
import { faqApi } from "../api/faq.js";
import { useToast } from "../hooks/useToast.js";

// 또타24 챗봇의 기존 학습데이터(질문-답변 쌍, 약 2000건) 조회 화면.
//
// 백엔드에 아직 엔드포인트가 없다. DTO 목록에 TrainingDataListResponse만 떠 있고
// API 명세서 표에는 해당 행 자체가 빠져 있어서, docs/backend-api-requests.md 에
// 제안한 명세(GET /api/v1/faq/training-data)를 기준으로 미리 붙여둔다.
// FaqVersionsPage와 같은 방식으로, 호출이 실패하면 "준비 중"으로 안내한다.
//
// 2000건 규모라 전체를 받아 클라이언트에서 거르는 방식은 쓰지 않는다.
// 페이지 이동과 검색 모두 서버 요청으로 처리한다.
const PAGE_SIZE = 20;

export default function TrainingDataPage() {
  const [state, setState] = useState("loading"); // loading | ready | not-implemented | error
  const [rows, setRows] = useState([]);
  const [pageInfo, setPageInfo] = useState({ totalCount: 0, totalPages: 0 });
  const [page, setPage] = useState(0);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [fetching, setFetching] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [expanded, setExpanded] = useState(null);
  const toast = useToast();

  const applyResponse = useCallback((res) => {
    // 응답 필드명이 명세서에 확정돼 있지 않아 흔한 이름 몇 가지를 방어적으로 시도한다
    const list = res.data?.trainingData || res.data?.items || res.data?.content || [];
    setRows(list);
    setPageInfo({
      totalCount: res.data?.totalCount ?? list.length,
      totalPages: res.data?.totalPages ?? 0,
    });
  }, []);

  useEffect(() => {
    faqApi.getTrainingData({ page: 0, size: PAGE_SIZE })
      .then((res) => {
        applyResponse(res);
        setState("ready");
      })
      .catch((err) => {
        // 아직 엔드포인트가 없을 때 404가 아니라 400이 온다. /api/v1/faq/{faqId}에
        // 먼저 걸려서 "training-data"를 Long으로 변환하다 실패하기 때문이다
        // (MethodArgumentTypeMismatchException -> 400 "요청 값의 형식이 올바르지 않습니다").
        // 그래서 400과 404를 모두 "준비 중"으로 보고, 나머지만 실제 오류로 처리한다.
        setErrorMessage(err.message || "");
        setState(err.status === 404 || err.status === 400 ? "not-implemented" : "error");
      });
  }, [applyResponse]);

  const run = async (targetPage, targetKeyword) => {
    setFetching(true);
    try {
      const res = await faqApi.getTrainingData({
        page: targetPage,
        size: PAGE_SIZE,
        keyword: targetKeyword || undefined,
      });
      applyResponse(res);
      setPage(targetPage);
      setKeyword(targetKeyword);
      setExpanded(null);
    } catch (err) {
      toast.error(err.message || "학습데이터를 불러오지 못했습니다.");
    } finally {
      setFetching(false);
    }
  };

  return (
    <div style={S.page}>
      <div style={S.pageTitle}>기존 학습데이터</div>
      <div style={S.pageSub}>
        또타24 챗봇이 현재 학습하고 있는 질문-답변 쌍입니다. 신규 FAQ 후보는 이 데이터와 비교해 추천됩니다.
      </div>

      {state === "loading" && <div style={{ color: "#6B7280", fontSize: 13 }}>불러오는 중...</div>}

      {state === "not-implemented" && (
        <div style={S.notice}>
          <div style={{ fontSize: 32, marginBottom: 10 }}>🚧</div>
          <div style={{ fontWeight: 600, color: "#374151", marginBottom: 6 }}>아직 준비되지 않은 기능입니다.</div>
          <div style={{ fontSize: 13, color: "#6B7280", lineHeight: 1.6 }}>
            기존 학습데이터 조회 API(<code style={S.code}>GET /api/v1/faq/training-data</code>)가
            백엔드에 추가되면 이 화면에서 바로 확인할 수 있습니다.
          </div>
          {errorMessage && (
            <div style={{ fontSize: 11, color: "#9CA3AF", marginTop: 12 }}>서버 응답: {errorMessage}</div>
          )}
        </div>
      )}

      {state === "error" && (
        <div style={S.notice}>
          <div style={{ fontSize: 32, marginBottom: 10 }}>⚠️</div>
          <div style={{ fontWeight: 600, color: "#374151", marginBottom: 6 }}>학습데이터를 불러오지 못했습니다.</div>
          <div style={{ fontSize: 13, color: "#6B7280" }}>{errorMessage || "잠시 후 다시 시도해주세요."}</div>
        </div>
      )}

      {state === "ready" && (
        <>
          <div style={{ marginBottom: 14, display: "flex", gap: 10, alignItems: "center" }}>
            <input style={S.searchInput} placeholder="질문 또는 답변으로 검색..."
              value={keywordInput}
              onChange={(e) => setKeywordInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") run(0, keywordInput.trim()); }} />
            <button style={S.btnPrimary} disabled={fetching} onClick={() => run(0, keywordInput.trim())}>
              {fetching ? "검색 중..." : "검색"}
            </button>
            {keyword && (
              <button style={S.btnSm} onClick={() => { setKeywordInput(""); run(0, ""); }}>초기화</button>
            )}
            <span style={{ fontSize: 12, color: "#6B7280" }}>
              총 {pageInfo.totalCount.toLocaleString()}건
              {pageInfo.totalPages > 1 && ` · ${page + 1}/${pageInfo.totalPages} 페이지`}
            </span>
          </div>

          {rows.length === 0 ? (
            <div style={S.empty}>
              <div style={{ fontSize: 32, marginBottom: 8 }}>🔍</div>
              <div>{keyword ? "검색 결과가 없습니다." : "학습데이터가 없습니다."}</div>
            </div>
          ) : (
            rows.map((r, i) => {
              const id = r.seqNum ?? r.sourceSeqNum ?? i;
              const isOpen = expanded === id;
              return (
                <div key={id} style={S.card} onClick={() => setExpanded(isOpen ? null : id)}>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
                    <div style={{ flex: 1 }}>
                      <div style={S.q}>{r.question ?? r.ciQuestion}</div>
                      <div style={{ ...S.a, ...(isOpen ? {} : S.aClamped) }}>
                        {r.answer ?? r.ciAnswer0}
                      </div>
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 6 }}>
                      <span style={S.seq}>#{r.seqNum ?? r.sourceSeqNum ?? "-"}</span>
                      {r.qaCnt > 0 && <span style={S.meta}>누적 {r.qaCnt.toLocaleString()}건</span>}
                      {r.linkedFaqId && <span style={S.linked}>FAQ #{r.linkedFaqId} 연동</span>}
                    </div>
                  </div>
                  <div style={{ fontSize: 11, color: "#C4C8CE", marginTop: 8 }}>
                    {isOpen ? "접기" : "클릭하면 전체 답변 보기"}
                  </div>
                </div>
              );
            })
          )}

          {pageInfo.totalPages > 1 && (
            <div style={S.pager}>
              <button style={S.btnSm} disabled={fetching || page === 0}
                onClick={() => run(page - 1, keyword)}>← 이전</button>
              <span style={{ fontSize: 12, color: "#6B7280" }}>{page + 1} / {pageInfo.totalPages}</span>
              <button style={S.btnSm} disabled={fetching || page >= pageInfo.totalPages - 1}
                onClick={() => run(page + 1, keyword)}>다음 →</button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

const S = {
  page:        { padding: 28 },
  pageTitle:   { fontSize: 20, fontWeight: 700, color: "#111827", marginBottom: 4 },
  pageSub:     { fontSize: 13, color: "#6B7280", marginBottom: 20 },
  searchInput: { height: 40, padding: "0 14px", border: "1.5px solid #D1D5DB", borderRadius: 8, fontSize: 13, fontFamily: "inherit", outline: "none", width: 320, boxSizing: "border-box" },
  card:        { background: "#fff", borderRadius: 12, padding: 18, marginBottom: 10, boxShadow: "0 1px 3px rgba(0,0,0,.06)", border: "1px solid rgba(0,0,0,.06)", cursor: "pointer" },
  q:           { fontSize: 14, fontWeight: 600, color: "#111827", marginBottom: 6 },
  a:           { fontSize: 12, color: "#6B7280", lineHeight: 1.65, whiteSpace: "pre-wrap" },
  aClamped:    { display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" },
  seq:         { fontSize: 11, fontFamily: "monospace", color: "#9CA3AF" },
  meta:        { fontSize: 11, color: "#6B7280", background: "#F3F4F6", padding: "2px 8px", borderRadius: 10 },
  linked:      { fontSize: 10, fontWeight: 600, color: "#065F46", background: "#D1FAE5", padding: "2px 8px", borderRadius: 10, border: "1px solid #A7F3D0" },
  notice:      { background: "#fff", borderRadius: 12, padding: 48, textAlign: "center", border: "1px solid rgba(0,0,0,.06)", boxShadow: "0 1px 3px rgba(0,0,0,.06)" },
  code:        { background: "#F3F4F6", padding: "1px 6px", borderRadius: 4, fontSize: 12 },
  empty:       { background: "#fff", borderRadius: 12, padding: 48, textAlign: "center", color: "#9CA3AF", fontSize: 14 },
  btnPrimary:  { padding: "0 18px", height: 40, background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  btnSm:       { padding: "6px 14px", background: "#F3F4F6", color: "#374151", border: "none", borderRadius: 8, fontSize: 12, fontWeight: 500, cursor: "pointer", fontFamily: "inherit" },
  pager:       { display: "flex", alignItems: "center", justifyContent: "center", gap: 14, marginTop: 16 },
};
