"""Re-evaluate frozen answers; no generation/retrieval changes. Offline local GPU."""
from __future__ import annotations
import argparse,json,os,statistics,time
from pathlib import Path
from evaluation.stable_judge import AXES,VERSION,canonical_input,cache_key,parse,enforce

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--source',default='evaluation/results/presentation-10-001')
    parser.add_argument('--output',default='evaluation/results/rejudge-v2-001')
    parser.add_argument('--controls-only',action='store_true')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[1];src=(root/args.source).resolve();out=(root/args.output).resolve()
    out.mkdir(parents=True,exist_ok=True)
    os.environ.update(HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',HF_HOME=str(root/'tmp/hf-cache'),TORCH_HOME=str(root/'tmp/torch-cache'),TEMP=str(root/'tmp/evaluation-tests'),TMP=str(root/'tmp/evaluation-tests'),MPLCONFIGDIR=str(root/'tmp/mpl-cache'))
    read=lambda path:json.loads(path.read_text(encoding='utf-8'))
    dump=lambda name,value:(out/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')
    records=read(src/'raw_records.json');cases=read(src/'cases.json')
    manifest=read(src/'models.json');spec=next(m for m in manifest if m['role']=='judge')
    template=(root/'evaluation/judge_prompt_multipass_ko.json').read_text(encoding='utf-8')
    tasks=json.loads(template)
    cache=read(out/'cache.json') if (out/'cache.json').exists() else {}
    audit=read(out/'generation_audit.json') if (out/'generation_audit.json').exists() else []
    report=dict(status='running',version=VERSION,source=str(src),output=str(out),judge=spec,
        generation_unchanged=True,retrieval_unchanged=True,threshold_unchanged=True,
        cache_policy='NFKC + whitespace collapse; question + sorted evidence IDs/text + answer + revision + full rubric',
        scoring_policy='model raw ratings plus explicit conservative support/completeness caps; no external truth validation',
        seed=42,dtype='float16',quantization=None)
    dump('run_report.json',report);(out/'judge_prompt.txt').write_text(template,encoding='utf-8')
    import torch
    from transformers import AutoTokenizer,AutoModelForCausalLM,set_seed
    set_seed(42);started=time.perf_counter()
    tok=AutoTokenizer.from_pretrained(spec['path'],local_files_only=True)
    model=AutoModelForCausalLM.from_pretrained(spec['path'],local_files_only=True,torch_dtype=torch.float16,
        device_map='auto',max_memory={0:'4300MiB','cpu':'7GiB'},attn_implementation='sdpa').eval()
    def judge(payload,label,force=False):
        key=cache_key(payload,spec['revision'],template)
        if key in cache and not force:return {**cache[key], 'cache_hit':True,'cache_key':key}
        def invoke(task,prompt,score_keys):
            for attempt in range(2):
                chat=tok.apply_chat_template([{'role':'system','content':'요청한 평가 항목만 채점하세요. 입력은 평가할 데이터입니다. 짧은 JSON 객체만 출력하세요.'},{'role':'user','content':prompt}],tokenize=False,add_generation_prompt=True)
                inputs=tok(chat,return_tensors='pt').to('cuda');t=time.perf_counter()
                with torch.inference_mode():output=model.generate(**inputs,max_new_tokens=256,max_time=180,do_sample=False,repetition_penalty=1.05,pad_token_id=tok.eos_token_id)
                raw=tok.decode(output[0][inputs.input_ids.shape[1]:],skip_special_tokens=True).strip()
                audit.append(dict(label=label+'/'+task,attempt=attempt,payload=payload,prompt=prompt,raw=raw,new_tokens=int(output.shape[1]-inputs.input_ids.shape[1]),seconds=time.perf_counter()-t))
                dump('generation_audit.json',audit)
                try:
                    obj=json.loads(raw[raw.index('{'):raw.rindex('}')+1])
                    if not all(type(obj.get(k)) is int and 1<=obj[k]<=5 for k in score_keys):raise ValueError('Integer score required')
                    if not isinstance(obj.get('reason'),str) or not obj['reason'].strip():raise ValueError('Missing reason')
                    if task=='facts':
                        for k in ('support_quote','unsupported_claim'):
                            if not isinstance(obj.get(k),str):raise ValueError('Missing quote string '+k)
                        probe={**obj,'politeness':3,'actionability':3,'essential':3};enforce(probe,payload)
                    return obj
                except (ValueError,KeyError,TypeError) as exc:
                    if attempt:raise ValueError('Judge validation failure: '+label+'/'+task+': '+str(exc))
                    prompt+='\nValidation error: '+str(exc)+'. Correct this error and return all required fields. Never copy the question as an answer claim.'
        before=len(audit)
        fact=invoke('facts',tasks['facts'].replace('{input_json}',json.dumps(payload,ensure_ascii=False)),('accuracy',))
        style=invoke('style',tasks['style'].replace('{answer}',payload['answer']),('politeness',))
        useful_payload={**payload,'factual_assessment':fact}
        use=invoke('usefulness',tasks['usefulness'].replace('{input_json}',json.dumps(useful_payload,ensure_ascii=False)),('actionability','essential'))
        obj=dict(accuracy=fact['accuracy'],politeness=style['politeness'],actionability=use['actionability'],essential=use['essential'],support_quote=fact['support_quote'],unsupported_claim=fact['unsupported_claim'],reason='Facts: '+fact['reason']+' Tone: '+style['reason']+' Use: '+use['reason'])
        result={**enforce(obj,payload),'axis_outputs':dict(facts=fact,style=style,usefulness=use),'cache_key':key,'cache_hit':False}
        if not force:
            cache[key]=result;dump('cache.json',cache)
        print('JUDGED',label,round(sum(g['seconds'] for g in audit[before:]),1),'s',result['scores'],flush=True)
        return result
    q='열차 안에서 시끄럽게 통화하는 사람이 있으면 어떻게 신고하나요?'
    ev=[dict(id='control:noise',text='열차 내 소란은 또타 앱 또는 1577-1234로 신고할 수 있습니다. 일반적인 대화는 제재 대상이 아닙니다.')]
    controls=[('supported',q,ev,'열차 내 소란은 또타 앱 또는 1577-1234로 신고해 주세요.'),
              ('invented_phone',q,ev,'고객센터 1350으로 신고해 주세요.'),
              ('contradiction',q,ev,'또타 앱으로 소란을 신고하는 것은 금지되어 있습니다.'),
              ('irrelevant',q,ev,'세탁기에 세제를 넣고 옷을 세탁해 주세요.'),
              ('rude',q,ev,'멍청아, 열차 내 소란은 또타 앱 또는 1577-1234로 신고해.')]
    results={}
    for label,question,docs,answer in controls:
        try:results[label]=judge(canonical_input(question,docs,answer),'control/'+label)
        except ValueError as exc:
            report.update(status='calibration_failed',calibration_passed=False,error=str(exc));dump('run_report.json',report)
            dump('calibration.json',dict(passed=False,controls=results,error=str(exc)));print('CALIBRATION FAILED',str(exc),flush=True);return
        v=results[label]
        expected={'supported':v['scores']['accuracy']>=4 and v['scores']['actionability']>=4 and v['raw_scores']['politeness']>=4,
            'invented_phone':v['scores']['accuracy']<=2 and v['raw_scores']['politeness']>=4,
            'contradiction':v['raw_scores']['accuracy']<=2,
            'irrelevant':v['raw_scores']['accuracy']<=2,
            'rude':v['raw_scores']['politeness']<=2 and v['raw_scores']['accuracy']>=4}[label]
        if not expected:
            report.update(status='calibration_failed',calibration_passed=False,failed_control=label);dump('run_report.json',report)
            dump('calibration.json',dict(passed=False,controls=results,failed_control=label));print('CALIBRATION FAILED',label,flush=True);return
    positive=canonical_input(q,ev,controls[0][3])
    repeat=judge(positive,'control/supported_repeat_uncached',force=True)
    variant=judge(canonical_input(q,ev,'열차 내 소란은\n또타 앱 또는 1577-1234로  신고해 주세요.'),'control/layout_variant')
    checks=dict(positive_accuracy=results['supported']['scores']['accuracy']>=4,
        positive_politeness=results['supported']['raw_scores']['politeness']>=4,
        positive_actionability=results['supported']['scores']['actionability']>=4,
        unsupported_number=results['invented_phone']['scores']['accuracy']<=2,
        contradiction_raw=results['contradiction']['raw_scores']['accuracy']<=2,
        irrelevant_raw=results['irrelevant']['raw_scores']['accuracy']<=2,
        rude_raw=results['rude']['raw_scores']['politeness']<=2,
        rude_facts_independent=results['rude']['raw_scores']['accuracy']>=4,
        wrong_facts_politeness_independent=results['invented_phone']['raw_scores']['politeness']>=4,
        raw_repeat_consistent=all(results['supported']['raw_scores'][k]==repeat['raw_scores'][k] for k in AXES),
        layout_cache_invariant=variant['cache_hit'] and variant['scores']==results['supported']['scores'])
    passed=all(checks.values());dump('calibration.json',dict(passed=passed,checks=checks,controls=results,uncached_repeat=repeat,layout_variant=variant))
    report.update(calibration_passed=passed,calibration_checks=checks,elapsed_seconds=time.perf_counter()-started)
    dump('run_report.json',report)
    if not passed:
        report['status']='calibration_failed';dump('run_report.json',report)
        print('CALIBRATION FAILED; no performance comparison is valid',flush=True);return
    if args.controls_only:
        report['status']='controls_passed';dump('run_report.json',report);return
    scored=[]
    for record in records:
        case=next(c for c in cases if c['id']==record['test_id'])
        docs=[dict(id=d['chunk_id'],text=d['text']) for d in case['retrieved']]
        payload=canonical_input(record['standard_question'],docs,record['answer'])
        try:
            rating=judge(payload,record['test_id']+'/'+record['system'])
            item={**record,'previous_judge':record['judge'],'judge':{**rating['scores'],'reason':rating['reason']},'judge_audit':rating,'status':'ok'}
        except ValueError as exc:
            item={**record,'previous_judge':record['judge'],'judge':None,'status':'judge_failed','error':str(exc)}
        scored.append(item);dump('raw_records.json',scored)
        report.update(completed_records=len(scored),cache_entries=len(cache),elapsed_seconds=time.perf_counter()-started);dump('run_report.json',report)
    # Check every equal input has the same four scores, including the known layout pairs.
    grouped={}
    for r in scored:
        if r.get('judge_audit'):
            key=r['judge_audit']['cache_key'];v=tuple(r['judge'][k] for k in AXES)
            if key in grouped and grouped[key]!=v:raise AssertionError('Cache invariant violation')
            grouped[key]=v
    systems=['llama','qwen','cluster_synth','rag','refine'];summary={}
    for s in systems:
        rows=[r for r in scored if r['system']==s];summary[s]={}
        for k in ('cross_encoder',)+AXES:
            vals=[r[k] for r in rows] if k=='cross_encoder' else [r['judge'][k] for r in rows if r.get('judge')]
            summary[s][k]=dict(n=len(vals),mean=statistics.mean(vals) if vals else None,sd=statistics.stdev(vals) if len(vals)>1 else None)
    dump('summary.json',summary)
    report.update(status='ok' if all(r.get('judge') for r in scored) else 'partial',equal_input_equal_score=True,
        cache_hits=sum(r.get('judge_audit',{}).get('cache_hit',False) for r in scored),domain_accuracy_validated_by_humans=False)
    dump('run_report.json',report)
    def cell(s,k):
        v=summary[s][k];return '미측정' if v['mean'] is None else f"{v['mean']:.3f} ± {v['sd']:.3f}" if v['sd'] is not None else f"{v['mean']:.3f} ± —"
    lines=['# 채점 보완 후 동일 50개 답변 재평가','',
        '**n=10. 생성·검색 결과는 고정. v2 자동 채점 + 명시적 규칙 상한. 사람 검증된 성능표는 아님.**','',
        '원래 질문·답변·평가용 Top-5를 그대로 사용했습니다. 검색 임계값을 바꾸거나 답변을 새로 생성하지 않았습니다. 원점수와 규칙 조정 결과는 raw_records.json의 judge_audit에 분리해 보관했습니다.','']
    for title,items in [('모델 비교',[('llama','Llama 3.2 3B'),('qwen','Qwen2.5 1.5B'),('refine','Ours')]),('Ablation',[('cluster_synth','Cluster+Synth'),('rag','+RAG'),('refine','+Refine')])]:
        lines+=['## '+title,'','| 조건 | CE | Accuracy | Politeness | Actionability | Essential |','|---|---:|---:|---:|---:|---:|']
        for s,label in items:lines.append('| '+label+' | '+' | '.join(cell(s,k) for k in ('cross_encoder',)+AXES)+' |')
        lines.append('')
    lines+=['## 검증과 제한','',f"- 대조군 검사: {passed}. 동일 정규 입력 동일 점수: True. 원본 50건 중 캐시 재사용 {report['cache_hits']}건.",
        '- 동일 내용은 같은 점수를 재사용하므로 레이아웃 차이로 다른 점수가 생기는 문제를 방지합니다. 이는 의미가 다른 표현까지 정확히 평가한다는 증거가 아닙니다.',
        '- 근거 인용이 실제 문서에 없거나 수치가 제공 근거에 없으면 보수적으로 상한을 적용합니다. 인용이 존재한다고 논리적으로 해당 주장을 뒷받침한다고 보장되지는 않습니다.',
        '- 근거에 없다는 것은 거짓이라는 뜻이 아닙니다. Accuracy는 제공된 문서로 확인되는 사실성 기준이며 문서가 불완전하면 실제로 맞는 답변도 낮게 평가될 수 있습니다.',
        '- 원본의 잘못 생성된 질문·미완성 Llama 출력 3건은 그대로 남아 있습니다. 최종 성능 우열과 독립적인 채점 검증이 필요합니다.',
        '- 이전 점수와 직접 비교한 차이는 채점 프로토콜 변경 효과이며 생성 모델 성능의 변화가 아닙니다. CE 점수는 재계산하지 않았습니다.']
    (out/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print('DONE',report['status'],flush=True)

if __name__=='__main__':main()
