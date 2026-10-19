import { useEffect, useState } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, Legend, ResponsiveContainer, CartesianGrid } from "recharts";
import { dashApi } from "../api/dashboard.js";
import { useToast } from "../hooks/useToast.js";

export default function DashboardPage() {
  const [analyze, setAnalyze]   = useState(null);
  const [loading, setLoading]   = useState(true);
  const toast = useToast();

  useEffect(() => {
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

  // 날짜별 미답변·오답변 건수. 명세서 AnalyzeResultResponse.trend 를 쓴다.
  // 백엔드는 usageStats(정답·전체 건수 포함)도 함께 내려주지만 명세서에 없는 필드라 쓰지 않는다.
  const trend = analyze?.trend || [];

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
          { label: "총 대화 수",      value: (system?.totalLogCount || 0).toLocaleString(),
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

      {/* 날짜별 미답변·오답변 — 명세서 trend */}
      {trend.length > 0 && (
        <div style={{ ...S.card, marginBottom: 20 }}>
          <div style={S.cardHeader}>
            <span style={S.cardTitle}>날짜별 미답변·오답변</span>
            <span style={{ fontSize: 11, color: "#9CA3AF" }}>{trend.length}일</span>
          </div>
          <div style={{ padding: 20, height: 260 }}>
            <ResponsiveContainer>
              <BarChart data={trend} margin={{ top: 4, right: 8, left: -12, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                <XAxis dataKey="date" tick={{ fontSize: 10, fill: "#9CA3AF" }} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: "#9CA3AF" }} tickLine={false} axisLine={false} />
                <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8, border: "1px solid #E5E7EB" }} />
                <Legend wrapperStyle={{ fontSize: 11 }} />
                <Bar dataKey="lowQualityCount" name="오답변" stackId="a" fill="#7C3AED" />
                <Bar dataKey="unanswerCount"   name="미답변" stackId="a" fill="#EF9F27" radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* 급증 키워드 */}
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
  card:       { background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,.08)", border: "1px solid rgba(0,0,0,.06)" },
  cardHeader: { padding: "14px 20px", borderBottom: "1px solid #F3F4F6", display: "flex", alignItems: "center", justifyContent: "space-between" },
  cardTitle:  { fontSize: 14, fontWeight: 600, color: "#111827" },
  newTag:     { fontSize: 10, fontWeight: 700, padding: "2px 8px", borderRadius: 10, background: "#DBEAFE", color: "#1E40AF", border: "1px solid #BFDBFE" },
  notice:     { background: "#EFF8FF", border: "1px solid #BFDBFE", borderRadius: 10, padding: "12px 16px", fontSize: 13, color: "#1E40AF", marginBottom: 20 },
};
