import { useEffect, useState } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, Legend, ResponsiveContainer, CartesianGrid } from "recharts";
import { dashApi } from "../api/dashboard.js";
import { useToast } from "../hooks/useToast.js";

export default function DashboardPage() {
  const [analyze, setAnalyze]   = useState(null);
  const [loading, setLoading]   = useState(true);
  const toast = useToast();

  useEffect(() => {
    // 월 단위 사용량(/dashboard/usage) 대신 분석 결과에 담겨 오는 파일별 통계(usageStats)를 쓴다.
    // 분석을 한 번도 돌리지 않았으면 /analyze/result가 404를 주는데, 이는 오류가 아니라
    // 정상적인 초기 상태이므로 안내 배너로 처리한다.
    dashApi.getAnalyzeResult()
      .then((res) => { if (res.data) setAnalyze(res.data); })
      .catch((err) => {
        if (err.status !== 404) toast.error(err.message || "분석 결과를 불러오지 못했습니다.");
      })
      .finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <div style={{ padding: 40, color: "#6B7280" }}>불러오는 중...</div>;

  const system    = analyze?.systemStatus;
  const sampling  = system?.samplingStatus;
  const total     = system?.totalLogCount || 1;
  const perf      = analyze?.evaluation;
  // 백엔드 JSON 키는 unansweredAnalysis가 아니라 unanswerAnalysis다 (AnalyzeResultResponse 필드명)
  const reasons   = analyze?.unanswerAnalysis?.byReason || [];

  // 업로드 파일의 날짜별 건수. 예전 분석이면 totalCnt·answerCnt가 null로 올 수 있어
  // 합계를 낼 때 걸러낸다 (BE 주석: "전체·정상 건수는 예전 분석이면 null").
  const usageStats = analyze?.usageStats || [];
  const sum = (key) => usageStats.reduce((acc, d) => acc + (typeof d[key] === "number" ? d[key] : 0), 0);
  const hasTotal = usageStats.some((d) => typeof d.totalCnt === "number");
  const statTotals = {
    total: sum("totalCnt"),
    answer: sum("answerCnt"),
    lowQuality: sum("lowQualityCount"),
    unanswer: sum("unanswerCnt"),
  };

  // 급증 키워드. increasedRate가 0인데 surgeDetection에서 같은 키워드가 isNew면
  // 이전 기간에 없던 신규 키워드라 증가율을 계산할 수 없는 경우다 (BE 확인).
  // 이때 "+0%" 대신 "신규"로 표시한다.
  const newKeywords = new Set(
    (analyze?.surgeDetection || []).filter((d) => d.isNew).map((d) => d.keyword)
  );
  const burstKeywords = (analyze?.burstKeywords || []).map((b) => ({
    ...b,
    isNew: newKeywords.has(b.keyword) && !(b.increasedRate > 0),
  }));

  const period = analyze?.periodStartDate && analyze?.periodEndDate
    ? `${analyze.periodStartDate} ~ ${analyze.periodEndDate}`
    : null;

  return (
    <div style={S.page}>
      <div style={S.pageTitle}>대시보드</div>
      <div style={S.pageSub}>또타24 챗봇 응답 현황을 확인합니다.</div>

      {!analyze && (
        <div style={S.notice}>
          아직 분석 결과가 없습니다. <strong>신규 데이터 분석</strong>에서 로그를 업로드하면 이곳에 응답 현황이 표시됩니다.
        </div>
      )}

      {/* 통계 카드 */}
      <div style={S.statGrid}>
        {[
          // 월 단위 사용량 대신 업로드한 파일의 전체 건수를 보여 준다.
          // 예전 분석은 totalCnt가 null이라 합계를 낼 수 없으므로 클러스터 로그 수로 대체한다.
          { label: "총 대화 수",      value: (hasTotal ? statTotals.total : (system?.totalLogCount || 0)).toLocaleString(),
            sub: period || "분석 대상 파일 기준", color: "#111827" },
          { label: "정답 (≥75%)",     value: `${sampling ? ((sampling.correct / total) * 100).toFixed(1) : 0}%`,          sub: `${(sampling?.correct || 0).toLocaleString()}건`,    color: "#1565C0" },
          { label: "오답변 (25~75%)", value: `${sampling ? ((sampling.lowQuality / total) * 100).toFixed(1) : 0}%`,       sub: `${(sampling?.lowQuality || 0).toLocaleString()}건`, color: "#7C3AED" },
          { label: "미답변 (<25%)",   value: `${sampling ? ((sampling.unanswer / total) * 100).toFixed(1) : 0}%`,       sub: `${(sampling?.unanswer || 0).toLocaleString()}건`, color: "#EF9F27" },
        ].map((s) => (
          <div key={s.label} style={S.statCard}>
            <div style={S.statLabel}>{s.label}</div>
            <div style={{ ...S.statValue, color: s.color }}>{s.value}</div>
            <div style={S.statSub}>{s.sub}</div>
          </div>
        ))}
      </div>

      {/* 날짜별 응답 현황 — 분석 결과의 usageStats */}
      {usageStats.length > 0 && (
        <div style={{ ...S.card, marginBottom: 20 }}>
          <div style={S.cardHeader}>
            <span style={S.cardTitle}>날짜별 응답 현황</span>
            <span style={{ fontSize: 11, color: "#9CA3AF" }}>{usageStats.length}일</span>
          </div>
          <div style={{ padding: 20, height: 260 }}>
            <ResponsiveContainer>
              <BarChart data={usageStats} margin={{ top: 4, right: 8, left: -12, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                <XAxis dataKey="statDate" tick={{ fontSize: 10, fill: "#9CA3AF" }} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: "#9CA3AF" }} tickLine={false} axisLine={false} />
                <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #E5E7EB" }} />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Bar dataKey="answerCnt"       name="정답"   stackId="a" fill="#1565C0" />
                <Bar dataKey="lowQualityCount" name="오답변" stackId="a" fill="#7C3AED" />
                <Bar dataKey="unanswerCnt"     name="미답변" stackId="a" fill="#EF9F27" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* 미답변 사유 + 성능 비교 */}
      <div style={S.row2}>
        <div style={S.card}>
          <div style={S.cardHeader}><span style={S.cardTitle}>미답변 사유 분석</span></div>
          <div style={{ padding: 20 }}>
            {reasons.length === 0 && <div style={{ color: "#9CA3AF", fontSize: 12 }}>데이터 없음</div>}
            {reasons.map((r) => (
              <div key={r.reason} style={{ marginBottom: 14 }}>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 12, marginBottom: 5 }}>
                  <span style={{ color: "#374151", fontWeight: 500 }}>{r.reason}</span>
                  <span style={{ color: "#6B7280" }}>{r.count.toLocaleString()}건</span>
                </div>
                <div style={{ height: 6, background: "#F3F4F6", borderRadius: 3 }}>
                  <div style={{
                    width: `${Math.min((r.count / (sampling?.unanswer || 1)) * 100, 100)}%`,
                    height: "100%", borderRadius: 3, background: "#1976D2",
                  }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div style={S.card}>
          <div style={S.cardHeader}><span style={S.cardTitle}>급증 키워드</span></div>
          <div style={{ padding: 20 }}>
            {burstKeywords.length === 0 && <div style={{ color: "#9CA3AF", fontSize: 12 }}>데이터 없음</div>}
            {burstKeywords.map((b) => (
              <div key={b.keyword} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "7px 0", borderBottom: "1px solid #F3F4F6", fontSize: 13 }}>
                <span style={{ color: "#374151", fontWeight: 500 }}>{b.keyword}</span>
                <span style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <span style={{ color: "#9CA3AF", fontSize: 12 }}>{(b.count ?? 0).toLocaleString()}건</span>
                  {b.isNew
                    ? <span style={S.newTag}>신규</span>
                    : <span style={{ color: b.increasedRate >= 0 ? "#E53935" : "#1565C0", fontWeight: 600, fontSize: 12 }}>
                        {b.increasedRate > 0 ? "+" : ""}{(b.increasedRate ?? 0).toFixed(0)}%
                      </span>}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div style={{ ...S.row2, marginTop: 16 }}>
        <div style={S.card}>
          <div style={S.cardHeader}><span style={S.cardTitle}>개선 전·후 성능 비교</span></div>
          <div style={{ padding: 20, display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 16 }}>
            {/* "정확도 향상"·"AI 해결 건수"는 BE가 저장값으로 재계산해 신뢰할 수 없어 표시하지 않는다
                (api-spec-changes.md 7절 결정 대기 항목). 확정되면 되살린다. */}
            {perf ? [
              { label: "예상 미답변율",  value: `${perf.afterUnanswerRate}%`,   color: "#43A047" },
              { label: "오답 발생률",    value: `${perf.falsePositiveRate}`,     color: "#EF9F27" },
            ].map((p) => (
              <div key={p.label} style={{ textAlign: "center", padding: 14, background: "#F9FAFB", borderRadius: 10 }}>
                <div style={{ fontSize: 11, color: "#6B7280", marginBottom: 6 }}>{p.label}</div>
                <div style={{ fontSize: 22, fontWeight: 700, color: p.color }}>{p.value}</div>
              </div>
            )) : <div style={{ color: "#9CA3AF", fontSize: 12 }}>데이터 없음</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

const S = {
  page:       { padding: 28 },
  pageTitle:  { fontSize: 20, fontWeight: 700, color: "#111827", marginBottom: 4 },
  pageSub:    { fontSize: 13, color: "#6B7280", marginBottom: 24 },
  statGrid:   { display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 14, marginBottom: 20 },
  statCard:   { background: "#fff", borderRadius: 12, padding: "18px 20px", boxShadow: "0 1px 3px rgba(0,0,0,.08)", border: "1px solid rgba(0,0,0,.06)" },
  statLabel:  { fontSize: 12, color: "#6B7280", marginBottom: 6 },
  statValue:  { fontSize: 26, fontWeight: 700, lineHeight: 1 },
  statSub:    { fontSize: 11, color: "#6B7280", marginTop: 4 },
  row2:       { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 },
  card:       { background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,.08)", border: "1px solid rgba(0,0,0,.06)" },
  cardHeader: { padding: "14px 20px", borderBottom: "1px solid #F3F4F6", display: "flex", alignItems: "center", justifyContent: "space-between" },
  cardTitle:  { fontSize: 14, fontWeight: 600, color: "#111827" },
  newTag:     { fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 10, background: "#DBEAFE", color: "#1E40AF", border: "1px solid #BFDBFE" },
  notice:     { background: "#EFF8FF", border: "1px solid #BFDBFE", borderRadius: 10, padding: "12px 16px", fontSize: 13, color: "#1E40AF", marginBottom: 20 },
};
