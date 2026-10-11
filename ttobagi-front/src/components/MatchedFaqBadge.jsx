// FAQ 후보가 기존 챗봇 학습데이터(bronze_counselling_info)의 어떤 질문-답변 쌍과
// 겹치는지 보여주는 뱃지. 백엔드가 콜백으로 받아 FaqCandidateMatch에 저장한
// matchedFaqSeqNum / matchScore를 그대로 쓴다.
//
// 후보 타입(candidateType)이 EXPAND면 "기존 FAQ의 키워드 확장"이라는 뜻이므로,
// 승인 시 신규 FAQ가 아니라 기존 항목을 갱신해야 한다는 점을 검토자에게 알린다.
// (현재 백엔드 applyFaqCandidate는 타입을 보지 않고 항상 새 행을 INSERT한다 —
//  docs/backend-api-requests.md 참고)
export default function MatchedFaqBadge({ matches, candidateType }) {
  const list = matches || [];
  if (candidateType !== "EXPAND" && list.length === 0) return null;

  const topScore = list.reduce((max, m) => Math.max(max, m.matchScore ?? 0), 0);
  const isExpand = candidateType === "EXPAND";

  return (
    <span
      title={
        list.length
          ? `기존 학습데이터 seq_num: ${list.map((m) => m.matchedFaqSeqNum).join(", ")}`
          : "기존 FAQ 키워드 확장 후보"
      }
      style={{
        fontSize: 10,
        fontWeight: 600,
        padding: "2px 8px",
        borderRadius: 10,
        background: isExpand ? "#EDE9FE" : "#FEF3C7",
        color: isExpand ? "#5B21B6" : "#92400E",
        border: `1px solid ${isExpand ? "#DDD6FE" : "#FDE68A"}`,
        cursor: "help",
      }}
    >
      {isExpand ? "기존 FAQ 확장" : "기존 FAQ 유사"}
      {list.length > 0 && ` ${list.length}건`}
      {topScore > 0 && ` · 최대 ${(topScore * 100).toFixed(0)}%`}
    </span>
  );
}
