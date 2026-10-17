"""Build blinded local HTML and draft references, without inventing human labels."""
import hashlib,html,json,random
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evaluation/results/checklist-v3-001'
DRAFTS={
'C5':('지하철에 가방을 두고 내렸을 때 어떻게 찾나요?','의도 혼합: 본인 분실과 타인 유실물 발견을 구분해야 함',['general_consultation:566'],['분실한 열차 번호·칸을 아는 경우 콜센터 또는 가까운 역에 연락','모르거나 시간이 지난 경우 유실물찾기 확인']),
'C7':('열차 좌석 열선이 너무 뜨거운데 조절할 수 있나요?','열선 민원이 일반 전원 확인으로 변형됨; 검색 근거에 열선 직접 안내 없음',[],[]),
'C9':('어느 노선의 급행 운행 또는 열차 속도가 궁금하신가요?','원본이 단어 위주라 의도 불명확; 노선·구간 확인 필요',['general_consultation:112'],[]),
'C10':('역사에서 악취가 심한데 어떻게 신고하나요?','역사 민원에 객실 근거가 검색됨; 위치 차이 검토 필요',['general_consultation:336','general_consultation:315'],['악취 불편 접수 방법 안내']),
'C13':('열차 안에서 종교 권유나 설교를 하는 경우 어떻게 하나요?','질문이 답변형 단정 문장으로 바뀜',['general_consultation:576'],['선교활동 금지 안내']),
'C21':('승객이 승하차 중인데 출입문을 닫고 출발하면 어떻게 하나요?','생성문이 질문이 아니며 위험한 문 개방 단정; 안전 안내와 불편 접수 근거 재검토 필요',['general_consultation:576','general_consultation:320'],['닫히는 출입문에 무리하게 승차하지 않도록 안내']),
'C22':('열차 내 이동상인을 발견하면 어떻게 신고하나요?','신고 의도가 상인 위치 질문으로 변형됨',['general_consultation:261'],['또타 앱 또는 1577-1234 신고 안내','물품 구매·기부 요청에 응하지 않도록 안내']),
'C24':('열차 안에서 큰 소리로 떠드는 사람이 있으면 어떻게 신고하나요?','민원 해결 의도가 존재 확인 요청으로 축소됨',['general_consultation:3'],['또타 앱 또는 1577-1234 신고 안내','일반 대화와 지속적 고성 등 소란의 구분']),
'C31':('열차 안에서 시끄러운 통화로 불편할 때 어떻게 신고하나요?','원본 의도 대체로 유지; 구체 상황·소란 수준 확인 필요',['general_consultation:3'],['또타 앱 또는 1577-1234 신고 안내','일반 대화와 지속적 고성 등 소란의 구분']),
'C38':('열차 또는 역사의 환기를 요청하려면 어떻게 하나요?','원본에 없는 연기 상황이 질문에 추가됨; 위치 확인 필요',['general_consultation:359','general_consultation:438'],['환기 관리 설명 또는 관련 불편 접수 안내'])}

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    src=ROOT/'evaluation/results/presentation-10-001'
    cases=json.loads((src/'cases.json').read_text(encoding='utf-8'))
    records=json.loads((src/'raw_records.json').read_text(encoding='utf-8'))
    drafts=[]
    for c in cases:
        question,note,ids,required=DRAFTS[c['id']]
        ev=[dict(id=d['chunk_id'],text=d['text']) for d in c['retrieved'] if d['chunk_id'] in ids]
        drafts.append(dict(id=c['id'],original_question=c['standardQuestion'],proposed_question=question,
            samples=c['samples'],review_note=note,required=required,evidence=ev,
            reference_answer_draft=' / '.join(required) if required else '근거·상황 부족: 구체 상황 확인이 먼저 필요합니다.',
            approved=False,human_review_status='pending',author='assistant draft; not human ground truth'))
    write=lambda name,obj:(OUT/name).write_text(json.dumps(obj,ensure_ascii=False,indent=2),encoding='utf-8')
    write('reference_drafts.json',drafts)
    shuffled=list(records);random.Random(42).shuffle(shuffled)
    private=[];public=[]
    for n,r in enumerate(shuffled,1):
        item_id=f'A{n:03d}';d=next(d for d in drafts if d['id']==r['test_id'])
        private.append(dict(item_id=item_id,test_id=r['test_id'],system=r['system'],answer_sha256=hashlib.sha256(r['answer'].encode()).hexdigest()))
        public.append(dict(item_id=item_id,case_id=r['test_id'],question=r['standard_question'],answer=r['answer'],
            required_draft=d['required'],evidence=d['evidence'],review_note=d['review_note']))
    write('private_system_key.json',private);write('blind_items.json',public)
    # No model names or old scores in the public evaluation form.
    cards=[]
    fields=[('fact','事実一致','supported|contradicted|unknown'),('respectful','公손한 존댓말','pass|fail|unknown'),('relevant','질문 관련성','pass|fail|unknown'),('action','실행 안내','pass|fail|unknown'),('essential','필수정보 충족','pass|fail|unknown')]
    fields[0]=('fact','사실 일치','supported|contradicted|unknown');fields[1]=('respectful','공손한 존댓말','pass|fail|unknown')
    for p in public:
        options=''.join(f'<label>{label} <select data-key="{key}"><option value="">미평가</option>'+''.join(f'<option>{v}</option>' for v in values.split('|'))+'</select></label>' for key,label,values in fields)
        ev=''.join('<h4>'+html.escape(e['id'])+'</h4><pre>'+html.escape(e['text'])+'</pre>' for e in p['evidence']) or '<p>관련 근거 부족: 사실성 판단 불가를 허용하세요.</p>'
        cards.append(f'<section data-id="{p["item_id"]}"><h2>{p["item_id"]} · {p["case_id"]}</h2><p>질문: {html.escape(p["question"])}</p><blockquote>{html.escape(p["answer"])}</blockquote><details><summary>근거와 검토 주의사항</summary><p>{html.escape(p["review_note"])}</p><p>필수 항목 초안: {html.escape(" / ".join(p["required_draft"]))}</p>{ev}</details><div>{options}</div><label>판정 근거·답변 인용 <textarea data-key="rationale"></textarea></label></section>')
    page='''<!doctype html><html lang="ko"><meta charset="utf-8"><title>익명 상담 답변 평가</title><style>body{max-width:1000px;margin:30px auto;font:17px/1.6 sans-serif;background:#f4f6f8;color:#182330}section{background:white;padding:24px;margin:24px 0;border-radius:12px}pre{white-space:pre-wrap}label{display:block;margin:10px 0}textarea{display:block;width:95%;height:70px}select,button,input{font:inherit}blockquote{white-space:pre-wrap;background:#edf3fa;padding:16px}header{position:sticky;top:0;background:#f4f6f8;padding:12px}</style><header><h1>익명 답변 평가 · 50건</h1><p>독립 평가자 2명이 각각 작성하세요. 근거 부족은 오답과 구분해 unknown으로 표시하세요. 기준 답안은 미승인 초안이며 생성된 질문은 변경하지 않았습니다. 사실 오류와 공손함은 독립 평가합니다. 모델 키 파일은 평가 완료 전 열지 마세요.</p><input id="reviewer" placeholder="평가자 ID"><button onclick="save()">평가 JSON 내려받기</button><span id="progress"></span></header>'''+''.join(cards)+'''<script>
const sections=[...document.querySelectorAll('section')];
const collect=()=>({protocol:'checklist-v3-001',reviewer:document.querySelector('#reviewer').value,ratings:sections.map(s=>({item_id:s.dataset.id,...Object.fromEntries([...s.querySelectorAll('[data-key]')].map(e=>[e.dataset.key,e.value]))}))});
const storageKey=()=>'breathai-checklist-v3-'+document.querySelector('#reviewer').value;
function save(){const data=collect();if(!data.reviewer){alert('평가자 ID를 입력하세요.');return;}const u=URL.createObjectURL(new Blob([JSON.stringify(data,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=u;a.download='human-review-'+data.reviewer.replace(/[^a-zA-Z0-9_-]/g,'_')+'.json';a.click();URL.revokeObjectURL(u);}
document.querySelector('#reviewer').addEventListener('change',()=>{for(const s of sections)for(const e of s.querySelectorAll('[data-key]'))e.value='';try{const old=JSON.parse(localStorage.getItem(storageKey())||'null');if(old)for(const r of old.ratings){const s=sections.find(x=>x.dataset.id===r.item_id);if(s)for(const e of s.querySelectorAll('[data-key]'))e.value=r[e.dataset.key]||'';}}catch(e){}});
document.addEventListener('input',(event)=>{if(event.target.id==='reviewer')return;const d=collect();if(d.reviewer)localStorage.setItem(storageKey(),JSON.stringify(d));document.querySelector('#progress').textContent=d.ratings.filter(r=>r.fact&&r.respectful&&r.relevant&&r.action&&r.essential).length+'/50 완료';});
</script></html>'''
    (OUT/'human_review.html').write_text(page,encoding='utf-8')
    lines=['# 질문·기준 답안 검토 초안','', '모든 항목은 assistant 작성 초안이며 사람 승인 전입니다. 질문을 바꾸면 새로 생성해야 하므로 이번 고정 답변 재평가에서는 기존 질문을 유지합니다.','', '| 사례 | 질문 검토 | 제안 질문 |','|---|---|---|']
    lines += [f'| {d["id"]} | {d["review_note"]} | {d["proposed_question"]} |' for d in drafts]
    (OUT/'question_review.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('Prepared 10 draft cases and 50 blinded items; human labels pending.')
if __name__=='__main__':main()
