"""Create Bronze FAQ tables and rename the existing unanswered batch FK.

Default: inspect and print SQL only. --apply executes DDL (implicit commits).
The existing parent table and constraint rules are never replaced or guessed.
"""
import argparse
from pathlib import Path

from services.bronze_faq_ingest import SCHEMA, connect_db


def quote_identifier(value):
    return "`" + value.replace("`", "``") + "`"


def fk_rename_sql(rows):
    target = "fk_unanswered_learning_batch"
    groups = {}
    for row in rows:
        groups.setdefault(row["CONSTRAINT_NAME"], []).append(row)
    matches = [parts for parts in groups.values()
               if any(r["COLUMN_NAME"] == "_ingest_batch_id" for r in parts)]
    if len(matches) != 1 or len(matches[0]) != 1:
        raise ValueError("Expected exactly one single-column batch foreign key")
    row = matches[0][0]
    if (row["REFERENCED_TABLE_NAME"], row["REFERENCED_COLUMN_NAME"]) != (
            "bronze_ingest_batch", "ingest_batch_id"):
        raise ValueError("Unexpected referenced batch table/column")
    if row["CONSTRAINT_NAME"] == target:
        return None
    if target in groups:
        raise ValueError("Desired constraint name is already used for a different key")
    allowed = {"RESTRICT", "CASCADE", "SET NULL", "NO ACTION", "SET DEFAULT"}
    if row["UPDATE_RULE"] not in allowed or row["DELETE_RULE"] not in allowed:
        raise ValueError("Unknown referential action")
    return (
        "ALTER TABLE bronze_unanswered_learning DROP FOREIGN KEY "
        + quote_identifier(row["CONSTRAINT_NAME"])
        + ", ADD CONSTRAINT " + quote_identifier(target)
        + " FOREIGN KEY (_ingest_batch_id) REFERENCES "
        + quote_identifier(row["REFERENCED_TABLE_SCHEMA"]) + ".bronze_ingest_batch(ingest_batch_id)"
        + " ON UPDATE " + row["UPDATE_RULE"] + " ON DELETE " + row["DELETE_RULE"]
    )


def plan(cursor, rename):
    paths = [SCHEMA.with_name("bronze_ingest_batch.sql"), SCHEMA,
             SCHEMA.with_name("bronze_unanswered_learning.sql")]
    statements = [sql.strip() for path in paths
                  for sql in path.read_text(encoding="utf-8").split(";") if sql.strip()]
    if rename:
        cursor.execute("""
            SELECT k.CONSTRAINT_NAME, k.COLUMN_NAME, k.REFERENCED_TABLE_SCHEMA,
                   k.REFERENCED_TABLE_NAME, k.REFERENCED_COLUMN_NAME,
                   r.UPDATE_RULE, r.DELETE_RULE
            FROM information_schema.KEY_COLUMN_USAGE k
            JOIN information_schema.REFERENTIAL_CONSTRAINTS r
              ON r.CONSTRAINT_SCHEMA = k.CONSTRAINT_SCHEMA
             AND r.TABLE_NAME = k.TABLE_NAME AND r.CONSTRAINT_NAME = k.CONSTRAINT_NAME
            WHERE k.TABLE_SCHEMA = DATABASE() AND k.TABLE_NAME = 'bronze_unanswered_learning'
            ORDER BY k.CONSTRAINT_NAME, k.ORDINAL_POSITION
        """)
        rows = cursor.fetchall()
        if not rows:
            cursor.execute("SELECT COUNT(*) AS n FROM information_schema.TABLES "
                           "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bronze_unanswered_learning'")
            if cursor.fetchone()["n"]:
                raise ValueError("Existing unanswered table has no expected batch foreign key")
        rename_sql = fk_rename_sql(rows) if rows else None
        if rename_sql:
            statements.append(rename_sql)
    return statements


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--rename-unanswered-fk", action="store_true")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    conn = connect_db(args.env_file)
    try:
        with conn.cursor() as cursor:
            statements = plan(cursor, args.rename_unanswered_fk)
            for sql in statements:
                print(sql + ";")
            if args.apply:
                for sql in statements:
                    cursor.execute(sql)
                print("DDL applied")
    finally:
        conn.close()


if __name__ == "__main__":
    main()
