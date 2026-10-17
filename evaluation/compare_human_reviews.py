"""Validate two independent exported reviews; missing labels never become scores."""
import argparse,json
from pathlib import Path
from evaluation.checklist_judge import agreement,VERSION
KEYS=('fact','respectful','relevant','action','essential')
def load(path,expected_ids):
    obj=json.loads(path.read_text(encoding='utf-8'))
    if obj.get('protocol')!=VERSION or not obj.get('reviewer','').strip():raise ValueError('Missing reviewer or wrong protocol')
    rows=obj['ratings'];ratings={r['item_id']:r for r in rows}
    if len(rows)!=len(ratings) or set(ratings)!=expected_ids:raise ValueError('Duplicate, unknown or missing item IDs')
    for r in rows:
        for k in KEYS:
            allowed=('supported','contradicted','unknown','') if k=='fact' else ('pass','fail','unknown','')
            if r.get(k) not in allowed:raise ValueError('Invalid label '+k)
        if any(r[k] not in ('unknown','') for k in KEYS) and not r.get('rationale','').strip():raise ValueError('Known labels require rationale: '+r['item_id'])
    return obj['reviewer'],ratings
def kappa(a,b,key):
    pairs=[(a[i][key],b[i][key]) for i in a if a[i][key] not in ('','unknown') and b[i][key] not in ('','unknown')]
    if not pairs:return None
    labels=set(x for p in pairs for x in p);n=len(pairs)
    observed=sum(x==y for x,y in pairs)/n
    chance=sum(sum(x==v for x,y in pairs)*sum(y==v for x,y in pairs)/n**2 for v in labels)
    return (observed-chance)/(1-chance) if chance<1 else None
def main():
    ap=argparse.ArgumentParser();ap.add_argument('review_a',type=Path);ap.add_argument('review_b',type=Path);ap.add_argument('--machine',type=Path);args=ap.parse_args()
    out=Path(__file__).resolve().parents[1]/'evaluation/results/checklist-v3-001'
    ids={p['item_id'] for p in json.loads((out/'blind_items.json').read_text(encoding='utf-8'))}
    name_a,a=load(args.review_a,ids);name_b,b=load(args.review_b,ids)
    if name_a==name_b:raise ValueError('Two independent reviewer IDs required')
    disagreements=[dict(item_id=i,axis=k,a=a[i][k],b=b[i][k]) for i in a for k in KEYS if a[i][k]!=b[i][k]]
    result=dict(reviewers=[name_a,name_b],human_agreement=agreement(a,b,KEYS),
        per_axis={k:{**agreement(a,b,[k]),'cohen_kappa':kappa(a,b,k)} for k in KEYS},
        disagreements=disagreements,reference_approval='not inferred from answer ratings; still requires explicit case review')
    provisional=args.machine or out/'provisional_ratings.json'
    if provisional.exists():
        machine={r['item_id']:{**r['result']['content'],**r['result']['style']} for r in json.loads(provisional.read_text(encoding='utf-8')) if 'result' in r}
        consensus={i:{k:a[i][k] if a[i][k]==b[i][k] else None for k in KEYS} for i in a}
        result['machine_vs_unadjudicated_consensus']=agreement(machine,consensus,KEYS)
    (out/'human_agreement.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result['human_agreement'],ensure_ascii=False))
if __name__=='__main__':main()
