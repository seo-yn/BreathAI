import hashlib,json,random,shutil,time
from pathlib import Path
from evaluation.restart_llama_runtime import LocalModel
from evaluation.restart_glider import prompt,parse
ROOT=Path(__file__).resolve().parents[1];BASE=ROOT/'evaluation/results/restart-10-20261014';OUT=BASE/'glider'
def main():
    read=lambda p:json.loads(p.read_text(encoding='utf-8'))
    def dump(name,value):(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    for name in ('benchmark.json','retrieval_report.json','retrieval.json','generation_audit.json','restart_rubrics.json'):shutil.copy2(BASE/name,OUT/name)
    bench=read(OUT/'benchmark.json');retrieval={r['id']:r for r in read(OUT/'retrieval.json')};rubrics=read(OUT/'restart_rubrics.json');manifest=read(OUT/'runtime_manifest.json')
    rows=[{k:v for k,v in r.items() if k!='judge'} for r in read(BASE/'raw_records.json')]
    assert len(rows)==50
    audit=read(OUT/'judge_audit.json') if (OUT/'judge_audit.json').exists() else []
    report=dict(status='running',n=10,final_judge='PatronusAI/glider (Phi-3.5 evaluation fine-tune)',judge_precision='Q4_K_M',
        old_judge_used=False,generator_precision='FP16, not quantized',independent_human_labels=False,
        rejected_candidate='M-Prometheus-7B: correct/rude accuracy and layout controls failed',
        source_answer_file_sha256=hashlib.sha256((BASE/'generated_answers.json').read_bytes()).hexdigest(),
        generated_count=50,judge_seed=42,parameters=dict(seed=42,temperature=0,top_k=1,top_p=1,min_p=0,repeat_penalty=1,max_judge_tokens=1024),
        prompt_format='publisher GLIDER user-only XML tags, one criterion per call',politeness_context='MODEL OUTPUT only; no factual reference')
    (OUT/'experiment_plan.md').write_text((BASE/'experiment_plan.md').read_text(encoding='utf-8')+'\n\n## Judge 후보 변경\nM-Prometheus는 정답·무례한 정답의 사실성 및 레이아웃 검증에 실패하여 최종 Judge로 채택하지 않았습니다. 두 번째 후보는 공식 PatronusAI/glider(Phi-3.5 계열)의 Q4_K_M입니다. 1~5점 rubric과 50개 생성 답변은 그대로 고정하고 공식 XML 평가 형식으로 대조 시험과 전체 채점을 수행합니다. 공손함에는 답변만 제공합니다. 다른 축에는 같은 질문·참조·검색 근거를 제공합니다. 실패 결과와 점수를 보정 없이 기록하고 사람 검증 여부를 별도로 표시합니다.\n',encoding='utf-8')
    dump('run_report.json',report);start=time.monotonic()
    def score(server,cid,q,answer,reference,context,axis,label):
        p=prompt(q,answer,reference,context,rubrics[axis],axis);value=server.complete(p,None,1024)
        entry=dict(case_id=cid,axis=axis,label=label,**value)
        try:entry.update(score=parse(value['text']),status='ok')
        except ValueError as exc:entry.update(score=None,status='parse_failed',error=str(exc))
        audit.append(entry);dump('judge_audit.json',audit)
        print('GLIDER',label,axis,entry['score'],value['finish_reason'],round(value['seconds'],1),flush=True);return entry
    with LocalModel(manifest,'judge',OUT) as server:
        q='열차 안에서 큰 소리로 통화하는 사람이 있으면 어떻게 신고하나요?';ref='열차 내 소란은 또타 앱 또는 1577-1234로 신고할 수 있습니다. 일반적인 대화는 제재 대상이 아닙니다.'
        normal='열차 내 소란은 또타 앱 또는 1577-1234로 신고해 주세요. 일반적인 대화만으로는 제재 대상이 되기 어렵습니다.'
        controls=[('correct',normal),('wrong_polite','고객센터 1350으로 신고해 주세요. 일반적인 대화도 모두 금지됩니다.'),('correct_rude','멍청아, 열차 내 소란은 또타 앱 또는 1577-1234로 신고해. 일반적인 대화는 제재 대상이 아니야.'),('repeat_uncached',normal),('layout','열차 내 소란은\n또타 앱 또는 1577-1234로  신고해 주세요. 일반적인 대화만으로는 제재 대상이 되기 어렵습니다.')]
        cal=[]
        for name,answer in controls:
            values={axis:score(server,'control',q,answer,ref,ref,axis,'control/'+name)['score'] for axis in rubrics}
            cal.append(dict(name=name,scores=values));dump('judge_calibration.json',cal)
        by={r['name']:r['scores'] for r in cal};checks=dict(all_parsed=all(v is not None for r in cal for v in r['scores'].values()))
        if checks['all_parsed']:checks.update(correct_accuracy=by['correct']['accuracy']>=4,correct_politeness=by['correct']['politeness']>=4,
            wrong_accuracy=by['wrong_polite']['accuracy']<=2,wrong_politeness=by['wrong_polite']['politeness']>=4,
            rude_accuracy=by['correct_rude']['accuracy']>=4,rude_politeness=by['correct_rude']['politeness']<=2,
            repeat_exact=by['correct']==by['repeat_uncached'],layout_exact=by['correct']==by['layout'])
        report.update(judge_calibration_checks=checks,judge_calibration_passed=all(checks.values()));dump('run_report.json',report)
        order=list(rows);random.Random(42).shuffle(order)
        for record in order:
            case=next(c for c in bench['cases'] if c['id']==record['test_id']);context='\n\n'.join('['+d['chunk_id']+'] '+d['text'][:1200] for d in retrieval[case['id']]['used_evidence'])
            result={}
            for axis in rubrics:
                previous=next((a for a in audit if a['case_id']==case['id'] and a['label']==record['system'] and a['axis']==axis),None)
                result[axis]=(previous or score(server,case['id'],case['question'],record['answer'],case['reference_answer'],context,axis,record['system']))['score']
            record['judge']=result;dump('raw_records.json',rows)
            report.update(judged_records=sum('judge' in r for r in rows),elapsed_seconds=time.monotonic()-start);dump('run_report.json',report)
    report.update(status='complete',completed_records=50,judge_parse_failures=sum(a['status']!='ok' for a in audit));dump('run_report.json',report);dump('raw_records.json',rows)
    print('GLIDER_DONE',flush=True)
if __name__=='__main__':main()
