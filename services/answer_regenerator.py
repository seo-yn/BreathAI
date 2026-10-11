"""또타24 답변 재생성기 — RAG + 템플릿 기반.

입력:
  dataset/faq_candidates.json       : 81 클러스터 (tag, standardQuestion, answerDraft 등)
  dataset/faq_retrieval.json        : FAQ별 top-5 KB 청크 (BGE-M3 + reranker)
  dataset/answer_templates.json     : 태그별 prefix/body_guideline/suffix + _shared

출력:
  dataset/faq_candidates_v2.json    : answerDraft 재생성본
  dataset/answer_regen.jsonl        : per-cluster 체크포인트
  dataset/answer_regen_prompts.txt  : (DRY_RUN) 프롬프트만 덤프

설계 원칙:
  1) 답변 = template.prefix + LLM(본문) + template.suffix
  2) FAQ는 RAG: 검색된 KB 청크가 있으면 근거로 인용, 점수 낮으면 fallback 모드
  3) EMERGENCY/OUT_OF_SCOPE/REJECT는 KB 없이도 템플릿 가이드만으로 본문 생성
  4) tag별 formality / citation_style / default_contacts를 프롬프트에 주입
"""
from __future__ import annotations

import json
import re
import time
import unicodedata
from pathlib import Path

import torch
from transformers import AutoTokenizer, AutoModelForCausalLM
from services.retrieval_contract import index_retrieval, bind_retrieval, select_evidence, evidence_label


# ---------------------------------------------------------------------------
# 경로 / 상수
# ---------------------------------------------------------------------------
def _nfd(p: Path) -> Path:
    return Path(unicodedata.normalize('NFD', str(p)))


DATA_DIR = _nfd(Path(__file__).resolve().parent.parent / 'dataset')
LOG_DIR = _nfd(Path(__file__).resolve().parent.parent / 'logs')
CANDIDATES_IN = DATA_DIR / 'faq_candidates.json'
RETRIEVAL_IN = DATA_DIR / 'faq_retrieval.json'
TEMPLATES_IN = DATA_DIR / 'answer_templates.json'

OUTPUT_JSON = DATA_DIR / 'faq_candidates_v2.json'
OUTPUT_JSONL = DATA_DIR / 'answer_regen.jsonl'
RUN_LOG = LOG_DIR / 'answer_regen.log'              # 한 줄 요약 (stdout 미러)
DETAIL_LOG = LOG_DIR / 'answer_regen_detail.log'    # 프롬프트 + LLM output + 최종답변
PROMPT_DUMP = LOG_DIR / 'answer_regen_prompts.txt'  # DRY_RUN 전용

MODEL_NAME = 'Qwen/Qwen2.5-7B-Instruct'
DEVICE = 'cuda' if torch.cuda.is_available() else 'cpu'
DTYPE = torch.bfloat16

MAX_NEW_TOKENS = 600
TEMPERATURE = 0.3
TOP_P = 0.9

# RAG 품질 게이트: 이보다 낮으면 KB 무근거로 간주
KB_SCORE_THRESHOLD = 0.05
MAX_KB_CHUNKS = 5  # 검색 Top-5와 동일; threshold 미만은 기존 정책대로 제외

# 실행 모드
DRY_RUN = False  # True면 LLM 호출 안 하고 프롬프트만 덤프


# ---------------------------------------------------------------------------
# I/O
# ---------------------------------------------------------------------------
def load_candidates() -> list[dict]:
    return json.loads(CANDIDATES_IN.read_text(encoding='utf-8'))['faqCandidates']


def load_retrieval() -> dict[int, dict]:
    """clusterLabel -> retrieval record"""
    return index_retrieval(json.loads(RETRIEVAL_IN.read_text(encoding='utf-8')))


def load_templates() -> dict:
    return json.loads(TEMPLATES_IN.read_text(encoding='utf-8'))


# ---------------------------------------------------------------------------
# 템플릿 보조
# ---------------------------------------------------------------------------
def resolve_contact(shared: dict, key: str) -> str:
    """contacts 풀에서 키로 값 추출. 'external.korail' 같은 점 경로 지원."""
    node = shared['contacts']
    for part in key.split('.'):
        node = node[part]
    return node


def select_kb_chunks(retrieval: dict | None) -> list[dict]:
    """rerank_score 기준 threshold 이상만 반환."""
    return select_evidence(retrieval, KB_SCORE_THRESHOLD, MAX_KB_CHUNKS)


# ---------------------------------------------------------------------------
# 프롬프트 빌더
# ---------------------------------------------------------------------------
def build_prompt(cluster: dict, tag_cfg: dict, shared: dict,
                 kb_chunks: list[dict]) -> str:
    """LLM에 줄 프롬프트. 인사~마무리까지 자연스럽게 통합된 답변 한 통을 생성."""
    tag = cluster.get('tag', '')

    # OUT_OF_SCOPE + oos_case 지정 시: tag_cfg를 케이스별 override
    if tag == 'OUT_OF_SCOPE' and cluster.get('oos_case') and 'cases' in tag_cfg:
        case_key = cluster['oos_case']
        case_cfg = tag_cfg['cases'].get(case_key)
        if case_cfg:
            tag_cfg = dict(tag_cfg)
            tag_cfg['body_guideline'] = case_cfg['body_guideline']
            tag_cfg['_case_key'] = case_key
            tag_cfg['_case_description'] = case_cfg['description']
            tag_cfg['_forbidden_mentions'] = case_cfg.get('forbidden_mentions', [])
            # default_contacts를 케이스 contacts로 교체
            tag_cfg['default_contacts'] = case_cfg.get('contacts', tag_cfg.get('default_contacts', []))

    formality_desc = templates_meta['formality_levels'][tag_cfg['formality']]
    citations = shared['citation_phrases'].get(tag_cfg['citation_style'], [])
    contacts = [resolve_contact(shared, k) for k in tag_cfg['default_contacts']]
    question = cluster.get('standardQuestion') or '(표준질문 없음 — 클러스터 샘플 참고)'

    kb_block = '(검색된 KB 근거 없음 — 답변에서 "고객센터로 문의" 식으로 우회)' \
        if not kb_chunks \
        else '\n\n'.join(
            f'[근거 {i+1}] ({evidence_label(c)}, score={c["rerank_score"]:.3f})\n{c["text"]}'
            for i, c in enumerate(kb_chunks)
        )

    citation_block = '\n'.join(f'  - "{p}"' for p in citations) if citations else '  (해당 없음)'
    contact_block = '\n'.join(f'  - {c}' for c in contacts)

    # 태그별 필수 요소 (검증 함수와 정확히 연동)
    must_items = ['"또타" 또는 "또타24"를 1회 이상 자연스럽게 언급']
    extras = ''
    if tag == 'EMERGENCY':
        must_items += [
            '사과 표현 (예: "죄송합니다" / "안타깝게 생각합니다" / "불편을 드려")',
            '도입부는 "안녕하십니까"로 시작',
            '안내 가능한 연락처 또는 즉시 조치 정보를 본문에 포함',
        ]
        extras = '\n- 격식체(-습니다/-드립니다) 종결 어미 일관 유지. 친근체(-해요/-요) 사용 금지.'
    elif tag == 'OUT_OF_SCOPE':
        case_key = tag_cfg.get('_case_key')
        case_desc = tag_cfg.get('_case_description', '')
        forbidden = tag_cfg.get('_forbidden_mentions', [])
        if case_key:
            must_items += [
                f'본 답변은 "{case_key}" 케이스로 분류됨 — 위 [답변 구성 가이드]만 따르고 다른 케이스 가이드는 적용 금지',
                '해당 문의가 서울교통공사 직접 소관이 아니라는 점을 정중히 명시',
            ]
            if forbidden:
                must_items.append(
                    f'다음 단어/기관은 답변에 절대 등장 금지: {", ".join(forbidden)}'
                )
            extras = (
                f'\n- 분류된 케이스: {case_key} — {case_desc}'
                '\n- 위 케이스의 가이드만 따라 한 가지 종류 안내로 일관되게 작성하라. 여러 케이스의 안내를 섞지 말 것.'
            )
        else:
            must_items += [
                '사용자가 명시적으로 언급한 외부기관만 안내 (임의 추가 금지)',
                '해당 문의가 서울교통공사 직접 소관이 아니라는 점을 정중히 명시',
            ]
            extras = ''
    must_block = '\n'.join(f'  - {m}' for m in must_items)

    # OUT_OF_SCOPE는 standardQuestion이 비어있는 경우가 많아 topKeywords+원본 샘플을 추가 노출
    oos_context = ''
    if tag == 'OUT_OF_SCOPE':
        kws = ', '.join(cluster.get('topKeywords', [])[:8]) or '(없음)'
        samples_raw = cluster.get('synonyms', [])[:6]
        samples_block = '\n'.join(f'  - {s}' for s in samples_raw) if samples_raw else '  (없음)'
        oos_context = (
            f'\n\n[클러스터 키워드 — 케이스 판별에 사용]\n{kws}'
            f'\n\n[원본 사용자 샘플 (대표)]\n{samples_block}'
        )

    return f"""너는 서울교통공사 챗봇 '또타24'다. 아래 질문에 대해 **인사 → 안내 → 마무리**가 매끄럽게 이어지는 답변 한 통을 자연스럽게 작성하라. (이음새가 어색하지 않게 한 덩어리로)

[페르소나]
{templates_meta['persona']}

[격식 레벨]
{tag_cfg['formality']} — {formality_desc}

[질문 유형 (tag)]
{tag} — {tag_cfg['description']}

[사용자 표준질문]
{question}{oos_context}

[참고 KB 청크]
{kb_block}

[답변 구성 가이드]
{tag_cfg['body_guideline']}

[참고 — 도입부 톤 예시 (그대로 복사 X, 변주하라)]
"{tag_cfg['prefix']}"

[참고 — 마무리 톤 예시 (그대로 복사 X, 변주하라)]
"{tag_cfg['suffix']}"

[근거 인용 표현 예시 — 적절히 사용]
{citation_block}

[안내 가능한 연락처 — 필요시 본문 안에 자연스럽게 언급]
{contact_block}

[필수 요소]
{must_block}{extras}

[작성 규칙]
- 도입부 예시의 톤을 참고하되, 똑같이 복사하지 말고 질문에 맞춰 자연스럽게 변주하라.
- KB 근거에 없는 사실은 만들어내지 말 것. 추측·과장 금지.
- 격식 레벨에 맞는 종결 어미를 일관되게 사용하라.
- 답변 분량: 5~9문장. 인사+안내+마무리가 한 덩어리로 자연스러워야 함.
- 마크다운/JSON/코드블록/리스트 기호 사용 금지. 일반 산문으로 작성.
- 답변 텍스트 외 메타·설명·헤더 절대 출력 금지.

[답변]
"""


# ---------------------------------------------------------------------------
# LLM
# ---------------------------------------------------------------------------
def load_model() -> tuple:
    print(f'모델 로드: {MODEL_NAME}')
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        MODEL_NAME, torch_dtype=DTYPE, device_map='auto', trust_remote_code=True,
    )
    return tokenizer, model


@torch.no_grad()
def generate(prompt: str, tokenizer, model) -> str:
    messages = [
        {'role': 'system', 'content': '당신은 또타24 챗봇 답변 본문 작성자입니다.'},
        {'role': 'user', 'content': prompt},
    ]
    inputs = tokenizer.apply_chat_template(
        messages, add_generation_prompt=True, return_tensors='pt'
    ).to(DEVICE)
    out = model.generate(
        inputs,
        max_new_tokens=MAX_NEW_TOKENS,
        temperature=TEMPERATURE,
        top_p=TOP_P,
        do_sample=True,
        eos_token_id=tokenizer.eos_token_id,
        pad_token_id=tokenizer.eos_token_id,
    )
    text = tokenizer.decode(out[0][inputs.shape[1]:], skip_special_tokens=True)
    return clean_body(text)


def build_retry_prompt(orig_prompt: str, prev_answer: str, missing: list[str]) -> str:
    """검증 실패 시: 이전 답변을 보존하면서 누락만 보완하도록 요청."""
    return orig_prompt.rstrip() + f"""

[직전 시도의 답변 — 참고]
{prev_answer}

[수정 요청]
위 답변에서 다음 항목이 누락되었다: {', '.join(missing)}
누락된 항목을 반드시 보완하되, 직전 답변의 내용·분량(5~9문장)·자연스러운 흐름은 그대로 유지하라.
처음부터 다시 한 통의 답변으로 작성하라. 마크다운/메타 텍스트 금지.

[수정된 답변]
"""


def clean_body(text: str) -> str:
    """LLM이 가끔 붙이는 잡음(코드블록, '답변:', 빈 줄 과다) 제거."""
    text = re.sub(r'```.*?```', '', text, flags=re.DOTALL).strip()
    text = re.sub(r'^(답변|본문|응답)\s*[:：]\s*', '', text)
    text = re.sub(r'\n{3,}', '\n\n', text).strip()
    return text


# ---------------------------------------------------------------------------
# 답변 조립
# ---------------------------------------------------------------------------
def assemble_answer_fallback(body: str, tag_cfg: dict) -> str:
    """검증 실패 시 fallback: 기존 조립식 구조로 안전하게 반환."""
    return f"{tag_cfg['prefix']}\n\n{body}\n\n{tag_cfg['suffix']}"


# 검증: 필수 표현이 포함됐는지
APOLOGY_PAT = re.compile(r'(죄송|사과|안타까|불편을 드려)')

def validate_answer(text: str, tag: str) -> tuple[bool, list[str]]:
    missing = []
    if '또타' not in text:
        missing.append('"또타" 누락')
    if tag == 'EMERGENCY' and not APOLOGY_PAT.search(text):
        missing.append('사과 표현 누락')
    if tag == 'EMERGENCY' and '안녕하십니까' not in text:
        missing.append('격식체 인사 누락')
    if len(text.strip()) < 80:
        missing.append('답변이 너무 짧음')
    return (len(missing) == 0, missing)


# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------
templates_meta: dict = {}  # main()에서 채워짐


def main() -> None:
    global templates_meta

    candidates = load_candidates()
    retrieval = load_retrieval()
    candidates = bind_retrieval(candidates, retrieval)
    templates = load_templates()
    templates_meta = templates['_meta']
    shared = templates['_shared']

    print(f'클러스터 {len(candidates)}개 / 검색결과 {len(retrieval)}개 / DRY_RUN={DRY_RUN}')

    # 디렉터리/로그 초기화
    LOG_DIR.mkdir(exist_ok=True)
    if OUTPUT_JSONL.exists():
        OUTPUT_JSONL.unlink()
    if DETAIL_LOG.exists():
        DETAIL_LOG.unlink()
    if RUN_LOG.exists():
        RUN_LOG.unlink()
    if DRY_RUN and PROMPT_DUMP.exists():
        PROMPT_DUMP.unlink()

    def _log_summary(msg: str) -> None:
        print(msg)
        with RUN_LOG.open('a', encoding='utf-8') as f:
            f.write(msg + '\n')

    def _log_detail(idx: int, total: int, cluster: dict, tag: str,
                    n_kb: int, formality: str, elapsed: float,
                    prompt: str, body: str, final: str) -> None:
        with DETAIL_LOG.open('a', encoding='utf-8') as f:
            f.write('=' * 80 + '\n')
            f.write(f'[{idx:>2}/{total}] C{cluster["clusterLabel"]:>2}  '
                    f'tag={tag}  kb={n_kb}  formality={formality}  '
                    f'elapsed={elapsed:.2f}s\n')
            f.write(f'standardQuestion: {cluster.get("standardQuestion","")}\n')
            f.write('=' * 80 + '\n\n')
            f.write('[INPUT PROMPT]\n')
            f.write(prompt + '\n\n')
            f.write('[LLM OUTPUT (body)]\n')
            f.write(body + '\n\n')
            f.write('[FINAL ANSWER]\n')
            f.write(final + '\n\n')

    tokenizer = model = None
    if not DRY_RUN:
        tokenizer, model = load_model()

    out_records = []
    for i, c in enumerate(candidates, 1):
        tag = c.get('tag')
        if tag not in templates:
            print(f'  [{i:>2}/{len(candidates)}] C{c["clusterLabel"]:>2} skip — unknown tag={tag}')
            out_records.append(c)
            continue

        tag_cfg = templates[tag]
        kb_chunks = select_kb_chunks(retrieval.get(c['clusterLabel'])) if tag == 'FAQ' else []
        prompt = build_prompt(c, tag_cfg, shared, kb_chunks)

        if DRY_RUN:
            with PROMPT_DUMP.open('a', encoding='utf-8') as f:
                f.write(f'\n{"="*80}\nC{c["clusterLabel"]} [{tag}] kb={len(kb_chunks)}\n{"="*80}\n{prompt}\n')
            answer = '(DRY_RUN — 답변 미생성)'
            elapsed = 0.0
            retried = False
            missing = []
            n_attempts = 0
        else:
            t0 = time.time()
            ans1 = generate(prompt, tokenizer, model)
            ok1, miss1 = validate_answer(ans1, tag)
            attempts = [(ans1, miss1)]
            retried = False
            if not ok1:
                retry_prompt = build_retry_prompt(prompt, ans1, miss1)
                ans2 = generate(retry_prompt, tokenizer, model)
                ok2, miss2 = validate_answer(ans2, tag)
                attempts.append((ans2, miss2))
                retried = True
            # best-of: 누락 적은 쪽 우선, 동률이면 더 긴 쪽 (보통 더 풍부함)
            answer, missing = min(attempts, key=lambda x: (len(x[1]), -len(x[0])))
            n_attempts = len(attempts)
            elapsed = time.time() - t0
            mark = '!' if missing else ('R' if retried else ' ')
            _log_summary(f'  [{i:>2}/{len(candidates)}] {mark} C{c["clusterLabel"]:>2} [{tag}] '
                         f'kb={len(kb_chunks)} ({elapsed:.1f}s, x{n_attempts}) '
                         f'"{answer[:50].replace(chr(10)," ")}..."')

        c_out = dict(c)
        c_out['answerDraft'] = answer
        _log_detail(i, len(candidates), c, tag, len(kb_chunks),
                    tag_cfg['formality'], elapsed,
                    prompt, answer, answer)
        c_out['answerMeta'] = {
            'regenerator': 'v0.3',
            'mode': 'integrated_generation_best_of_N',
            'kbChunksUsed': len(kb_chunks),
            'topKbScore': kb_chunks[0]['rerank_score'] if kb_chunks else None,
            'formality': tag_cfg['formality'],
            'citationStyle': tag_cfg['citation_style'],
            'attempts': n_attempts,
            'validationRetried': retried,
            'validationMissing': missing,
        }
        out_records.append(c_out)

        with OUTPUT_JSONL.open('a', encoding='utf-8') as fp:
            fp.write(json.dumps(c_out, ensure_ascii=False) + '\n')

    OUTPUT_JSON.write_text(
        json.dumps({'faqCandidates': out_records}, ensure_ascii=False, indent=2),
        encoding='utf-8',
    )
    _log_summary(f'\n산출물: {OUTPUT_JSON.name} ({OUTPUT_JSON.stat().st_size:,} bytes, {len(out_records)}건)')
    _log_summary(f'상세 로그: {DETAIL_LOG.name} ({DETAIL_LOG.stat().st_size:,} bytes)' if DETAIL_LOG.exists() else '상세 로그: (없음)')
    if DRY_RUN:
        _log_summary(f'프롬프트 덤프: {PROMPT_DUMP.name}')


if __name__ == '__main__':
    main()
