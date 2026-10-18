"""Preflight without inference by default. Does not submit jobs or write to DB."""
import argparse
import importlib
import json
import os
from pathlib import Path


def check(*, gpu=False, database=False):
    report = {}
    for name in ("fastapi", "uvicorn", "multipart", "httpx", "services.pipeline_api", "services.pipeline_worker"):
        importlib.import_module(name)
    report["apiImports"] = "ok"
    root = Path(os.environ.get("TTOBAGI_JOB_ROOT", "/data/jobs"))
    report["jobRootExists"] = root.is_dir()
    report["jobRootWritable"] = root.is_dir() and os.access(root, os.W_OK)
    for name in ("TTOBAGI_AI_API_TOKEN", "TTOBAGI_CALLBACK_TOKEN", "TTOBAGI_BE_BASE_URL"):
        report[name + "Configured"] = bool(os.getenv(name))
    if gpu:
        import torch
        from services.faq_kb import DEFAULT_GENERAL_FAQ, DEFAULT_COUNSELLING_FAQ
        for name in ("services.run_pipeline", "services.preprocess", "services.clustering", "services.retriever"):
            importlib.import_module(name)
        report["pipelineImports"] = "ok"
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA unavailable")
        device = torch.cuda.get_device_properties(0)
        tensor = torch.randn(256, 256, device="cuda")
        _ = tensor @ tensor
        torch.cuda.synchronize()
        report.update(gpu=device.name, vramBytes=device.total_memory, torch=torch.__version__, cuda=torch.version.cuda)
        for name, default in (("TTOBAGI_GENERAL_FAQ_PATH", DEFAULT_GENERAL_FAQ),
                              ("TTOBAGI_COUNSELLING_FAQ_PATH", DEFAULT_COUNSELLING_FAQ)):
            report[name + "Exists"] = Path(os.getenv(name) or default).is_file()
    if database:
        from services.bronze_faq_ingest import connect_db
        connection = connect_db(os.getenv("TTOBAGI_DB_ENV_FILE"))
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT 1 AS ok")
                report["database"] = cursor.fetchone()["ok"] == 1
        finally:
            connection.close()
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gpu", action="store_true")
    parser.add_argument("--database", action="store_true", help="Read-only SELECT 1 on configured DB")
    args = parser.parse_args()
    report = check(gpu=args.gpu, database=args.database)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if any(value is False for value in report.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
