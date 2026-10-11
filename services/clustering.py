"""또타24 FAQ 발굴 — KoSimCSE 임베딩 + UMAP + HDBSCAN + c-TF-IDF.

입력:  dataset/preprocessed_full.csv  (preprocess.py 산출물, 전체 행)
출력:  dataset/embeddings.npy         (N × 768 임베딩 행렬)
       dataset/clusters.csv           (logId, clusterId, umap_x, umap_y, text)
       dataset/cluster_summary.csv    (clusterId, size, isNoise, topKeywords)
       dataset/cluster_keywords.json  (clusterId → keywords[])
       dataset/clusters_umap2d.png    (UMAP 2D 산점도)

설계 원칙:
  - 라벨 기반 사전 필터 없음. HDBSCAN이 노이즈(-1)로 자연스럽게 분리.
  - 진짜 정보 0인 행(정규화 후 길이 ≤1)만 임베딩 전 제외.
  - 라벨은 평가 단계의 ground truth로만 사용.
"""
from __future__ import annotations

import json
import re
import time
import unicodedata
from collections import defaultdict
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from transformers import AutoTokenizer, AutoModel
import umap
import hdbscan
from sklearn.feature_extraction.text import CountVectorizer, TfidfTransformer
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def _nfd(p: Path) -> Path:
    """NFD(자모 분리) 디렉토리명 정규화."""
    return Path(unicodedata.normalize('NFD', str(p)))


# 0. 경로/모델/하이퍼파라미터
DATA_DIR = _nfd(Path(__file__).resolve().parent.parent / 'dataset')
INPUT_CSV = DATA_DIR / 'preprocessed_full.csv'

MODEL_NAME = 'BM-K/KoSimCSE-roberta'
DEVICE = 'cuda' if torch.cuda.is_available() else 'cpu'
BATCH_SIZE = 32
MAX_LENGTH = 128

UMAP_CLUSTER_DIM = 10
UMAP_VIZ_DIM = 2
UMAP_NEIGHBORS = 15

HDBSCAN_MIN_CLUSTER_SIZE = 5
HDBSCAN_MIN_SAMPLES = 2

TOP_N_KEYWORDS = 7
RANDOM_STATE = 42

# 사전 필터: 정규화 후 의미 토큰 1개 이하면 임베딩 의미 없음 (이모티콘만, "ㅋ", "." 등)
MIN_NORMALIZED_LEN = 2


# 1. 데이터 로드 + 사전 필터
def load() -> pd.DataFrame:
    df = pd.read_csv(INPUT_CSV, encoding='utf-8-sig')
    before = len(df)
    df = df[df['normalizedForClustering'].notna() &
            (df['normalizedForClustering'].astype(str).str.len() >= MIN_NORMALIZED_LEN)
           ].reset_index(drop=True)
    print(f'사전 필터: {before}행 → {len(df)}행 (정규화 길이 < {MIN_NORMALIZED_LEN} 제외 {before - len(df)}건)')
    return df


# 2. KoSimCSE 임베딩 — [CLS] pooling
def load_model():
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModel.from_pretrained(MODEL_NAME).to(DEVICE).eval()
    return tokenizer, model


@torch.no_grad()
def encode(texts: list[str], tokenizer, model) -> np.ndarray:
    all_emb = []
    for i in range(0, len(texts), BATCH_SIZE):
        batch = texts[i:i+BATCH_SIZE]
        inp = tokenizer(batch, padding=True, truncation=True,
                        max_length=MAX_LENGTH, return_tensors='pt').to(DEVICE)
        out = model(**inp)
        emb = out.last_hidden_state[:, 0, :]
        all_emb.append(emb.cpu().numpy())
    return np.vstack(all_emb)


# 3. UMAP — 클러스터링용 10D + 시각화용 2D
def reduce_dims(embeddings: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    reducer_clu = umap.UMAP(n_components=UMAP_CLUSTER_DIM, n_neighbors=UMAP_NEIGHBORS,
                            min_dist=0.0, metric='cosine', random_state=RANDOM_STATE)
    emb_clu = reducer_clu.fit_transform(embeddings)

    reducer_viz = umap.UMAP(n_components=UMAP_VIZ_DIM, n_neighbors=UMAP_NEIGHBORS,
                            min_dist=0.1, metric='cosine', random_state=RANDOM_STATE)
    emb_viz = reducer_viz.fit_transform(embeddings)
    return emb_clu, emb_viz


# 4. HDBSCAN — 노이즈는 -1
def cluster(emb_clu: np.ndarray) -> np.ndarray:
    clusterer = hdbscan.HDBSCAN(
        min_cluster_size=HDBSCAN_MIN_CLUSTER_SIZE,
        min_samples=HDBSCAN_MIN_SAMPLES,
        metric='euclidean',
        cluster_selection_method='eom',
    )
    return clusterer.fit_predict(emb_clu)


# 5. c-TF-IDF — 클러스터별 키워드
KOREAN_STOPWORDS = {
    '은', '는', '이', '가', '을', '를', '에', '의', '도', '만', '와', '과',
    '로', '으로', '부터', '까지', '에서', '하다', '이다', '있다', '없다',
    '요', '네', '입니다', '해주세요', '주세요', '됩니다', '합니다',
    '어떻게', '왜', '뭐', '무엇', '어디', '언제', '누가', '저', '그',
    '좀', '제발', '진짜', '정말', '그냥', '너무', '한번', '그리고',
    '하시', '하지', '있는', '있나', '있어요', '있습니다',
}
DOMAIN_STOPWORDS = {
    '지하철', '열차', '역', '또타', '또타24', '챗봇', '민원', '고객',
    '안녕하세요', '안녕', '수고', '감사합니다', '문의', '질문',
}
STOPWORDS = KOREAN_STOPWORDS | DOMAIN_STOPWORDS

RE_TOKEN = re.compile(r'\[[A-Z_]+\]|[가-힣]{2,}|[a-zA-Z]{2,}')


def korean_tokenizer(text: str) -> list[str]:
    return [w for w in RE_TOKEN.findall(text) if w not in STOPWORDS]


def extract_keywords(texts: list[str], cluster_labels: np.ndarray
                     ) -> tuple[dict[int, list[str]], dict[int, list[str]]]:
    cluster_docs: dict[int, list[str]] = defaultdict(list)
    for t, cid in zip(texts, cluster_labels):
        cluster_docs[int(cid)].append(t)

    cluster_ids = sorted(cluster_docs.keys())
    concat_docs = [' '.join(cluster_docs[cid]) for cid in cluster_ids]

    cv = CountVectorizer(tokenizer=korean_tokenizer, min_df=1, token_pattern=None)
    counts = cv.fit_transform(concat_docs)
    tfidf = TfidfTransformer(smooth_idf=True, sublinear_tf=True).fit_transform(counts)

    vocab = cv.get_feature_names_out()
    keywords: dict[int, list[str]] = {}
    for i, cid in enumerate(cluster_ids):
        row = tfidf[i].toarray().flatten()
        top_idx = row.argsort()[::-1][:TOP_N_KEYWORDS]
        keywords[cid] = [vocab[j] for j in top_idx if row[j] > 0]
    return keywords, dict(cluster_docs)


# 6. 시각화
def plot_2d(emb_viz: np.ndarray, cluster_labels: np.ndarray, n_clusters: int, out_path: Path):
    fig, ax = plt.subplots(figsize=(11, 7))
    unique_cids = sorted(set(cluster_labels.tolist()))
    cmap = plt.cm.get_cmap('tab20', max(len(unique_cids) - 1, 1))

    color_idx = 0
    for cid in unique_cids:
        mask = cluster_labels == cid
        if cid == -1:
            ax.scatter(emb_viz[mask, 0], emb_viz[mask, 1], c='lightgray',
                       s=15, alpha=0.4, label=f'noise (n={mask.sum()})')
        else:
            ax.scatter(emb_viz[mask, 0], emb_viz[mask, 1], c=[cmap(color_idx)],
                       s=25, alpha=0.8, label=f'C{cid} (n={mask.sum()})')
            color_idx += 1

    ax.set_title(f'HDBSCAN clusters on UMAP 2D ({n_clusters} clusters + noise)')
    ax.set_xlabel('UMAP-1')
    ax.set_ylabel('UMAP-2')
    if len(unique_cids) <= 20:
        ax.legend(loc='center left', bbox_to_anchor=(1.01, 0.5), fontsize=8)
    ax.grid(True, alpha=0.2)
    plt.tight_layout()
    plt.savefig(out_path, dpi=150, bbox_inches='tight')
    plt.close()


# 7. 산출물 저장
def save(df: pd.DataFrame, embeddings: np.ndarray, emb_viz: np.ndarray,
         cluster_labels: np.ndarray, keywords: dict[int, list[str]],
         cluster_docs: dict[int, list[str]]) -> dict[str, Path]:
    paths = {
        'emb': DATA_DIR / 'embeddings.npy',
        'clu': DATA_DIR / 'clusters.csv',
        'sum': DATA_DIR / 'cluster_summary.csv',
        'kw': DATA_DIR / 'cluster_keywords.json',
    }

    np.save(paths['emb'], embeddings)

    pd.DataFrame({
        'logId': df['logId'].tolist(),
        'clusterId': cluster_labels.astype(int),
        'umap_x': emb_viz[:, 0],
        'umap_y': emb_viz[:, 1],
        'text': df['normalizedForClustering'].astype(str).tolist(),
    }).to_csv(paths['clu'], index=False, encoding='utf-8-sig')

    summary = pd.DataFrame([
        {
            'clusterId': cid,
            'size': len(cluster_docs[cid]),
            'isNoise': cid == -1,
            'topKeywords': ', '.join(keywords[cid][:5]),
        }
        for cid in sorted(keywords.keys())
    ]).sort_values(by=['isNoise', 'size'], ascending=[True, False]).reset_index(drop=True)
    summary.to_csv(paths['sum'], index=False, encoding='utf-8-sig')

    with open(paths['kw'], 'w', encoding='utf-8') as f:
        json.dump({str(k): v for k, v in keywords.items()}, f, ensure_ascii=False, indent=2)

    return paths


# 8. 리포트
def report(cluster_labels: np.ndarray, keywords: dict[int, list[str]],
           cluster_docs: dict[int, list[str]]):
    n_clusters = len(set(cluster_labels.tolist())) - (1 if -1 in cluster_labels else 0)
    n_noise = int((cluster_labels == -1).sum())
    print(f'클러스터: {n_clusters}개  /  노이즈(-1): {n_noise}건 ({n_noise/len(cluster_labels):.1%})')
    print()
    items = sorted(
        [(cid, len(cluster_docs[cid])) for cid in keywords],
        key=lambda x: (x[0] == -1, -x[1])
    )[:13]
    for cid, size in items:
        kws = keywords[cid][:5]
        tag = 'noise   ' if cid == -1 else f'cluster {cid:>2}'
        print(f'  [{tag}] n={size:>3}  keywords: {kws}')
        for s in cluster_docs[cid][:3]:
            print(f'      · {s[:70]}')
        print()


def main() -> None:
    df = load()
    texts = df['normalizedForClustering'].astype(str).tolist()
    print(f'GPU: {torch.cuda.is_available()} '
          f'({torch.cuda.get_device_name(0) if torch.cuda.is_available() else "cpu"})')
    print()

    print(f'[1/4] KoSimCSE 임베딩 ({MODEL_NAME})')
    t0 = time.time()
    tokenizer, model = load_model()
    embeddings = encode(texts, tokenizer, model)
    print(f'  shape={embeddings.shape}, {time.time()-t0:.1f}s')

    print(f'[2/4] UMAP 축소 (768 → {UMAP_CLUSTER_DIM}D / {UMAP_VIZ_DIM}D)')
    t0 = time.time()
    emb_clu, emb_viz = reduce_dims(embeddings)
    print(f'  clustering={emb_clu.shape}, viz={emb_viz.shape}, {time.time()-t0:.1f}s')

    print(f'[3/4] HDBSCAN (min_cluster_size={HDBSCAN_MIN_CLUSTER_SIZE})')
    t0 = time.time()
    cluster_labels = cluster(emb_clu)
    print(f'  {time.time()-t0:.1f}s')

    print('[4/4] c-TF-IDF 키워드')
    keywords, cluster_docs = extract_keywords(texts, cluster_labels)

    print()
    report(cluster_labels, keywords, cluster_docs)

    print('시각화:')
    viz_path = DATA_DIR / 'clusters_umap2d.png'
    n_clusters = len(set(cluster_labels.tolist())) - (1 if -1 in cluster_labels else 0)
    plot_2d(emb_viz, cluster_labels, n_clusters, viz_path)
    print(f'  {viz_path.stat().st_size:>10,} bytes  {viz_path.name}')
    print()

    print('산출물:')
    paths = save(df, embeddings, emb_viz, cluster_labels, keywords, cluster_docs)
    for p in paths.values():
        print(f'  {p.stat().st_size:>10,} bytes  {p.name}')


if __name__ == '__main__':
    main()
