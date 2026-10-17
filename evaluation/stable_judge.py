"""Versioned, evidence-grounded judge contract; model-independent checks."""
from __future__ import annotations
import hashlib
import json
import re
import unicodedata

VERSION = 'grounded-v2-multipass-brand-number-fix'
AXES = ('accuracy', 'politeness', 'actionability', 'essential')

def normalize(text: str) -> str:
    # Preserve punctuation, word boundaries and numbers; only normalize layout.
    return ' '.join(unicodedata.normalize('NFKC', text).split())

def canonical_input(question, evidence, answer):
    docs = sorted(({'id':str(d['id']), 'text':normalize(d['text'])} for d in evidence), key=lambda d:d['id'])
    if len({d['id'] for d in docs}) != len(docs):
        raise ValueError('Duplicate evidence ID')
    return dict(question=normalize(question), evidence=docs, answer=normalize(answer))

def cache_key(payload, model_revision, prompt):
    body=dict(version=VERSION,model_revision=model_revision,prompt=prompt,input=payload)
    return hashlib.sha256(json.dumps(body,ensure_ascii=False,sort_keys=True).encode()).hexdigest()

def parse(raw):
    obj=json.loads(raw[raw.index('{'):raw.rindex('}')+1])
    for k in AXES:
        if type(obj.get(k)) is not int or not 1<=obj[k]<=5:
            raise ValueError('Expected integer score 1..5: '+k)
    for k in ('support_quote','unsupported_claim','reason'):
        if not isinstance(obj.get(k),str):raise ValueError('Expected string: '+k)
    if not obj['reason'].strip():raise ValueError('Missing rationale')
    return obj

def numbers(text):
    # Service-name digits are not opening hours, quantities or phone numbers.
    text=re.sub(r'또타\s*24','또타',unicodedata.normalize('NFKC',text))
    text=re.sub(r'(?<!\S)\d+[.)](?=\s)\s*','',text)
    return {re.sub(r'\D','',x) for x in re.findall(r'\d+(?:[-:.,]\d+)*',unicodedata.normalize('NFKC',text))}

def enforce(raw_scores,payload):
    """Conservative support/completeness policy, NOT a truth oracle.

    Absent numbers are unverified in supplied documents, not proven false.
    Store original ratings and explicit deterministic caps separately.
    """
    unsupported=normalize(raw_scores['unsupported_claim'])
    if unsupported and unsupported not in payload['answer']:
        raise ValueError('Unsupported claim must quote the ANSWER, not the question or evidence')
    result={k:raw_scores[k] for k in AXES}
    evidence='\n'.join(d['text'] for d in payload['evidence'])
    quote=normalize(raw_scores['support_quote'])
    valid_quote=bool(quote and any(quote in d['text'] for d in payload['evidence']))
    flags=[]
    def cap(axis,limit,flag):
        result[axis]=min(result[axis],limit)
        if flag not in flags:flags.append(flag)
    if not payload['evidence']:cap('accuracy',2,'no_reference_evidence')
    if not valid_quote:cap('accuracy',2,'missing_or_invalid_support_quote')
    if raw_scores['unsupported_claim'].strip():
        cap('accuracy',2,'judge_reports_unsupported_claim')
        cap('actionability',2,'unverified_claim_in_action_guidance')
    absent=sorted(numbers(payload['answer'])-numbers(evidence))
    if absent:
        cap('accuracy',2,'numbers_absent_from_reference')
        cap('actionability',2,'unverified_numeric_instructions')
    if re.search(r'\[(?:연락처번호|운영시간|전화번호|링크|URL)\]',payload['answer'],re.I):
        cap('actionability',2,'unfilled_placeholder');cap('essential',2,'unfilled_placeholder')
    if any(t in payload['answer'] for t in ('예비실험 길이 제한','답변 본문만 출력','JSON 하나만 출력')):
        cap('essential',2,'prompt_instruction_leak')
    return dict(scores=result,raw_scores=raw_scores,policy_flags=flags,
                valid_support_quote=valid_quote,unverified_numbers=absent,
                reason=raw_scores['reason'])
