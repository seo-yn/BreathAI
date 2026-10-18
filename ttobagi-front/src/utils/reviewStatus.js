// 백엔드 ReviewStatus는 PENDING / ACCEPTED / REJECTED / APPLIED 4종이다.
// 반영까지 끝난 후보는 APPLIED로 내려오므로 승인 계열을 함께 묶어 판단한다.
export function isAccepted(reviewStatus) {
  return reviewStatus === "ACCEPTED" || reviewStatus === "APPLIED";
}
