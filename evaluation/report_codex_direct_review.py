"""Publish explicit, one-off Codex judgments; this is not an automated Judge runner."""
import hashlib, html, json, os, statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'evaluation/results/restart-10-20261014'
OUT = SOURCE / 'codex-direct-review'
os.environ.update(MPLCONFIGDIR=str(ROOT/'tmp/mpl-cache'), TEMP=str(ROOT/'tmp/evaluation-tests'), TMP=str(ROOT/'tmp/evaluation-tests'))
AXES = ['accuracy', 'politeness', 'actionability', 'essential']
SYSTEMS = ['llama', 'qwen', 'cluster_synth', 'rag', 'refine']
LABELS = dict(zip(SYSTEMS, ['Llama 3.2 3B FP16', 'Qwen2.5 3B FP16', 'Cluster+Synth', '+RAG', 'Ours / +Refine']))
# Direct assessments are local experiment outputs, intentionally excluded from Git.
# This reporter reads assessments.json; it does not perform a new evaluation.
def load_review():
    artifact=OUT/'assessments.json'
    if not artifact.exists():
        raise FileNotFoundError('Provide the locally reviewed assessments.json; this script does not generate scores.')
    records=json.loads(artifact.read_text(encoding='utf-8'))['records']
    return {f'T{i:02d}': [(list(next(r for r in records if r['test_id']==f'T{i:02d}' and r['system']==s)['scores'][a] for a in AXES), next(r for r in records if r['test_id']==f'T{i:02d}' and r['system']==s)['assessment']) for s in SYSTEMS] for i in range(1,11)}

def main():
 REVIEW=load_review()
 OUT.mkdir(exist_ok=True)
 read=lambda name: json.loads((SOURCE/name).read_text(encoding='utf-8'))
 original=read('raw_records.json'); benchmark={c['id']:c for c in read('benchmark.json')['cases']}
 evidence={c['id']:c['used_evidence'] for c in read('retrieval.json')}
 rows=[]
 for c,judgments in REVIEW.items():
  for s,(scores,note) in zip(SYSTEMS,judgments):
   r=next(x for x in original if x['test_id']==c and x['system']==s)
   rows.append({k:r[k] for k in ('test_id','system','question','answer','finish_reason','seconds','cross_encoder')} | {
    'scores':dict(zip(AXES,scores)), 'assessment':note.replace('反復','반복'),
    'reference':benchmark[c]['reference_answer'], 'evidence_ids':[d['chunk_id'] for d in evidence[c]],
    'answer_sha256':hashlib.sha256(r['answer'].encode()).hexdigest()})
 assert len(rows)==50 and len({(r['test_id'],r['system']) for r in rows})==50
 # Exact duplicate answers under the same question must retain identical judgments.
 groups={}
 for r in rows:
  key=(r['test_id'],r['answer']); assert key not in groups or groups[key]==r['scores']; groups[key]=r['scores']
 meta={'evaluator':'Codex assistant, direct contextual assessment in current conversation',
  'date':'2026-10-15','paid_api_calls':0,'n_questions':10,'n_answers':50,
  'rating_scale':'1–5, existing restart_rubrics.json', 'blind':False,
  'limitations':['Single assistant assessment, not human evaluation.', 'Model/condition identities and prior results were visible; no blinded experiment.', 'No independent repeatability or human agreement validation.', 'Source FAQ is dataset evidence, not verified current policy.', 'Llama has two length-limited outputs; retained without regeneration.'],
  'source_sha256':{f:hashlib.sha256((SOURCE/f).read_bytes()).hexdigest() for f in ['raw_records.json','benchmark.json','retrieval.json','restart_rubrics.json']},
  'scope':'Controlled fixed-question answer comparison, not end-to-end pipeline evaluation.'}
 (OUT/'assessments.json').write_text(json.dumps({'metadata':meta,'records':rows},ensure_ascii=False,indent=2),encoding='utf-8')
 metrics=['cross_encoder']+AXES
 summary={s:{m:{'n':10,'mean':statistics.mean([r['cross_encoder'] if m=='cross_encoder' else r['scores'][m] for r in rows if r['system']==s]),'sd':statistics.stdev([r['cross_encoder'] if m=='cross_encoder' else r['scores'][m] for r in rows if r['system']==s])} for m in metrics} for s in SYSTEMS}
 (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
 cell=lambda s,m:f"{summary[s][m]['mean']:.3f} ± {summary[s][m]['sd']:.3f}"
 tables=[('모델 비교',['llama','qwen','refine']),('Ablation',['cluster_synth','rag','refine'])]
 lines=['# Codex 직접 평가: 10개 질문 · 50개 답변','',
 '**단일 AI 평가자의 탐색적 평가입니다. 사람 평가나 검증된 독립 Judge의 결과가 아닙니다.**','',
 '각 조건 n=10, 평균 ± 표본 표준편차(ddof=1). Accuracy는 1~5점 사실성 평정이며 정답률이 아닙니다. CE는 기존 실제 Cross-Encoder 측정값을 그대로 사용했습니다.', '',
 '2026-10-15 현재 대화의 Codex가 50개 답변, 참조 FAQ, 실제 제공된 검색 근거를 읽고 직접 평정했습니다. 별도 유료 API 호출은 없습니다. 생성 모델은 Llama 3.2 3B / Qwen2.5 3B FP16이고, 기존 Judge 점수는 집계에 사용하지 않았습니다.', '',
 '평가 기준은 사전에 작성된 restart_rubrics.json을 유지했습니다. 사실성은 주장과 근거의 일치, 공손함은 말투만, 실행 가능성은 올바른 행동·이해 가능성, Essential은 질문에 필요한 정보 충족도를 평가했습니다. 사실 누락을 모두 사실 오류로 처리하지 않았으며, 무관하지만 출처가 있는 정보는 사실성보다 Essential에서 감점했습니다.', '']
 body=['<h1>Codex 직접 평가</h1><p class="notice">단일 AI 평가자 · 탐색적 평가 · 사람 평가 아님 · 외부 유료 API 호출 없음</p><p>10개 질문 × 5조건. 평균 ± 표본 표준편차. CE는 실제 측정값, 나머지는 1~5점 직접 평정.</p>']
 for title,systems in tables:
  lines += ['## '+title,'','| 조건 | Cross-Encoder ↑ | Accuracy ↑ | Politeness ↑ | Actionability ↑ | Essential ↑ |','|---|---:|---:|---:|---:|---:|']
  body.append('<h2>'+title+'</h2><table><tr><th>조건</th>'+''.join('<th>'+m+'</th>' for m in metrics)+'</tr>')
  for s in systems:
   lines.append('| '+LABELS[s]+' | '+' | '.join(cell(s,m) for m in metrics)+' |')
   body.append('<tr><th>'+LABELS[s]+'</th>'+''.join('<td>'+cell(s,m)+'</td>' for m in metrics)+'</tr>')
  lines.append('');body.append('</table>')
 lines += ['## 해석과 제한','',
 '- 이번 직접 평가에서는 Ours의 평균 사실성·실행 가능성·필수 정보 점수가 기본 Qwen보다 높습니다. 이 10개 표본에서의 단일 평가자 결과이며, 전체 시스템 우열이나 통계적 유의성을 입증하지 않습니다.',
 '- Refine은 냄새 신고의 세탁비 오류를 삭제했지만, 선교활동 허용·PSD/출입문 혼동·장난감 판매 공간 오류를 해결하지 못했습니다.',
 '- 사실성과 말투를 분리했으므로 정중한 오답도 공손함 5점을 받을 수 있습니다. 공손함의 작은 차이 자체는 채점 실패가 아닙니다.',
 '- 모델/조건 이름과 이전 점수가 보인 상태에서 평가했으므로 맹검이 아닙니다. 별도 반복 평가나 사람과의 일치도 검증도 수행하지 않았습니다.',
 '- 동일 질문의 완전히 동일한 답변은 동일 점수인지 저장 단계에서 확인했습니다. 이는 독립 반복 채점의 재현성 검증이 아닙니다.',
 '- 질문은 고정했고 기존 군집 맥락을 사용했습니다. 전처리·군집화·질문 생성의 종단간 성능은 측정하지 않았습니다.',
 '- 사전에 구성한 10개 주제의 검색 Recall@5 및 MRR@5는 각각 1.0입니다. 참조 FAQ가 검색 코퍼스에 포함되어 있으며 실제 운영 분포의 검색 성능으로 일반화할 수 없습니다.',
 '- Llama 2개 답변은 토큰 제한으로 잘렸습니다. 모든 조건에 같은 생성 예산을 적용했고 이 답변도 포함했습니다.',
 '- 발표에는 Judge를 “Codex 직접 평가(단일 AI 평가자, 비맹검)”라고 표시해야 합니다. 정식 독립 Judge 결과 또는 사람 평가라고 표시하면 안 됩니다.', '', '## 개별 평가 근거','']
 for c in REVIEW:
  lines += ['### '+c+' — '+benchmark[c]['question'],'','참조: '+benchmark[c]['reference_answer'],'']
  body.append('<details><summary>'+c+' · '+html.escape(benchmark[c]['question'])+'</summary><h3>참조 FAQ</h3><pre>'+html.escape(benchmark[c]['reference_answer'])+'</pre>')
  for r in rows:
   if r['test_id']!=c:continue
   score=', '.join(f'{m}={r["scores"][m]}' for m in AXES)
   lines += ['**'+LABELS[r['system']]+'** ('+score+')','',r['answer'],'','평가: '+r['assessment'],'']
   body.append('<h3>'+LABELS[r['system']]+'</h3><pre>'+html.escape(r['answer'])+'</pre><p>'+score+'</p><p>'+html.escape(r['assessment'])+'</p>')
  body.append('</details>')
 (OUT/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
 body.append('<p>비맹검 단일 AI 평가. 독립 반복 평가/사람 일치도 미검증. 상세 범위·제한은 results.md 참고.</p>')
 (OUT/'results.html').write_text('<!doctype html><html lang="ko"><meta charset="utf-8"><title>Codex 직접 평가</title><style>body{font:16px/1.6 sans-serif;max-width:1200px;margin:30px auto;color:#182537}table{border-collapse:collapse;width:100%;margin:20px 0}td,th{border:1px solid #bdc8d5;padding:12px;text-align:center}th{background:#e9efff}pre{white-space:pre-wrap;background:#f5f7fa;padding:14px}.notice{background:#fff0cc;padding:16px}details{border:1px solid #bdc8d5;padding:12px;margin:12px 0}</style>'+''.join(body)+'</html>',encoding='utf-8')
 import matplotlib
 matplotlib.use('Agg')
 import matplotlib.pyplot as plt
 from matplotlib import font_manager
 font_manager.fontManager.addfont('C:/Windows/Fonts/malgun.ttf');plt.rcParams['font.family']='Malgun Gothic'
 fig,axes=plt.subplots(2,1,figsize=(16,8));fig.patch.set_facecolor('white')
 for ax,(title,systems) in zip(axes,tables):
  ax.axis('off');ax.set_title(title,loc='left',fontsize=18,pad=10)
  table=ax.table(cellText=[[LABELS[s]]+[cell(s,m) for m in metrics] for s in systems],colLabels=['조건','Cross-Encoder ↑','Accuracy ↑','Politeness ↑','Actionability ↑','Essential ↑'],colWidths=[.27,.146,.146,.146,.146,.146],cellLoc='center',bbox=[0,.08,1,.82])
  table.auto_set_font_size(False);table.set_fontsize(12)
  for (rr,cc),cl in table.get_celld().items():
   cl.set_edgecolor('#c0ccdc')
   if rr==0:cl.set_facecolor('#e9efff')
   elif systems[rr-1]=='refine':cl.set_facecolor('#fff0e5')
 fig.suptitle('Codex 직접 평가 · 10개 질문 · 평균 ± 표본 표준편차',fontsize=20)
 fig.text(.5,.015,'단일 AI 평가자 / 비맹검 / 사람 평가 아님 / CE는 측정값, 나머지는 1–5점 평정',ha='center',fontsize=12)
 fig.tight_layout(rect=[0,.04,1,.94]);fig.savefig(OUT/'comparison_tables.png',dpi=180);plt.close(fig)
 print(json.dumps(summary,ensure_ascii=False,indent=2))

if __name__=='__main__':main()
