"""Translate real pipeline artifacts to the existing BE callback DTO."""
import csv
import json
from pathlib import Path


def build_callback(job_dir):
    data = Path(job_dir) / "dataset"
    source = json.loads((data / "faq_candidates_final.json").read_text(encoding="utf-8"))
    if not isinstance(source, dict) or not isinstance(source.get("faqCandidates"), list):
        raise ValueError("Invalid final result")
    keywords = json.loads((data / "cluster_keywords.json").read_text(encoding="utf-8"))
    with (data / "cluster_summary.csv").open(encoding="utf-8-sig", newline="") as file:
        summaries = {int(row["clusterId"]): row for row in csv.DictReader(file)}
    clusters = []
    for label, row in summaries.items():
        if label == -1:
            continue
        words = keywords.get(str(label), [])
        clusters.append({"clusterLabel": label, "name": ", ".join(words[:3]) or f"클러스터 {label}",
                         "topKeywords": words, "size": int(row["size"])})
    candidates = []
    for candidate in source["faqCandidates"]:
        label = candidate["clusterLabel"]
        if label not in summaries or label == -1:
            raise ValueError("Candidate refers to unknown cluster")
        kind = candidate.get("candidateType", "NEW")
        if kind not in {"NEW", "EXPAND"}:
            raise ValueError("Invalid candidateType")
        synonyms = candidate.get("synonyms") or []
        for synonym in synonyms:
            if not isinstance(synonym.get("text"), str) or synonym.get("type") not in {"TYPO", "ABBR", "SYNONYM"}:
                raise ValueError("Invalid synonym")
        mapped = {key: candidate.get(key) for key in (
            "standardQuestion", "answerDraft", "qType", "category")}
        mapped.update({
            "clusterLabel": label, "candidateType": kind,
            "occurrenceCount": int(summaries[label]["size"]),
            "representativeKeywords": candidate.get("topKeywords") or keywords.get(str(label), []),
            "similarQuestions": candidate.get("similarQuestions") or [],
            "synonyms": synonyms, "matchedFaqs": candidate.get("matchedFaqs") or [],
        })
        if not all(isinstance(mapped[key], str) for key in ("standardQuestion", "answerDraft")):
            raise ValueError("Question and answer must be strings")
        candidates.append(mapped)
    # CSV logId is not confirmed to be a Bronze primary key. Never invent it.
    return {"status": "COMPLETED", "faqCandidates": candidates,
            "clusteringView": {"clusterNames": clusters, "points": []}}
