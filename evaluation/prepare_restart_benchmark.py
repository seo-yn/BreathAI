"""Freeze a source-linked ten-topic answer-quality benchmark before generation."""
import csv,hashlib,json,random,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'evaluation/results/restart-10-20261014'
SPECS=[
('lost',5,566,'지하철에 가방을 두고 내렸는데 어떻게 찾을 수 있나요?'),
('seat_heat',7,2,'열차 좌석 열선이 너무 뜨거운데 어디에 어떻게 신고하나요?'),
('express',9,112,'급행열차가 운행하는 노선과 이용 전에 확인할 사항을 알려주세요.'),
('odor',10,336,'열차 안에서 불쾌한 냄새가 심하게 나면 어떻게 신고하나요?'),
('religion',13,576,'열차 안에서 선교나 종교 권유 활동을 해도 되나요?'),
('doors',21,320,'승강장 안전문이 닫힐 때 물체가 끼면 어떤 안전장치가 작동하나요?'),
('vendors',22,261,'열차 안에서 이동상인이 물건을 파는데 어떻게 대처하고 신고하나요?'),
('noise',24,3,'열차 안에서 큰 소리로 떠드는 사람이 있으면 어떻게 신고하나요?'),
('air',38,438,'전동차 객실 공기질을 개선하기 위해 어떤 관리를 하고 있나요?'),
('sms',63,270,'고객센터에 문자를 보냈는데 답변이 없으면 무엇을 확인해야 하나요?')]
def masked(text):
    text=re.sub(r'(?:https?://|www\.)\S+','[URL]',text)
    text=re.sub(r'\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b','[EMAIL]',text)
    text=re.sub(r'\b01[016789][ -]?\d{3,4}[ -]?\d{4}\b','[TEL]',text)
    return text
def main():
    OUT.mkdir(parents=True,exist_ok=True)
    kbpath=ROOT/'tmp/evaluation-assets/kb_snapshot.json';kb=json.loads(kbpath.read_text(encoding='utf-8'));byid={d['chunk_id']:d for d in kb}
    keywordpath=ROOT/'dataset/cluster_keywords.json';keywords=json.loads(keywordpath.read_text(encoding='utf-8'))
    logpath=ROOT/'dataset/clusters.csv'
    with logpath.open(encoding='utf-8-sig',newline='') as f:logs=list(csv.DictReader(f))
    cases=[]
    for index,(topic,cluster,num,question) in enumerate(SPECS,1):
        ref=byid[f'general_consultation:{num}'];rows=[(i,r) for i,r in enumerate(logs) if int(r['clusterId'])==cluster]
        random.Random(42+cluster).shuffle(rows)
        # Separate context and withheld source logs; no training is performed.
        context=rows[:min(5,len(rows)-1)];heldout=rows[len(context):]
        if not context or not heldout:raise ValueError('Insufficient cluster data')
        answer=ref['answer']
        if topic=='religion':
            needle='불법 부착물 부착, 불법 상행위, 구걸행위, 선교활동은 금지되어 있습니다.'
            assert needle in answer;answer=needle
        cases.append(dict(id=f'T{index:02d}',topic=topic,cluster_id=cluster,question=question,
            context_logs=[masked(r['text']) for i,r in context],context_row_indices=[i for i,r in context],
            heldout_logs=[masked(r['text']) for i,r in heldout],heldout_row_indices=[i for i,r in heldout],
            keywords=keywords[str(cluster)],reference_id=ref['chunk_id'],reference_question=ref['question'],
            reference_answer=answer,reference_source_text=ref['text'],reference_status='dataset-backed benchmark reference; not independent human grading'))
    payload=dict(cases=cases,n=10,selection='predeclared 10 distinct FAQ topics linked to unanswered-log clusters; purposive, not representative random sampling',
        context_split_seed=42,model_generation_seed=42,judge_seed=42,context_shuffle='Random(42 + cluster_id)',
        question_policy='fixed intent-preserving benchmark questions supplied identically to all systems; question synthesis quality is excluded',
        source_hashes={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (kbpath,keywordpath,logpath)},
        no_training=True,reference_faq_available_to_rag=True,independent_human_labels=False)
    (OUT/'benchmark.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf-8')
    print('FROZEN',len(cases),'topics; disjoint context/withheld row indices; same question per condition.')
if __name__=='__main__':main()
