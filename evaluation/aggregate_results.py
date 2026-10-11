"""Validate and aggregate saved evaluation records (Python standard library only)."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
import statistics
from pathlib import Path

JUDGE_METRICS = ('accuracy', 'politeness', 'actionability', 'essential')
METRICS = ('recall_at_5', 'mrr_at_5', 'cross_encoder', *JUDGE_METRICS)


def read_jsonl(path):
    return [json.loads(line) for line in Path(path).read_text(encoding='utf-8-sig').splitlines() if line.strip()]


def unique_index(rows, key):
    result = {}
    for row in rows:
        value = row[key]
        if not isinstance(value, str) or not value.strip() or value in result:
            raise ValueError(f'Invalid or duplicate {key}: {value!r}')
        result[value] = row
    if not result:
        raise ValueError('Empty dataset')
    return result


def number(value, low, high):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value) or not low <= value <= high:
        raise ValueError(f'Expected finite number in [{low}, {high}]: {value!r}')
    return value


def retrieval_scores(retrieved_ids, truth):
    """Single human-labelled answer; truncated MRR, not unrestricted MRR."""
    if len(retrieved_ids) != len(set(retrieved_ids)):
        raise ValueError('Duplicate retrieval IDs')
    if truth is None:
        return None, None
    rank = next((i for i, value in enumerate(retrieved_ids[:5], 1) if value == truth), None)
    return (1.0, 1.0 / rank) if rank else (0.0, 0.0)


def evaluate(queries, kb, outputs, systems):
    queries = unique_index(queries, 'id')
    kb = unique_index(kb, 'id')
    if not systems or len(systems) != len(set(systems)):
        raise ValueError('Expected unique systems')
    for query in queries.values():
        truth = query.get('ground_truth_faq_id')
        if truth is not None and truth not in kb:
            raise ValueError(f'Unknown ground truth: {truth}')
    seen, raw = set(), []
    for output in outputs:
        key = output['test_id'], output['system']
        if key in seen or key[0] not in queries or key[1] not in systems:
            raise ValueError(f'Duplicate or unexpected output: {key}')
        seen.add(key)
        status = output['status']
        if status not in ('ok', 'failed'):
            raise ValueError(f'Invalid status: {status}')
        row = dict(test_id=key[0], system=key[1], status=status)
        row.update({metric: None for metric in METRICS})
        if status == 'failed':
            if not output.get('error'):
                raise ValueError('Failed output must include error')
            raw.append(row)
            continue
        if not output.get('standard_question', '').strip() or not output.get('answer', '').strip():
            raise ValueError('Successful output needs question and answer')
        retrieved = output['retrieved']
        if not 1 <= len(retrieved) <= 5:
            raise ValueError('Successful output must have 1..5 evaluation retrieval documents')
        ids = [doc['id'] for doc in retrieved]
        if any(doc_id not in kb for doc_id in ids):
            raise ValueError('Retrieved ID outside frozen KB')
        row['recall_at_5'], row['mrr_at_5'] = retrieval_scores(ids, queries[key[0]].get('ground_truth_faq_id'))
        row['cross_encoder'] = statistics.mean(number(doc['cross_encoder'], 0, 1) for doc in retrieved)
        judge = output['judge']
        if not judge.get('reason', '').strip():
            raise ValueError('Judge reason is required')
        for metric in JUDGE_METRICS:
            value = number(judge[metric], 1, 5)
            if int(value) != value:
                raise ValueError('Judge scores must be integers')
            row[metric] = value
        raw.append(row)
    expected = {(test_id, system) for test_id in queries for system in systems}
    if seen != expected:
        raise ValueError(f'Missing outputs: {sorted(expected - seen)}; record failures explicitly')
    summary = []
    for system in systems:
        rows = [row for row in raw if row['system'] == system]
        failed = sum(row['status'] == 'failed' for row in rows)
        for metric in METRICS:
            values = [row[metric] for row in rows if row[metric] is not None]
            summary.append(dict(system=system, metric=metric, n=len(values), total=len(rows),
                                failed=failed, failure_rate=failed / len(rows),
                                mean=statistics.mean(values) if values else None,
                                std=statistics.stdev(values) if len(values) > 1 else None))
    return raw, summary


def write_csv(path, rows):
    with path.open('w', encoding='utf-8-sig', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--queries', type=Path, required=True)
    parser.add_argument('--kb', type=Path, required=True)
    parser.add_argument('--outputs', type=Path, required=True)
    parser.add_argument('--systems', nargs='+', required=True)
    parser.add_argument('--result-dir', type=Path, required=True)
    args = parser.parse_args()
    kb = json.loads(args.kb.read_text(encoding='utf-8-sig'))
    raw, summary = evaluate(read_jsonl(args.queries), kb, read_jsonl(args.outputs), args.systems)
    # A fresh directory prevents mixing runs or overwriting past results.
    args.result_dir.mkdir(parents=True, exist_ok=False)
    write_csv(args.result_dir / 'raw_scores.csv', raw)
    write_csv(args.result_dir / 'summary.csv', summary)
    provenance = {name: {'path': str(path.resolve()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
                  for name, path in [('queries', args.queries), ('kb', args.kb), ('outputs', args.outputs)]}
    provenance['systems'] = args.systems
    (args.result_dir / 'inputs.json').write_text(json.dumps(provenance, ensure_ascii=False, indent=2), encoding='utf-8')
    print(f'Validated {len(raw)} records; results: {args.result_dir}')


if __name__ == '__main__':
    main()
