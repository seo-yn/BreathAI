import gc,hashlib,json,os,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'evaluation/results/restart-10-20261014'
os.environ.update(HF_HUB_OFFLINE='1',TRANSFORMERS_OFFLINE='1',HF_HOME=str(ROOT/'tmp/hf-cache'),TORCH_HOME=str(ROOT/'tmp/torch-cache'),TEMP=str(ROOT/'tmp/evaluation-tests'),TMP=str(ROOT/'tmp/evaluation-tests'))
def main():
    import numpy as np,torch
    from sentence_transformers import SentenceTransformer,CrossEncoder
    read=lambda p:json.loads(p.read_text(encoding='utf-8'))
    bench=read(OUT/'benchmark.json');kb=read(ROOT/'tmp/evaluation-assets/kb_snapshot.json');embpath=ROOT/'tmp/evaluation-assets/kb_embeddings.npy';emb=np.load(embpath)
    assert emb.shape==(len(kb),1024)
    models=read(ROOT/'tmp/evaluation-assets/retrieval_models.json');paths={m['model_id']:m['path'] for m in models}
    start=time.perf_counter();enc=SentenceTransformer(paths['BAAI/bge-m3'],device='cuda',local_files_only=True)
    queries=enc.encode([c['question'] for c in bench['cases']],batch_size=2,normalize_embeddings=True,show_progress_bar=False)
    normalized=emb/np.maximum(np.linalg.norm(emb,axis=1,keepdims=True),1e-12)
    candidate_indices=[np.argsort(normalized@q)[::-1][:20] for q in queries]
    del enc;gc.collect();torch.cuda.empty_cache()
    ce=CrossEncoder(paths['BAAI/bge-reranker-v2-m3'],device='cuda',max_length=512,local_files_only=True,default_activation_function=torch.nn.Sigmoid())
    results=[]
    for case,indices in zip(bench['cases'],candidate_indices):
        t=time.perf_counter();scores=ce.predict([(case['question'],kb[int(i)]['text']) for i in indices],batch_size=2,show_progress_bar=False)
        candidates=[{**kb[int(i)],'rerank_score':float(s)} for i,s in zip(indices,scores)]
        ranked=sorted(candidates,key=lambda d:d['rerank_score'],reverse=True)[:5]
        used=[d for d in ranked if d['rerank_score']>=0.05][:3]
        ids=[d['chunk_id'] for d in ranked];rank=ids.index(case['reference_id'])+1 if case['reference_id'] in ids else None
        results.append(dict(id=case['id'],retrieved=ranked,used_evidence=used,source_faq_rank=rank,recall_at_5=int(rank is not None),mrr_at_5=1/rank if rank else 0,rerank_seconds=time.perf_counter()-t))
        (OUT/'retrieval.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
        print('RETRIEVED',case['id'],'source rank',rank,'used',len(used),flush=True)
    report=dict(n=10,recall_at_5=sum(r['recall_at_5'] for r in results)/10,mrr_at_5=sum(r['mrr_at_5'] for r in results)/10,
        elapsed_seconds=time.perf_counter()-start,threshold=0.05,top_k_dense=20,top_k_rerank=5,max_used_docs=3,
        models=models,index_sha256=hashlib.sha256(embpath.read_bytes()).hexdigest(),reference_mapping='predeclared source FAQ identifiers; not independent human retrieval labels')
    (OUT/'retrieval_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('DONE',report['recall_at_5'],report['mrr_at_5'],flush=True)
if __name__=='__main__':main()
