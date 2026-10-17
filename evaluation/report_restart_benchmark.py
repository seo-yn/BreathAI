"""Summarize actual observations and produce presentation-shaped comparison tables."""
import html,json,statistics,os,argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'evaluation/results/restart-10-20261014'
os.environ.update(MPLCONFIGDIR=str(ROOT/'tmp/mpl-cache'),TEMP=str(ROOT/'tmp/evaluation-tests'),TMP=str(ROOT/'tmp/evaluation-tests'))
METRICS=('cross_encoder','accuracy','politeness','actionability','essential')
LABELS={'llama':'Llama 3.2 3B (FP16)','qwen':'Qwen2.5 3B (FP16)','cluster_synth':'Cluster+Synth','rag':'+RAG','refine':'Ours / +Refine (Qwen2.5 3B)'}
def main():
    global OUT
    ap=argparse.ArgumentParser();ap.add_argument('--directory',type=Path,default=OUT);args=ap.parse_args();OUT=args.directory.resolve()
    read=lambda p:json.loads(p.read_text(encoding='utf-8'))
    rows=read(OUT/'raw_records.json');report=read(OUT/'run_report.json');retrieval=read(OUT/'retrieval_report.json');calibration=read(OUT/'judge_calibration.json')
    if report['status']!='complete':raise ValueError('Do not publish unfinished measurements')
    glider='glider' in report['final_judge'].lower();judge_short='GLIDER 3.8B (Phi-3.5)' if glider else 'M-Prometheus-7B'
    judge_url='https://huggingface.co/PatronusAI/glider' if glider else 'https://huggingface.co/Unbabel/M-Prometheus-7B'
    judge_format='공식 GLIDER XML 평가 형식' if glider else '공식 Prometheus 직접 평가 형식'
    expected={(f'T{i:02d}',s) for i in range(1,11) for s in LABELS}
    if {(r['test_id'],r['system']) for r in rows}!=expected or len(rows)!=50:raise ValueError('Missing or duplicate sample/condition')
    for r in rows:
        if not 0<=r['cross_encoder']<=1:raise ValueError('Invalid CE')
        for k in METRICS[1:]:
            v=r['judge'][k]
            if v is not None and (type(v) is not int or not 1<=v<=5):raise ValueError('Invalid Likert score')
    summary={}
    for system in LABELS:
        selected=[r for r in rows if r['system']==system];summary[system]={}
        for metric in METRICS:
            values=[r['cross_encoder'] if metric=='cross_encoder' else r['judge'][metric] for r in selected]
            known=[v for v in values if v is not None]
            summary[system][metric]=dict(n=len(known),mean=statistics.mean(known) if known else None,sd=statistics.stdev(known) if len(known)>1 else None)
        summary[system]['generation']=dict(n=len(selected),eos_completed=sum(r['finish_reason']=='stop' for r in selected),token_limited=sum(r['finish_reason']=='length' for r in selected),mean_seconds=statistics.mean(r['seconds'] for r in selected))
    (OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),encoding='utf-8')
    def cell(system,metric):
        x=summary[system][metric]
        if x['mean'] is None:return '미측정'
        text=f"{x['mean']:.3f} ± {x['sd']:.3f}" if x['sd'] is not None else f"{x['mean']:.3f} ± —"
        return text if x['n']==10 else text+f" (n={x['n']})"
    tables=[('모델 비교',['llama','qwen','refine']),('Ablation',['cluster_synth','rag','refine'])]
    lines=['# 새 Judge 기반 10개 표본 실험 결과','',
    '**각 조건 n=10, 평균 ± 표본 표준편차(ddof=1). 2026-10-14 새로 생성한 50개 답변의 자동 예비 평가입니다.**','',
    f'생성 모델: Llama 3.2 3B / Qwen2.5 3B FP16. 새 Judge: {judge_short}, Q4_K_M GGUF. 기존 범용 Qwen Judge 및 이전 보완 채점기를 사용하지 않았습니다. {judge_format}으로 축마다 별도 1~5점을 얻었습니다.','',
    f"**Judge 대조 검증: {'통과' if report['judge_calibration_passed'] else '일부 실패'}**. 자세한 조건별 점수와 반복·레이아웃 일치 여부는 아래에 있습니다. 사람 평가와의 일치도는 미측정입니다.",'']
    for title,systems in tables:
        lines+=['## '+title,'','| 조건 | Cross-Encoder ↑ | Accuracy ↑ | Politeness ↑ | Actionability ↑ | Essential ↑ |','|---|---:|---:|---:|---:|---:|']
        for s in systems:lines.append('| '+LABELS[s]+' | '+' | '.join(cell(s,k) for k in METRICS)+' |')
        lines.append('')
    lines+=['## Judge 대조 검증','', '| 조건 | Accuracy | Politeness | Actionability | Essential |','|---|---:|---:|---:|---:|']
    for c in calibration:lines.append('| '+c['name']+' | '+' | '.join(str(c['scores'][k]) for k in METRICS[1:])+' |')
    lines+=['', '검증 항목: '+json.dumps(report['judge_calibration_checks'],ensure_ascii=False), '',
    '## 해석 범위','',
    '- Cross-Encoder는 질문과 생성 답변의 의미 정합 점수입니다. 사실성 확률이나 검색 정확도가 아닙니다.',
    f"- 출처 FAQ 매핑 기반 Recall@5={retrieval['recall_at_5']:.3f}, MRR@5={retrieval['mrr_at_5']:.3f}. 사전 구성한 10개 주제의 검색 결과이며 전체 미응답 로그 성능으로 일반화할 수 없습니다.",
    '- 질문은 모든 조건에서 같게 고정했습니다. 질문 생성·군집 품질의 종단간 평가가 아니라 답변 품질의 통제 실험입니다.',
    '- 기존 군집의 로그 맥락과 FAQ 코퍼스를 사용했지만 답변과 Judge 점수는 모두 새로 생성했습니다. 참조 FAQ는 RAG 검색 코퍼스에 포함되어 있습니다.',
    '- Cluster+Synth → RAG 수정 → 한 번의 자기 비평·Refine 흐름입니다. 운영 코드의 조건부 best-of-N과 동일한 실험이라고 주장하지 않습니다.',
    ('- GLIDER는 Phi-3.5 계열의 별도 평가 학습된 모델입니다. 생성 모델과 계열이 다르지만 양자화 영향과 평가 편향이 남아 있습니다.' if glider else '- M-Prometheus는 별도 평가 학습된 모델이지만 Qwen 계열 기반입니다. 양자화 영향과 모델 계열 편향이 남아 있습니다.'),
    '- 참조는 제공된 데이터셋의 FAQ입니다. 최신 운영 정책을 외부 검증한 것이 아니며 독립적인 사람 정답 라벨도 없습니다.',
    '- 표본 크기는 조건마다 질문 10개입니다. 50개 답변을 독립 질문 50개로 세지 않습니다. PDF와 모델·표본·Judge가 다르므로 수치의 직접 재현 비교는 부적절합니다.',
    '- 파싱 실패는 미측정으로 두고 실제 n을 표시합니다. 점수를 강제로 보정하거나 실패를 0점으로 바꾸지 않았습니다.','',
    '## 생성 완료와 시간','', '| 조건 | 정상 종료 | 토큰 제한 | 평균 생성 시간(초) |','|---|---:|---:|---:|']
    for s in LABELS:
        g=summary[s]['generation'];lines.append(f"| {LABELS[s]} | {g['eos_completed']}/10 | {g['token_limited']}/10 | {g['mean_seconds']:.1f} |")
    calls=read(OUT/'generation_audit.json');ours_seconds=[sum(a['seconds'] for a in calls if a['case_id']==f'T{i:02d}' and a['condition'] in ('cluster_synth','rag','self_critique','refine')) for i in range(1,11)]
    lines+=['',f'표의 시간은 해당 답변 생성 호출만의 시간입니다. Ours의 초안·RAG·비평·수정을 합산한 평균 모델 호출 시간은 {statistics.mean(ours_seconds):.1f}초/질문입니다. 서버 로딩·다운로드·최종 평가는 포함하지 않습니다.']
    lines+=['','## 실행 기록','',
    '- benchmark.json: 고정 질문, 근거 FAQ, 로그 맥락 분리와 원본 해시.',
    '- runtime_manifest.json: 모델 리비전·정밀도·파일 SHA256·실행 바이너리.',
    '- generation_audit.json / judge_audit.json: 모든 프롬프트·원출력·시간·토큰·완료 사유.',
    '- raw_records.json / summary.json: 개별 관측과 집계.',
    '- experiment_plan.md / restart_rubrics.json: 답변 생성 전에 고정한 설계·채점 기준.','',
    'Judge 출처: '+judge_url]
    (OUT/'results.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    body=['<h1>새 Judge 기반 성능 비교</h1><p>n=10 · 평균 ± 표본 표준편차 · 자동 예비 평가</p><p>Judge: '+judge_short+' Q4_K_M · 생성: FP16 3B 모델</p>',
        '<p class="notice">Judge 대조 검증: '+('통과' if report['judge_calibration_passed'] else '일부 실패')+' · 사람 평가와의 일치도 미측정</p>']
    for title,systems in tables:
        body.append('<h2>'+title+'</h2><table><thead><tr><th>조건</th>'+''.join('<th>'+k+'</th>' for k in METRICS)+'</tr></thead><tbody>')
        for s in systems:body.append('<tr'+(' class="ours"' if s=='refine' else '')+'><th>'+LABELS[s]+'</th>'+''.join('<td>'+cell(s,k)+'</td>' for k in METRICS)+'</tr>')
        body.append('</tbody></table>')
    body.append('<h2>개별 답변과 참조</h2>')
    bench={c['id']:c for c in read(OUT/'benchmark.json')['cases']}
    for i in range(1,11):
        c=bench[f'T{i:02d}'];body.append('<details><summary>'+c['id']+' · '+html.escape(c['question'])+'</summary><h3>참조 FAQ</h3><pre>'+html.escape(c['reference_answer'])+'</pre>')
        for r in rows:
            if r['test_id']==c['id']:body.append('<h3>'+LABELS[r['system']]+'</h3><pre>'+html.escape(r['answer'])+'</pre><p>'+html.escape(json.dumps(r['judge'],ensure_ascii=False))+'</p>')
        body.append('</details>')
    body.append('<p>상세 설계·제한·검증 결과는 results.md에 있습니다. 확정된 사람 평가 결과가 아닙니다.</p>')
    document='<!doctype html><html lang="ko"><meta charset="utf-8"><title>BreathAI 10표본 비교</title><style>body{max-width:1200px;margin:40px auto;font:17px/1.65 sans-serif;color:#192537;background:#f5f7fa}table{border-collapse:collapse;width:100%;background:white;margin:20px 0}th,td{padding:14px;border:1px solid #bcc9d8;text-align:center}thead{background:#e6ecff}.ours{background:#fff0f0}pre{white-space:pre-wrap;background:white;padding:16px}.notice{background:#fff1d2;padding:16px}details{background:white;padding:16px;margin:12px 0}</style>'+''.join(body)+'</html>'
    (OUT/'results.html').write_text(document,encoding='utf-8')
    # Export a sharp table image suitable for inserting into the presentation.
    import matplotlib
    matplotlib.use('Agg')
    import matplotlib.pyplot as plt
    from matplotlib import font_manager
    font=Path('C:/Windows/Fonts/malgun.ttf')
    if font.exists():font_manager.fontManager.addfont(str(font));plt.rcParams['font.family']='Malgun Gothic'
    fig,axes=plt.subplots(2,1,figsize=(16,8));fig.patch.set_facecolor('white')
    for ax,(title,systems) in zip(axes,tables):
        ax.axis('off');ax.set_title(title,fontsize=18,fontweight='bold',loc='left',pad=12)
        values=[[LABELS[s]]+[cell(s,k) for k in METRICS] for s in systems]
        table=ax.table(cellText=values,colLabels=['Condition','Cross-Encoder ↑','Accuracy ↑','Politeness ↑','Actionability ↑','Essential ↑'],cellLoc='center',colWidths=[.27,.145,.145,.145,.145,.15],bbox=[0,.08,1,.82])
        table.auto_set_font_size(False);table.set_fontsize(11)
        for (r,c),cell_obj in table.get_celld().items():
            cell_obj.set_edgecolor('#66758a');cell_obj.set_linewidth(.8)
            if r==0:cell_obj.set_facecolor('#e6ecff');cell_obj.set_text_props(weight='bold')
            elif systems[r-1]=='refine':cell_obj.set_facecolor('#fff0f0')
    fig.suptitle('BreathAI: 10-sample preliminary comparison',fontsize=21,fontweight='bold')
    fig.text(.5,.025,'n=10 / Mean ± sample SD / Generators: FP16 / Judge: '+judge_short+' Q4_K_M / Calibration: '+('passed' if report['judge_calibration_passed'] else 'partly failed'),ha='center',fontsize=11)
    fig.tight_layout(rect=[0,.05,1,.94]);fig.savefig(OUT/'comparison_tables.png',dpi=180);plt.close(fig)
    print('TABLES_READY',OUT)
if __name__=='__main__':main()
