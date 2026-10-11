"""Validate CP949 source CSVs and atomically load one reproducible Bronze batch.

Run with --help. Batch metadata must match the existing bronze_ingest_batch schema.
No schema is inferred and no existing source rows are overwritten.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import os
import re
import uuid
from datetime import datetime
from pathlib import Path
from urllib.parse import urlparse

SCHEMA = Path(__file__).resolve().parents[1] / "db/schema/bronze_faq_sources.sql"


def source_columns(table):
    ddl = SCHEMA.read_text(encoding="utf-8")
    body = ddl.split(f"CREATE TABLE IF NOT EXISTS {table} (", 1)[1].split("PRIMARY KEY", 1)[0]
    return [(m[1], m[2], m[3]) for line in body.splitlines()
            if (m := re.match(r"\s+(\w+) (INT|BIGINT|TINYINT|VARCHAR\(\d+\)|TEXT|LONGTEXT) (.*)", line))
            and not m[1].startswith("_")]


def read_source(path, table):
    columns = source_columns(table)
    rows, seen = [], set()
    if Path(path).suffix.lower() != ".csv":
        raise ValueError(f"{path}: a CP949 source CSV is required")
    csv.field_size_limit(16 * 1024 * 1024)
    with Path(path).open(encoding="cp949", newline="") as stream:
        reader = csv.DictReader(stream)
        headers = reader.fieldnames
        raw_rows = list(reader)
    expected = {name for name, _, _ in columns}
    if (headers is None or set(headers) != expected
            or len(headers) != len(expected)):
        raise ValueError(f"{path}: CSV headers must exactly match {sorted(expected)}")
    for line, raw in enumerate(raw_rows, 2):
        if None in raw or any(value is None for value in raw.values()):
            raise ValueError(f"{path}:{line}: malformed CSV row")
        row = {}
        for name, kind, rules in columns:
            value = raw[name]
            if value == "":
                value = 0 if name == "qa_cnt" else None
            if value is None and "NOT NULL" in rules:
                raise ValueError(f"{path}:{line}: {name} is required")
            if value is not None and kind in ("INT", "BIGINT", "TINYINT"):
                try:
                    value = int(value)
                except ValueError:
                    raise ValueError(f"{path}:{line}: {name} must be an integer") from None
                bits = {"INT": 32, "BIGINT": 64, "TINYINT": 8}[kind]
                if not -(2 ** (bits - 1)) <= value < 2 ** (bits - 1):
                    raise ValueError(f"{path}:{line}: {name} is out of range")
            if value is not None and kind.startswith("VARCHAR"):
                if len(value) > int(re.search(r"\d+", kind)[0]):
                    raise ValueError(f"{path}:{line}: {name} is too long")
            row[name] = value
        if row["seq_num"] in seen:
            raise ValueError(f"{path}:{line}: duplicate seq_num {row['seq_num']}")
        seen.add(row["seq_num"])
        row["_row_hash"] = hashlib.sha256(json.dumps(
            row, sort_keys=True, ensure_ascii=False, separators=(",", ":")
        ).encode("utf-8")).hexdigest()
        rows.append(row)
    if not rows:
        raise ValueError(f"{path}: empty source")
    return sorted(rows, key=lambda row: row["seq_num"])


def batch_id_for(sources):
    fingerprint = json.dumps({table: [r["_row_hash"] for r in rows]
                              for table, rows in sorted(sources.items())}, sort_keys=True)
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "ttobagi:bronze-faq:" + fingerprint))


def identifier(name):
    if not re.fullmatch(r"[A-Za-z_][A-Za-z_0-9]*", name):
        raise ValueError(f"Invalid SQL identifier: {name}")
    return f"`{name}`"


def ingest(conn, sources, files, metadata):
    if len(sources) != 1:
        raise ValueError("bronze_ingest_batch records one source table per batch")
    batch_id = batch_id_for(sources)
    with conn.cursor() as cursor:
        # Fail before any writes if a nontransactional table would break rollback.
        for table in ["bronze_ingest_batch", *sources]:
            cursor.execute("SELECT ENGINE FROM information_schema.TABLES "
                           "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s", (table,))
            engine = cursor.fetchone()
            if not engine or engine["ENGINE"].upper() != "INNODB":
                raise ValueError(f"{table}: an existing InnoDB table is required")
        cursor.execute("SHOW COLUMNS FROM bronze_ingest_batch")
        schema = {row["Field"]: row for row in cursor.fetchall()}
        batch = dict(metadata)
        if "batch_status" in schema:
            batch["batch_status"] = "RUNNING"
            batch["row_count"] = sum(len(rows) for rows in sources.values())
        if "ingest_batch_id" in batch:
            raise ValueError("Do not specify ingest_batch_id; it is derived from source content")
        batch["ingest_batch_id"] = batch_id
        if set(batch) - set(schema):
            raise ValueError("Unknown batch metadata columns")
        missing = [name for name, col in schema.items() if name not in batch
                   and col["Null"] == "NO" and col["Default"] is None
                   and "auto_increment" not in col["Extra"]]
        if missing:
            raise ValueError(f"Batch metadata required: {missing}")
        try:
            conn.begin()
            cursor.execute("SELECT * FROM bronze_ingest_batch "
                           "WHERE ingest_batch_id = %s FOR UPDATE", (batch_id,))
            exists = cursor.fetchone()
            if exists:
                if "batch_status" in schema and (
                    exists.get("batch_status") != "SUCCESS"
                    or exists.get("row_count") != batch["row_count"]
                    or exists.get("source_table") != batch.get("source_table")
                ):
                    raise ValueError("Existing batch metadata is inconsistent or not SUCCESS")
                for table, rows in sources.items():
                    cursor.execute(f"SELECT seq_num, _row_hash FROM {identifier(table)} "
                                   "WHERE _ingest_batch_id = %s", (batch_id,))
                    actual = {r["seq_num"]: r["_row_hash"] for r in cursor.fetchall()}
                    if actual != {r["seq_num"]: r["_row_hash"] for r in rows}:
                        raise ValueError(f"{table}: existing batch differs; refusing partial reload")
                conn.rollback()
                return batch_id, "already loaded"
            names = list(batch)
            cursor.execute("INSERT INTO bronze_ingest_batch (" + ",".join(map(identifier, names))
                           + ") VALUES (" + ",".join(["%s"] * len(names)) + ")",
                           tuple(batch[n] for n in names))
            for table, rows in sources.items():
                records = [dict(row, _ingest_batch_id=batch_id,
                                _source_file=Path(files[table]).name) for row in rows]
                names = list(records[0])
                cursor.executemany(f"INSERT INTO {identifier(table)} ("
                                   + ",".join(map(identifier, names)) + ") VALUES ("
                                   + ",".join(["%s"] * len(names)) + ")",
                                   [tuple(row[n] for n in names) for row in records])
                cursor.execute(f"SELECT COUNT(*) AS n FROM {identifier(table)} "
                               "WHERE _ingest_batch_id = %s", (batch_id,))
                if cursor.fetchone()["n"] != len(rows):
                    raise ValueError(f"{table}: row count mismatch")
            if "batch_status" in schema:
                cursor.execute("UPDATE bronze_ingest_batch SET batch_status = 'SUCCESS' "
                               "WHERE ingest_batch_id = %s", (batch_id,))
            conn.commit()
        except Exception:
            conn.rollback()
            raise
    return batch_id, "loaded"


def connect_db(env_file=None):
    """Use explicit DB_* environment variables or a Spring-style .env file."""
    import pymysql
    settings = dict(os.environ)
    if env_file:
        for line in Path(env_file).read_text(encoding="utf-8-sig").splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                settings[key.strip()] = value.strip().strip("\"'")
    if settings.get("TTOBAGI_DB_URL"):
        url = urlparse(settings["TTOBAGI_DB_URL"].removeprefix("jdbc:"))
        host, port, database = url.hostname, url.port or 3306, url.path.strip("/")
        user, password = settings["TTOBAGI_DB_USERNAME"], settings["TTOBAGI_DB_PASSWORD"]
    else:
        host, port, database = settings["DB_HOST"], int(settings.get("DB_PORT", "3306")), settings["DB_NAME"]
        user, password = settings["DB_USER"], settings["DB_PASSWORD"]
    return pymysql.connect(host=host, port=port, user=user, password=password,
                           database=database, charset="utf8mb4", autocommit=False,
                           init_command="SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'STRICT_ALL_TABLES')",
                           connect_timeout=5, read_timeout=30, write_timeout=30,
                           cursorclass=pymysql.cursors.DictCursor)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cate", type=Path)
    parser.add_argument("--counselling", type=Path)
    parser.add_argument("--batch-metadata", type=Path, help="JSON object matching existing batch columns")
    parser.add_argument("--env-file", type=Path, help="DB_* or TTOBAGI_DB_* .env file")
    parser.add_argument("--extracted-at", help="Source extraction time (ISO datetime); otherwise read filename _YYYYMMDDHHMM")
    parser.add_argument("--apply", action="store_true", help="Write to the configured DB; default only validates")
    args = parser.parse_args()
    files = {"bronze_cate_info": args.cate, "bronze_counselling_info": args.counselling}
    files = {table: path for table, path in files.items() if path is not None}
    if not files:
        parser.error("Provide --cate and/or --counselling")
    sources = {table: read_source(path, table) for table, path in files.items()}
    print(json.dumps({"batch_ids": {table: batch_id_for({table: rows}) for table, rows in sources.items()},
                      "counts": {t: len(r) for t, r in sources.items()}}, ensure_ascii=False))
    if not args.apply:
        return
    metadata = json.loads(args.batch_metadata.read_text(encoding="utf-8")) if args.batch_metadata else {}
    if not isinstance(metadata, dict):
        raise ValueError("Batch metadata must be a JSON object")
    conn = connect_db(args.env_file)
    try:
        batches = []
        for table, rows in sources.items():
            source_table = table.removeprefix("bronze_")
            values = dict(metadata)
            if values.get("source_table", source_table) != source_table:
                raise ValueError("Metadata source_table does not match the input")
            stamp = re.search(r"_(\d{12})\.csv$", files[table].name, re.I)
            extracted = args.extracted_at or values.get("extracted_at")
            if extracted:
                extracted = datetime.fromisoformat(extracted)
            elif stamp:
                extracted = datetime.strptime(stamp[1], "%Y%m%d%H%M")
            else:
                raise ValueError("Provide --extracted-at or batch metadata extracted_at")
            values.update(source_system=values.get("source_system", "seoulmetro"),
                          source_table=source_table, source_object=values.get("source_object", files[table].name),
                          extracted_at=extracted, load_type=values.get("load_type", "FULL"))
            batches.append((table, rows, values))
        for table, rows, values in batches:
            print(ingest(conn, {table: rows}, files, values))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
