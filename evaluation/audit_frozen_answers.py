"""Observable review flags, not a quality score or substitute human labels."""
import json,re
from pathlib import Path
from evaluation.stable_judge import numbers,canonical_input,cache_key
ROOT=Path(__file__).resolve().parents[1]
def main():
    src=ROOT/'evaluation/results/presentation-10-001';out=ROOT/'evaluation/results/checklist-v3-004'
    read=lambda p:json.loads(p.read_text(encoding='utf-8'))
    cases={c['id']:c for c in read(src/'cases.json')};records=read(src/'raw_records.json');rows=[];groups={}
    for r in records:
        c=cases[r['test_id']];answer=r['answer'];docs=[dict(id=d['chunk_id'],text=d['text']) for d in c['retrieved']]
        key=cache_key(canonical_input(r['standard_question'],docs,answer),'frozen-audit','frozen-audit')
        groups.setdefault(key,[]).append((r['test_id'],r['system']))
        refs='\n'.join(d['text'] for d in docs)
        rows.append(dict(test_id=r['test_id'],system=r['system'],answer=answer,
            numbers_not_in_evaluation_top5=sorted(numbers(answer)-numbers(refs)),
            placeholders=re.findall(r'\[(?:연락처번호|운영시간|전화번호|링크|URL)\]',answer),
            prompt_instruction_fragments=[s for s in ('예비실험 길이 제한','답변 본문만 출력','JSON 하나만 출력') if s in answer],
            honorific_surface_matches=re.findall(r'[^.!?\n]{0,12}(?:습니다|세요|드립니다|해요|입니다|바랍니다)',answer),
            generator_evidence_count=len(c.get('used_evidence',[])) if r['system'] in ('rag','refine') else None,
            human_quality_label=None))
    (out/'frozen_answer_flags.json').write_text(json.dumps(dict(rows=rows,equal_input_groups=[v for v in groups.values() if len(v)>1],
        interpretation='Numbers absent from supplied references are unverified, not proven false; honorific matches are linguistic surface observations, not a politeness score.'),ensure_ascii=False,indent=2),encoding='utf-8')
    lines=['# 고정 답변 검토 우선순위','', '자동 채점 결과와 독립적인 원문 관찰입니다. 숫자가 근거에 없다고 거짓을 확정하지 않으며 존댓말 표면형이 있다고 공손함을 확정하지 않습니다. 사람 품질 라벨은 입력하지 않았습니다. 서비스명 또타24의 숫자는 수치 주장으로 세지 않습니다.','',
    '| 사례 | 조건 | 평가 근거에 없는 수치 | 자리표시자 | 지시문 잔존 | 생성 시 근거 수 |','|---|---|---|---|---|---:|']
    for r in rows:
        if r['numbers_not_in_evaluation_top5'] or r['placeholders'] or r['prompt_instruction_fragments']:
            lines.append('| '+r['test_id']+' | '+r['system']+' | '+', '.join(r['numbers_not_in_evaluation_top5'])+' | '+', '.join(r['placeholders'])+' | '+', '.join(r['prompt_instruction_fragments'])+' | '+str(r['generator_evidence_count'] if r['generator_evidence_count'] is not None else '—')+' |')
    lines+=['','50개 답변을 모두 검사했습니다. 표에는 수치·자리표시자·지시문 관찰 플래그가 있는 답변만 표시했습니다. 전체 기록은 frozen_answer_flags.json에 있습니다. 생성 시 RAG에 실제 사용된 근거 수와 평가용 Top-5는 서로 다릅니다.']
    (out/'review_priority.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('Audited',len(rows),'frozen answers; no human labels or quality scores created.')
if __name__=='__main__':main()
