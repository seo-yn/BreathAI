"""SQLite-backed queue shared by HTTP intake and one GPU worker."""
import json
from contextlib import contextmanager
from pathlib import Path
import sqlite3
import time


class Conflict(ValueError):
    pass


class JobStore:
    def __init__(self, root):
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.path = self.root / "jobs.sqlite3"
        with self.connect() as db:
            db.execute("""CREATE TABLE IF NOT EXISTS jobs (
                id INTEGER PRIMARY KEY, fingerprint TEXT NOT NULL,
                metadata TEXT NOT NULL, input_path TEXT NOT NULL,
                state TEXT NOT NULL DEFAULT 'QUEUED',
                payload TEXT, delivery TEXT NOT NULL DEFAULT 'NONE',
                updated REAL NOT NULL)""")
            db.execute("""CREATE TABLE IF NOT EXISTS progress_callbacks (
                job_id INTEGER NOT NULL, status TEXT NOT NULL, delivery TEXT NOT NULL,
                updated REAL NOT NULL, PRIMARY KEY(job_id,status))""")

    def claim_progress(self, analysis_id, status):
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT state FROM jobs WHERE id=?", (analysis_id,)).fetchone()
            if not row or row[0] != 'RUNNING':
                return False
            if status == 'PREPROCESSING' and db.execute(
                "SELECT 1 FROM progress_callbacks WHERE job_id=? AND status='ANALYZING'", (analysis_id,)
            ).fetchone():
                return False
            return db.execute("INSERT OR IGNORE INTO progress_callbacks VALUES(?,?,'SENDING',?)",
                              (analysis_id, status, time.time())).rowcount == 1

    def finish_progress(self, analysis_id, status, delivery):
        with self.connect() as db:
            db.execute("UPDATE progress_callbacks SET delivery=?,updated=? WHERE job_id=? AND status=?",
                       (delivery, time.time(), analysis_id, status))

    @contextmanager
    def connect(self):
        connection = sqlite3.connect(self.path, timeout=30)
        try:
            with connection:
                yield connection
        finally:
            connection.close()

    def submit(self, analysis_id, fingerprint, metadata, input_path):
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            row = db.execute("SELECT fingerprint FROM jobs WHERE id=?", (analysis_id,)).fetchone()
            if row:
                if row[0] != fingerprint:
                    raise Conflict("analysisId already has different input or options")
                return False
            db.execute("INSERT INTO jobs(id,fingerprint,metadata,input_path,updated) VALUES(?,?,?,?,?)",
                       (analysis_id, fingerprint, json.dumps(metadata), str(input_path), time.time()))
            return True

    def get(self, analysis_id):
        with self.connect() as db:
            db.row_factory = sqlite3.Row
            row = db.execute("SELECT * FROM jobs WHERE id=?", (analysis_id,)).fetchone()
            return dict(row) if row else None

    def claim(self):
        with self.connect() as db:
            db.execute("BEGIN IMMEDIATE")
            if db.execute("SELECT 1 FROM jobs WHERE state='RUNNING'").fetchone():
                raise RuntimeError("Interrupted/running job requires operator reconciliation")
            row = db.execute("SELECT id FROM jobs WHERE state='QUEUED' ORDER BY updated,id LIMIT 1").fetchone()
            if row:
                db.execute("UPDATE jobs SET state='RUNNING',updated=? WHERE id=?", (time.time(), row[0]))
        return self.get(row[0]) if row else None

    def finish(self, analysis_id, payload):
        with self.connect() as db:
            db.execute("UPDATE jobs SET state=?,payload=?,delivery='PENDING',updated=? WHERE id=?",
                       (payload["status"], json.dumps(payload, ensure_ascii=False, allow_nan=False), time.time(), analysis_id))

    def pending_callback(self):
        with self.connect() as db:
            row = db.execute("SELECT id FROM jobs WHERE delivery='PENDING' ORDER BY updated,id LIMIT 1").fetchone()
        return self.get(row[0]) if row else None

    def set_delivery(self, analysis_id, state):
        with self.connect() as db:
            db.execute("UPDATE jobs SET delivery=?,updated=? WHERE id=?", (state, time.time(), analysis_id))

    def recover_callbacks(self):
        # A crash after sending might mean BE committed. Do not blindly resend.
        with self.connect() as db:
            db.execute("UPDATE jobs SET delivery='UNKNOWN' WHERE delivery='SENDING'")
            db.execute("UPDATE progress_callbacks SET delivery='UNKNOWN' WHERE delivery='SENDING'")
