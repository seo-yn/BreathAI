"""Authenticated multipart intake. Run worker separately; no GPU imports here."""
from datetime import date
import csv
import hashlib
import hmac
import json
import os
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile

from services.job_store import Conflict, JobStore
from services.dashboard_metrics import read_csv, validate_source_rows


def create_app(*, root=None, token=None, max_upload_bytes=50 * 1024 * 1024):
    token = token or os.environ.get("TTOBAGI_AI_API_TOKEN")
    if not token:
        raise RuntimeError("TTOBAGI_AI_API_TOKEN must be configured")
    store = JobStore(root or os.environ.get("TTOBAGI_JOB_ROOT", "/data/jobs"))
    uploads = store.root / "uploads"
    uploads.mkdir(exist_ok=True)
    app = FastAPI(title="BreathAI", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store = store

    def authorize(authorization: str | None = Header(default=None)):
        if not hmac.compare_digest((authorization or "").encode(), f"Bearer {token}".encode()):
            raise HTTPException(401, "Invalid service credential")

    @app.get("/health")
    def health():
        return {"status": "ok"}  # Liveness only, not GPU/model readiness.

    @app.get("/jobs/{analysis_id}", dependencies=[Depends(authorize)])
    def job_status(analysis_id: int):
        row = store.get(analysis_id)
        if not row:
            raise HTTPException(404, "Unknown analysisId")
        return {"analysisId": analysis_id, "status": row["state"], "callback": row["delivery"]}

    @app.post("/pipeline", status_code=202, dependencies=[Depends(authorize)])
    def pipeline(file: UploadFile = File(...), uploadId: str = Form(...), analysisId: int = Form(...),
                 isMaskingEnabled: bool = Form(True), isTranslationEnabled: bool = Form(False),
                 periodStartDate: str = Form(""), periodEndDate: str = Form("")):
        if analysisId <= 0 or not uploadId.strip() or len(uploadId) > 200:
            raise HTTPException(422, "Invalid identifiers")
        try:
            start = date.fromisoformat(periodStartDate) if periodStartDate else None
            end = date.fromisoformat(periodEndDate) if periodEndDate else None
            if start and end and start > end:
                raise ValueError()
        except ValueError:
            raise HTTPException(422, "Invalid analysis period") from None
        metadata = {"uploadId": uploadId, "analysisId": analysisId,
                    "masking": isMaskingEnabled, "translation": isTranslationEnabled,
                    "periodStartDate": periodStartDate, "periodEndDate": periodEndDate}
        path = uploads / f"{uuid4().hex}.csv"
        digest = hashlib.sha256(json.dumps(metadata, sort_keys=True).encode())
        keep = False
        try:
            size = 0
            with path.open("xb") as output:
                while chunk := file.file.read(1024 * 1024):
                    size += len(chunk)
                    if size > max_upload_bytes:
                        raise HTTPException(413, "File exceeds upload limit")
                    digest.update(chunk)
                    output.write(chunk)
                output.flush()
                os.fsync(output.fileno())
            if not size:
                raise HTTPException(422, "Empty input file")
            try:
                with path.open(encoding="utf-8-sig", newline="") as incoming:
                    columns = next(csv.reader(incoming))
                    columns = {column.replace("\n", "") for column in columns}
                    if not {"번호", "질문", "날짜"} <= columns:
                        raise ValueError()
                validate_source_rows(read_csv(path))
            except (UnicodeDecodeError, StopIteration, ValueError, csv.Error, KeyError, TypeError, AttributeError):
                raise HTTPException(422, "Expected UTF-8 CSV with unique INT 번호, 질문, ISO 날짜 and optional valid logType") from None
            created = store.submit(analysisId, digest.hexdigest(), metadata, path)
            keep = created
            return {"analysisId": analysisId, "status": store.get(analysisId)["state"], "duplicate": not created}
        except Conflict as exc:
            raise HTTPException(409, str(exc)) from None
        finally:
            file.file.close()
            if not keep:
                path.unlink(missing_ok=True)

    return app
