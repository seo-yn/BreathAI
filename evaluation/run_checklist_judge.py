"""Bounded deterministic checklist calibration, then provisional frozen regrade."""
import argparse,hashlib,json,os,time
from pathlib import Path
from evaluation.checklist_judge import VERSION,CONTENT_PROMPT,STYLE_PROMPT,validate_content,validate_style,scores
from evaluation.stable_judge import canonical_input
from evaluation.constrained_choices import style_completions,prefix_constraint

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--controls-only',action='store_true');ap.add_argument('--output',default='evaluation/results/checklist-v3-001');ap.add_argument('--diagnostic-controls',action='store_true');ap.add_argument('--resume-controls',action='store_true');ap.add_argument('--budget-seconds',type=int,default=3600);args=ap.parse_args()
    root=Path(__file__).resolve().parents[1];out=root/args.output;out.mkdir(exist_ok=True)
    os.environ.update(HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',HF_HOME=str(root/'tmp/hf-cache'),TORCH_HOME=str(root/'tmp/torch-cache'),TEMP=str(root/'tmp/evaluation-tests'),TMP=str(root/'tmp/evaluation-tests'),CUBLAS_WORKSPACE_CONFIG=':4096:8')
    read=lambda p:json.loads(p.read_text(encoding='utf-8'))
    def dump(n,v): (out/n).write_text(json.dumps(v,ensure_ascii=False,indent=2),encoding='utf-8')
    spec=next(m for m in read(root/'evaluation/results/presentation-10-001/models.json') if m['role']=='judge')
    old_report=read(out/'run_report.json') if args.resume_controls and (out/'run_report.json').exists() else None
    prompt_hash=hashlib.sha256((CONTENT_PROMPT+STYLE_PROMPT).encode()).hexdigest()
    if old_report and (old_report.get('judge')!=spec or old_report.get('protocol')!=VERSION or old_report.get('prompt_sha256')!=prompt_hash or old_report.get('style_decoding')!='finite grammar: all three labels and actual answer snippets'):raise ValueError('Cannot resume different model or judging protocol')
    report=dict(status='running',protocol=VERSION,judge=spec,seed=42,deterministic_algorithms=True,do_sample=False,prompt_sha256=hashlib.sha256((CONTENT_PROMPT+STYLE_PROMPT).encode()).hexdigest(),
        human_validity='pending',human_ground_truth_count=0,original_answers_unchanged=True,rag_threshold_unchanged=True,style_decoding='finite grammar: all three labels and actual answer snippets',diagnostic_controls=args.diagnostic_controls,budget_seconds=args.budget_seconds)
    dump('run_report.json',report)
    import torch
    from transformers import AutoModelForCausalLM,AutoTokenizer,set_seed
    set_seed(42);torch.use_deterministic_algorithms(True);torch.backends.cudnn.benchmark=False;torch.backends.cudnn.deterministic=True
    torch.backends.cuda.matmul.allow_tf32=False;torch.backends.cudnn.allow_tf32=False
    start=time.perf_counter();audit=read(out/'generation_audit.json') if args.resume_controls else [];cache={}
    tok=AutoTokenizer.from_pretrained(spec['path'],local_files_only=True)
    model=AutoModelForCausalLM.from_pretrained(spec['path'],local_files_only=True,torch_dtype=torch.float16,device_map='auto',max_memory={0:'4300MiB','cpu':'7GiB'},attn_implementation='eager').eval()
    def invoke(prompt,label,validate,completions=None):
        if time.perf_counter()-start>args.budget_seconds:raise TimeoutError('Experiment time budget exhausted')
        set_seed(42)
        text=tok.apply_chat_template([dict(role='system',content='항목별 검사 결과를 짧은 JSON 객체 하나로 출력하세요.'),dict(role='user',content=prompt)],tokenize=False,add_generation_prompt=True)
        inputs=tok(text,return_tensors='pt').to('cuda');t=time.perf_counter()
        extra={'prefix_allowed_tokens_fn':prefix_constraint(tok,completions,inputs.input_ids.shape[1])} if completions else {}
        with torch.inference_mode():result=model.generate(**inputs,max_new_tokens=192,max_time=120,do_sample=False,pad_token_id=tok.eos_token_id,**extra)
        raw=tok.decode(result[0][inputs.input_ids.shape[1]:],skip_special_tokens=True).strip()
        audit.append(dict(label=label,prompt=prompt,raw=raw,seconds=time.perf_counter()-t,tokens=int(result.shape[1]-inputs.input_ids.shape[1])))
        dump('generation_audit.json',audit)
        obj=json.loads(raw[raw.index('{'):raw.rindex('}')+1]);return validate(obj)
    def judge(payload,label,force=False):
        key=hashlib.sha256(json.dumps(dict(payload=payload,protocol=VERSION,model=spec,prompts=[CONTENT_PROMPT,STYLE_PROMPT]),sort_keys=True,ensure_ascii=False).encode()).hexdigest()
        if key in cache and not force:return {**cache[key],'cache_hit':True}
        prompt=CONTENT_PROMPT+json.dumps(payload,ensure_ascii=False)
        try:c=invoke(prompt,label+'/content',lambda o:validate_content(o,payload))
        except (ValueError,KeyError,TypeError) as exc:
            c=invoke(prompt+'\n이전 출력의 형식 오류: '+str(exc)+'. support는 문서 ID가 아니라 evidence.text 본문의 부분 문자열이어야 한다. claim은 answer의 부분 문자열이어야 한다. 오류만 수정하여 짧은 JSON 출력.',label+'/content_repair',lambda o:validate_content(o,payload))
        s=invoke(STYLE_PROMPT+payload['answer'],label+'/style',lambda o:validate_style(o,payload['answer']),style_completions(payload['answer']))
        obj=dict(content=c,style=s,endpoint_scores=scores(c,s),cache_key=key,cache_hit=False)
        if not force:cache[key]=obj;dump('cache.json',cache)
        print(label,json.dumps(dict(content=c,style=s),ensure_ascii=False),flush=True);return obj
    question='열차 안에서 큰 소리로 통화하는 사람이 있으면 어떻게 신고하나요?'
    ev=[dict(id='synthetic-control',text='열차 내 소란은 또타 앱 또는 1577-1234로 신고할 수 있습니다. 일반적인 대화는 제재 대상이 아닙니다.')]
    required=['또타 앱 또는 1577-1234 신고 안내']
    def payload(answer):return {**canonical_input(question,ev,answer),'required':required}
    answer='열차 내 소란은 또타 앱 또는 1577-1234로 신고해 주세요.'
    controls=[('supported',answer,dict(fact='supported',respectful='pass',action='pass',essential='pass')),
        ('invented_phone','고객센터 1350으로 신고해 주세요.',dict(fact='unknown',respectful='pass')),
        ('contradiction','또타 앱으로 소란을 신고하는 것은 금지되어 있습니다.',dict(fact='contradicted',respectful='pass')),
        ('irrelevant','세탁기에 세제를 넣고 옷을 세탁해 주세요.',dict(fact='unknown',respectful='pass',relevant='fail',action='fail',essential='fail')),
        ('rude','멍청아, 열차 내 소란은 또타 앱 또는 1577-1234로 신고해.',dict(fact='supported',respectful='fail',action='pass')),
        ('repeat_1',answer,None),('repeat_2',answer,None),
        ('layout_raw','열차 내 소란은\n또타 앱 또는 1577-1234로  신고해 주세요.',None),
        ('paraphrase','소란 신고는 또타 앱이나 1577-1234를 이용해 주세요.',dict(fact='supported',respectful='pass',action='pass',essential='pass'))]
    results=read(out/'calibration.json') if args.resume_controls else [];first=None
    for previous in results:
        if previous['label']=='supported' and 'result' in previous:first={**previous['result']['content'],**previous['result']['style']}
    for label,text,expected in controls:
        if any(r['label']==label for r in results):continue
        p=payload(text)
        # Layout perturbation must reach the model uncached and unnormalized.
        if label=='layout_raw':p['answer']=text
        try:
            r=judge(p,'control/'+label,force=True);values={**r['content'],**r['style']}
            passed=all(values.get(k)==v for k,v in expected.items()) if expected else all(values[k]==first[k] for k in ('fact','respectful','relevant','action','essential'))
            if label=='supported':first=values
            results.append(dict(label=label,expected=expected,passed=passed,result=r))
        except TimeoutError as e:
            report.update(status='diagnostic_budget_exhausted',calibration_passed=False,error=str(e));dump('run_report.json',report);return
        except (ValueError,KeyError,TypeError,RuntimeError) as e:results.append(dict(label=label,passed=False,error=str(e)))
        dump('calibration.json',results)
        report.update(completed_controls=len(results),elapsed_seconds=time.perf_counter()-start);dump('run_report.json',report)
        if not results[-1]['passed'] and not args.diagnostic_controls:
            report.update(status='calibration_failed',calibration_passed=False,failed_control=label);dump('run_report.json',report);return
    passed=all(r['passed'] for r in results)
    report.update(calibration_passed=passed,status='controls_passed' if passed else 'calibration_failed');dump('run_report.json',report)
    if not passed or args.controls_only:return
    public=read(root/'evaluation/results/checklist-v3-001/blind_items.json');ratings=[]
    for p in public:
        payload_data={**canonical_input(p['question'],p['evidence'],p['answer']),'required':p['required_draft']}
        try:ratings.append(dict(item_id=p['item_id'],case_id=p['case_id'],status='unvalidated_provisional',result=judge(payload_data,p['item_id'])))
        except (ValueError,KeyError,TypeError,RuntimeError) as e:ratings.append(dict(item_id=p['item_id'],status='judge_failed',error=str(e)))
        dump('provisional_ratings.json',ratings)
        report.update(completed_records=len(ratings),elapsed_seconds=time.perf_counter()-start);dump('run_report.json',report)
    report.update(status='provisional_complete_human_validation_pending');dump('run_report.json',report)
if __name__=='__main__':main()
