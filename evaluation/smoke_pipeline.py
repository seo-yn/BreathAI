"""Isolated, offline smoke run using existing real clusters and a file-only KB.

This reuses clustering artifacts; it does not measure clustering or final quality.
No model download, production database access, or operational output writes.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import os
from pathlib import Path
import shutil
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run-dir', type=Path, required=True)
    parser.add_argument('--clusters', type=int, default=5)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    run = args.run_dir.resolve()
    if not run.is_relative_to(root / 'evaluation' / 'results'):
        parser.error('run-dir must be inside evaluation/results')
    if args.clusters < 1:
        parser.error('clusters must be positive')
    run.mkdir(parents=True, exist_ok=False)
    data, logs = run / 'dataset', run / 'logs'
    data.mkdir()
    logs.mkdir()
    report = {'status': 'running', 'scope': 'existing clusters -> synth -> retrieval -> answer/refine',
              'clustering_rerun': False, 'kb_source': 'handcrafted files only; no DB',
              'offline': True, 'stages': [], 'source_hashes': {}}

    def save():
        (run / 'run_report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')

    stage, started = 'prepare', time.perf_counter()
    try:
        import pandas as pd
        from services.faq_kb import load_handcrafted_faqs
        source = root / 'dataset'
        for name in ['clusters.csv', 'cluster_summary.csv', 'cluster_keywords.json',
                     'answer_templates.json', '일반상담.xlsx', 'counselling_info.csv']:
            report['source_hashes'][name] = hashlib.sha256((source / name).read_bytes()).hexdigest()
        summary = pd.read_csv(source / 'cluster_summary.csv', encoding='utf-8-sig')
        selected = summary[(summary['clusterId'] != -1) & (summary['size'] >= 5)].sort_values('clusterId').head(args.clusters)
        if len(selected) != args.clusters:
            raise ValueError('Not enough eligible clusters')
        ids = selected.clusterId.astype(int).tolist()
        clusters = pd.read_csv(source / 'clusters.csv', encoding='utf-8-sig')
        clusters = clusters[clusters.clusterId.isin(ids)]
        clusters.to_csv(data / 'clusters.csv', index=False, encoding='utf-8-sig')
        selected.to_csv(data / 'cluster_summary.csv', index=False, encoding='utf-8-sig')
        keywords = json.loads((source / 'cluster_keywords.json').read_text(encoding='utf-8'))
        (data / 'cluster_keywords.json').write_text(json.dumps({str(i): keywords[str(i)] for i in ids}, ensure_ascii=False), encoding='utf-8')
        shutil.copyfile(source / 'answer_templates.json', data / 'answer_templates.json')
        kb, counts = load_handcrafted_faqs(source / '일반상담.xlsx', source / 'counselling_info.csv')
        if not kb:
            raise ValueError('Empty KB')
        (data / 'kb_snapshot.json').write_text(json.dumps(kb, ensure_ascii=False, indent=2), encoding='utf-8')
        report.update(cluster_ids=ids, log_count=len(clusters), kb_count=len(kb), kb_counts=counts)
        report['stages'].append({'stage': stage, 'status': 'ok', 'seconds': time.perf_counter()-started})
        save()
        print(f'Prepared {len(ids)} clusters / {len(clusters)} logs / {len(kb)} FAQ', flush=True)

        os.environ['HF_HUB_OFFLINE'] = '1'
        os.environ['TRANSFORMERS_OFFLINE'] = '1'
        stage, started = 'module_import', time.perf_counter()
        modules = [importlib.import_module('services.' + name) for name in
                   ['faq_generator', 'retriever', 'answer_regenerator', 'answer_evaluator',
                    'answer_refiner', 'oos_refresher', 'run_pipeline']]
        for module in modules:
            for key, value in list(vars(module).items()):
                if isinstance(value, Path):
                    if value.is_relative_to(source):
                        setattr(module, key, data / value.relative_to(source))
                    elif value.is_relative_to(root / 'logs'):
                        setattr(module, key, logs / value.relative_to(root / 'logs'))
        faq, retriever, *_, pipeline = modules
        retriever.load_kb = lambda: kb
        from transformers import set_seed
        import torch
        set_seed(42)
        report.update(seed=42, generator=faq.MODEL_NAME, torch=torch.__version__,
                      cuda=torch.cuda.is_available(), generation_max_tokens=faq.MAX_NEW_TOKENS)
        report['stages'].append({'stage': stage, 'status': 'ok', 'seconds': time.perf_counter()-started})
        save()
        for stage, action in [('synthesis', faq.main), ('retrieval', retriever.main),
                              ('answer_refine', pipeline.run_answer_pipeline)]:
            started = time.perf_counter()
            print(f'Start {stage}', flush=True)
            action()
            report['stages'].append({'stage': stage, 'status': 'ok', 'seconds': time.perf_counter()-started})
            save()
        report['status'] = 'ok'
        save()
    except Exception as exc:
        report['status'] = 'failed'
        report['stages'].append({'stage': stage, 'status': 'failed',
                                 'seconds': time.perf_counter()-started,
                                 'error_type': type(exc).__name__, 'error': str(exc)})
        save()
        print(f'FAILED {stage}: {type(exc).__name__}: {exc}', flush=True)
        raise SystemExit(1)


if __name__ == '__main__':
    main()
