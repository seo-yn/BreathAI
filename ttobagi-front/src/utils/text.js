// 운영 FAQ 답변에는 <p>, <br> 같은 HTML이 섞여 있다 (또타24 원본 데이터가 HTML로 저장돼 있음).
// 목록·카드처럼 미리보기로 보여주는 자리에서는 태그를 그대로 노출하면 안 되고,
// dangerouslySetInnerHTML로 실행하면 AI 생성 답변까지 HTML로 해석돼 XSS 위험이 생긴다.
// 그래서 표시용으로만 태그를 떼고 평문으로 바꾼다. 편집 화면에서는 원문을 그대로 쓴다.
export function toPlainText(value) {
  if (!value) return "";
  return String(value)
    .replace(/<br\s*\/?>/gi, "\n")      // 줄바꿈 태그는 개행으로
    .replace(/<\/(p|div|li|tr)>/gi, "\n") // 블록 종료도 개행으로
    .replace(/<[^>]+>/g, "")             // 나머지 태그 제거
    .replace(/&nbsp;/gi, " ")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;/g, "'")
    .replace(/\n{3,}/g, "\n\n")          // 과도한 빈 줄 정리
    .trim();
}
