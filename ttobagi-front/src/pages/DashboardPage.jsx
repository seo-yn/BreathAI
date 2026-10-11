import { useEffect, useState } from "react";
import { dashApi } from "../api/dashboard.js";
import { useToast } from "../hooks/useToast.js";

export default function DashboardPage() {
  const [usage, setUsage]       = useState(null);
  const [analyze, setAnalyze]   = useState(null);
  const [loading, setLoading]   = useState(true);
  const toast = useToast();

  useEffect(() => {
    const now = new Date();
    const year = now.getFullYear();
    const month = now.getMonth() + 1;

    Promise.all([
      dashApi.getUsage(year, month),
      dashApi.getAnalyzeResult(),
    ]).then(([usageRes, analyzeRes]) => {
      if (usageRes.data)   setUsage(usageRes.data);
      if (analyzeRes.data) setAnalyze(analyzeRes.data);
    }).catch((err) => {
      toast.error(err.message || "대시보드 데이터를 불러오지 못했습니다.");
    }).finally(() => setLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) return <div style={{ padding: 40, color: "#6B7280" }}>불러오는 중...</div>;

  const system    = analyze?.systemStatus;
  const sampling  = system?.samplingStatus;
  const total     = system?.totalLogCount || 1;
  const perf      = analyze?.evaluation;
  const reasons   = analyze?.unansweredAnalysis?.byReason || [];

  return (
    <div style={S.page}>
      <div style={S.pageTitle}>대시보드</div>
      <div style={S.pageSub}>또타24 챗봇 응답 현황을 확인합니다.</div>

      {/* 통계 카드 */}
      <div style={S.statGrid}>
        {[
          { label: "총 대화 수",      value: (usage?.totalUsageCount || 0).toLocaleString(),                            sub: `전월 대비 ${usage?.increaseRate > 0 ? "+" : ""}${usage?.increaseRate || 0}%`, color: "#111827" },
          { label: "정답 (≥75%)",     value: `${sampling ? ((sampling.correct / total) * 100).toFixed(1) : 0}%`,          sub: `${(sampling?.correct || 0).toLocaleString()}건`,    color: "#1565C0" },
          { label: "오답변 (25~75%)", value: `${sampling ? ((sampling.lowQuality / total) * 100).toFixed(1) : 0}%`,       sub: `${(sampling?.lowQuality || 0).toLocaleString()}건`, color: "#7C3AED" },
          { label: "미답변 (<25%)",   value: `${sampling ? ((sampling.unanswered / total) * 100).toFixed(1) : 0}%`,       sub: `${(sampling?.unanswered || 0).toLocaleString()}건`, color: "#EF9F27" },
        ].map((s) => (
          <div key={s.label} style={S.statCard}>
            <div style={S.statLabel}>{s.label}</div>
            <div style={{ ...S.statValue, color: s.color }}>{s.value}</div>
            <div style={S.statSub}>{s.sub}</div>
          </div>
        ))}
      </div>

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
                    width: `${Math.min((r.count / (sampling?.unanswered || 1)) * 100, 100)}%`,
                    height: "100%", borderRadius: 3, background: "#1976D2",
                  }} />
                </div>
              </div>
            ))}
          </div>
        </div>

        <div style={S.card}>
          <div style={S.cardHeader}><span style={S.cardTitle}>개선 전·후 성능 비교</span></div>
          <div style={{ padding: 20, display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 16 }}>
            {perf ? [
              { label: "예상 미답변율",  value: `${perf.afterUnanswerRate}%`,          color: "#43A047" },
              { label: "정확도 향상",    value: `+${perf.accuracyGain}%`,              color: "#1565C0" },
              { label: "오답 발생률",    value: `${perf.falsePositiveRate}`,            color: "#EF9F27" },
              { label: "AI 해결 건수",   value: `${perf.resolvedCountByAI.toLocaleString()}건`, color: "#43A047" },
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
};
