"""Finite output grammar. Constrains syntax, never selects a preferred judgement."""
import json,re

def style_completions(answer):
    snippets={answer[:24],answer[-24:]}
    for chunk in re.split(r'[.!?。,\n]',answer):
        words=chunk.strip().split()
        for i in range(len(words)):
            snippets.add(' '.join(words[i:i+3])[:24])
    snippets=sorted(s for s in snippets if s and s in answer)
    if not snippets:snippets=['']
    return [json.dumps(dict(respectful=label,quote=q),ensure_ascii=False,separators=(',',':'))
        for label in ('pass','fail','unknown') for q in snippets]

def prefix_constraint(tokenizer,completions,prompt_length):
    trie={}
    for text in completions:
        ids=tokenizer.encode(text,add_special_tokens=False)
        node=trie
        for token in ids:node=node.setdefault(token,{})
        node[tokenizer.eos_token_id]={}
    def allowed(batch_id,input_ids):
        node=trie
        for token in input_ids.tolist()[prompt_length:]:
            if token not in node:raise ValueError('Output left finite grammar')
            node=node[token]
        return sorted(node) if node else [tokenizer.eos_token_id]
    return allowed
