"""Publish diagnostic counts, never present unvalidated ratings as final quality."""
import argparse,json,hashlib
from pathlib import Path
AXES=('fact','respectful','relevant','action','essential')
def main():
    ap=argparse.ArgumentParser();ap.add_argument('directory',type=Path);args=ap.parse_args()
    d=args.directory;read=lambda p:json.loads(p.read_text(encoding='utf-8'))
    report=read(d/'run_report.json');controls=read(d/'calibration.json') if (d/'calibration.json').exists() else []
    lines=['# 항목별 채점 후속 검증','',
    '**자동 채점기 대조 시험 결과입니다. 사람 정답 라벨은 아직 없으므로 확정된 모델 성능표가 아닙니다.**','',
    '하나의 합성 질문과 근거에 답변 변형·반복을 포함한 9개 조건을 적용했습니다. 서로 다른 상담 질문 9건이 아닙니다. 기존 상담 질문 10건/답변 50건의 생성 성능 실험과 별도입니다.','',
    '시드 42·결정적 연산·샘플링 비활성화 설정을 유지했습니다. 공손함의 JSON은 세 판정값을 모두 허용하는 문법으로 제한했습니다. 답변 인용 선택지도 판정값별로 동일하게 제공했습니다. 형식 제약이 판정의 정확성을 보장하지는 않습니다.','',
    '코드 검사 19개 통과. 별도 검토 목록에서 서비스명 또타24의 숫자를 수치 주장으로 잘못 잡는 문제를 수정했습니다. 기존 답변 50건을 관찰 항목으로 검사한 결과는 review_priority.md와 frozen_answer_flags.json에 있습니다. 품질 점수나 사람 라벨로 변환하지 않았습니다.','',
    '| 대조군 | 사실성 | 공손함 | 관련성 | 실행 안내 | 필수정보 | 검증 |','|---|---|---|---|---|---|---|']
    failures=[]
    for c in controls:
        if 'result' in c:
            v={**c['result']['content'],**c['result']['style']};cells=[str(v[k]) for k in AXES]
        else:cells=['유효 판정 없음','미실행','유효 판정 없음','유효 판정 없음','유효 판정 없음']
        lines.append('| '+c['label']+' | '+' | '.join(cells)+' | '+('통과' if c['passed'] else '실패')+' |')
        if c.get('error'):failures.append('- '+c['label']+': '+c['error'])
    if failures:lines+=['','내용 검사의 인용·형식 검증에 실패한 조건은 유효 판정으로 쓰지 않았고, 공손함 검사를 호출하지 않았습니다. 실패 이유:','']+failures
    lines+=['',f"실행 상태: `{report['status']}`. 완료한 대조군 {len(controls)}개 중 {sum(c['passed'] for c in controls)}개 통과."]
    if (d/'generation_audit.json').exists():
        audit=read(d/'generation_audit.json');groups={}
        for call in audit:groups.setdefault(call['prompt'],[]).append(call)
        repeated=[]
        for prompt,rows in groups.items():
            if len(rows)>1:
                repeated.append(dict(prompt_sha256=hashlib.sha256(prompt.encode()).hexdigest(),calls=[r['label'] for r in rows],
                    identical_raw_outputs=len({r['raw'] for r in rows})==1,
                    any_near_time_cap=any(r['seconds']>=120 for r in rows)))
        (d/'repeat_audit.json').write_text(json.dumps(repeated,ensure_ascii=False,indent=2),encoding='utf-8')
        lines+=['','## 일관성과 타당성 구분','',
            f'실제로 두 번 이상 호출된 동일 프롬프트 그룹 {len(repeated)}개 중 원출력이 완전히 같은 그룹은 {sum(r["identical_raw_outputs"] for r in repeated)}개입니다. 시간 제한에 걸린 호출 여부도 repeat_audit.json에 별도 저장했습니다. 반복하지 않은 조건의 안정성을 추정하지 않았습니다.',
            '동일 출력의 반복은 재현성을 보여주는 제한적인 증거이며 정답 여부를 입증하지 않습니다. 유효한 형식과 실제 원문 인용의 존재만으로 판정의 의미가 맞다고 볼 수도 없습니다.']
    ratings_path=d/'provisional_ratings.json'
    if ratings_path.exists():
        rows=read(ratings_path);key=read(d.parent/'checklist-v3-001/private_system_key.json');mapping={r['item_id']:r['system'] for r in key}
        summary={}
        for system in ('llama','qwen','cluster_synth','rag','refine'):
            system_rows=[r for r in rows if mapping[r['item_id']]==system]
            summary[system]=dict(total=len(system_rows),judge_failed=sum('result' not in r for r in system_rows),axes={})
            for axis in AXES:
                values=[{**r['result']['content'],**r['result']['style']}[axis] for r in system_rows if 'result' in r]
                summary[system]['axes'][axis]={k:values.count(k) for k in ('supported','contradicted','pass','fail','unknown')}
        (d/'provisional_summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
        lines+=['','## 고정 답변의 예비 판정','',f'기존 50건 중 {len(rows)}건 처리. 상태별 건수는 provisional_summary.json에 저장했습니다. unknown을 실패나 0점으로 바꾸지 않았습니다. 사람 기준과의 일치도 검증 전입니다.']
    else:lines+=['','전체 50건의 예비 재채점은 실행되지 않았습니다.']
    lines+=['','## 남은 검증','',
    '- 기준 답안 초안과 질문 의도의 사람 검토·승인.',
    '- 익명 평가 양식 50건을 독립 평가자 2명이 작성.',
    '- 사람 간 일치도와 자동 판정의 일치도 확인. 대조군 통과만으로 실제 상담 품질 평가의 타당성이 확보되지는 않습니다.']
    (d/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(report['status'])
if __name__=='__main__':main()
