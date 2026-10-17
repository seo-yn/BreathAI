# 항목별 평가 v3

이번 경로는 기존 답변 50건을 고정해 평가 방식을 검증하는 실험이다. 운영 Refine은
변경하지 않았다. 생성 질문을 제안 질문으로 바꿔서 기존 답변을 채점하지 않는다.
검색 임계값 0.05도 그대로다.

## 기준과 결과

- 사실성: supported(제공 근거로 확인), contradicted(근거와 반대), unknown(근거 부족).
- 공손함: 존댓말이고 모욕이 없는지. 사실성과 독립적으로 답변만 보고 판정한다.
- 관련성: 원래 생성 질문에 답하는지. 질문 의도가 불명확하면 unknown.
- 실행 안내: 질문에 필요한 근거 있는 행동 또는 설명이 있는지.
- 필수정보: 사례별 필수 항목 초안의 충족 여부. 항목이 없으면 unknown.
- supported/contradicted는 답변·문서의 실제 인용을 요구한다. 인용 존재가 논리적
  함의까지 입증하지는 않으므로 사람 검토가 필요하다.

pass=5, fail=1의 endpoint_scores는 체크리스트의 선택적 표현이며 과거 1~5점
Judge와 비교하면 안 된다. unknown은 null이다. 주된 보고는 상태별 건수·판정 가능
비율이고, 사람 검증 전 성능 우열 표는 발행하지 않는다.

## 재현성과 대조 시험

Python/NumPy/PyTorch 시드 42, 각 호출 전 재설정, eval, do_sample=False,
deterministic_algorithms=True, cuDNN deterministic=True/benchmark=False,
CUBLAS_WORKSPACE_CONFIG=:4096:8, TF32 비활성화, eager attention을 사용한다.
결정적 연산을 지원하지 않으면 오류를 기록하며 설정을 몰래 완화하지 않는다.
같은 소프트웨어·하드웨어 내 재현성 설정이지 환경 간 동일 출력 보장은 아니다.

정답·근거 없는 번호·반대 주장·무관 답변·무례한 정답 및 원문 3회·줄바꿈·동의어
대조군을 실제 모델로 검사한다. 모두 캐시 없이 호출한다. 내용 검사 형식 오류만 최대 한 번
수정 요청하며 판정 실패는 답을 알려 재시도하지 않는다. 공손함은 JSON 필드명과
값의 오타를 막기 위해 finite grammar로 디코딩한다. pass/fail/unknown 모두 같은
실제 답변 인용 선택지를 갖고 있으며 점수의 정답을 강제하지 않는다. 이 문법은
판정의 의미적 타당성을 입증하지 않는다. 부분 문자열 검증과
축별 기대 판정, 반복 일치를 검사한다. 하나라도 실패하면 50건 자동 채점은 막는다.
모든 대조군이 통과해도 사람과의 일치도는 별도이며 provisional만 생성한다.
실험 예산 1시간, 호출 120초/192토큰 제한. 원본 결과를 삭제하지 않는다.

## 사람 평가

`evaluation/results/checklist-v3-001/question_review.md` 및 `reference_drafts.json`은
assistant 초안이다. 사람 검토·승인 상태는 pending이며 정답으로 확정하지 않았다.
원본 로그가 빈약한 경우 추가 원문 확인이 필요하다. 날짜가 있는 실제 운영 정책의
최신성은 검증하지 않았으며 제공 데이터셋에 대한 평가다.

`human_review.html`을 두 평가자가 독립 작성하고 JSON을 내려받는다. 모델명을
감춘 무작위 50개 답변이다. `private_system_key.json`은 완료 전 열지 않는다.
자동 저장은 브라우저 로컬 저장소의 평가자 ID별로 분리된다. 다른 평가자의 결과를
보지 않고 독립 작성한다. unknown과 미평가를 혼동하지 않는다. 판단에는 인용
또는 이유를 기록한다. 불일치는 토론 후 별도로 합의하고 원본 평가를 보존한다.

```powershell
python -m evaluation.prepare_checklist_review
python -m unittest tests.test_checklist_judge tests.test_stable_judge tests.test_evaluation
python -m evaluation.run_checklist_judge
python -m evaluation.compare_human_reviews reviewer_a.json reviewer_b.json
```

비교기는 판단 가능한 항목의 일치율과 전체 대비 판단 가능 비율, 축별 Cohen kappa,
불일치 목록을 계산한다. 라벨이 없거나 변이가 없는 경우 통계를 꾸며내지 않는다.
합의된 항목에 한해 자동 판정과의 일치도도 별도 계산한다. 사례 10개/답변 50개는
독립적인 질문 50개가 아니며 통계적 일반화에 한계가 있다.
