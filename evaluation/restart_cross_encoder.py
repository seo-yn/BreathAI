import json,os
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'evaluation/results/restart-10-20261014'
os.environ.update(HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',HF_HOME=str(ROOT/'tmp/hf-cache'),TORCH_HOME=str(ROOT/'tmp/torch-cache'),TEMP=str(ROOT/'tmp/evaluation-tests'),TMP=str(ROOT/'tmp/evaluation-tests'))
def main():
    import torch,numpy as np
    from sentence_transformers import CrossEncoder
    rows=json.loads((OUT/'judged_answers.json').read_text(encoding='utf-8'))
    models=json.loads((ROOT/'tmp/evaluation-assets/retrieval_models.json').read_text(encoding='utf-8'))
    spec=next(m for m in models if m['model_id']=='BAAI/bge-reranker-v2-m3')
    ce=CrossEncoder(spec['path'],device='cuda',max_length=512,local_files_only=True,default_activation_function=torch.nn.Sigmoid())
    scores=ce.predict([(r['question'],r['answer']) for r in rows],batch_size=2,show_progress_bar=False)
    for r,value in zip(rows,scores):
        assert np.isfinite(value) and 0<=value<=1;r['cross_encoder']=float(value)
    (OUT/'raw_records.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding='utf-8')
    report=json.loads((OUT/'run_report.json').read_text(encoding='utf-8'));report.update(status='complete',cross_encoder=spec,ce_definition='sigmoid(question, generated answer); semantic matching, not factuality probability',completed_records=len(rows))
    (OUT/'run_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('CE_DONE',len(rows))
if __name__=='__main__':main()
