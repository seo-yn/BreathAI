"""Copy an explicitly selected Bronze FAQ batch into gold_faq without overwriting edits.

Default is a read-only preflight. --apply creates missing source_seq_num rows only.
No edit history is created: untouched copies must remain excluded from the AI DB KB.
"""
import argparse
from pathlib import Path
from services.bronze_faq_ingest import connect_db


def seed(conn, batch_id, category_batch_id, apply=False):
    locked = False
    try:
        with conn.cursor() as cursor:
            if apply:
                cursor.execute("SELECT ENGINE FROM information_schema.TABLES "
                               "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'gold_faq'")
                engine = cursor.fetchone()
                if not engine or engine["ENGINE"].upper() != "INNODB":
                    raise ValueError("gold_faq must be an existing InnoDB table")
                cursor.execute("SELECT GET_LOCK('ttobagi_seed_gold_faq', 10) AS acquired")
                locked = cursor.fetchone()["acquired"] == 1
                if not locked:
                    raise RuntimeError("Another FAQ seed is running")
            conn.begin()
            for selected, source in ((batch_id, "counselling_info"), (category_batch_id, "cate_info")):
                cursor.execute("SELECT source_table, batch_status FROM bronze_ingest_batch "
                               "WHERE ingest_batch_id = %s", (selected,))
                batch = cursor.fetchone()
                if not batch or batch["source_table"] != source or batch["batch_status"] != "SUCCESS":
                    raise ValueError(f"A successful {source} batch is required")
            cursor.execute("SELECT seq_num, q_type, ci_question, ci_answer0, qa_cnt "
                           "FROM bronze_counselling_info WHERE _ingest_batch_id = %s "
                           "ORDER BY seq_num", (batch_id,))
            rows = cursor.fetchall()
            if not rows:
                raise ValueError("Selected counselling batch is empty or missing")
            cursor.execute("SELECT q_type, q_disp_name FROM bronze_cate_info "
                           "WHERE _ingest_batch_id = %s", (category_batch_id,))
            categories = {}
            for row in cursor.fetchall():
                code, label = row["q_type"], row["q_disp_name"]
                if code in categories and categories[code] != label:
                    raise ValueError("Ambiguous category code in selected batch")
                if len(label) > 100:
                    raise ValueError("Category exceeds gold_faq.category length")
                categories[code] = label
            if not categories:
                raise ValueError("Selected category batch is empty")
            cursor.execute("SELECT source_seq_num FROM gold_faq WHERE source_seq_num IS NOT NULL")
            existing_rows = [row["source_seq_num"] for row in cursor.fetchall()]
            if len(existing_rows) != len(set(existing_rows)):
                raise ValueError("Duplicate source_seq_num in gold_faq; reconcile before seeding")
            existing = set(existing_rows)
            records = []
            for row in rows:
                if row["seq_num"] in existing:
                    continue
                if not str(row["ci_question"] or "").strip() or not str(row["ci_answer0"] or "").strip():
                    raise ValueError(f"FAQ {row['seq_num']} has an empty question/answer")
                if row["q_type"] is not None and row["q_type"] not in categories:
                    raise ValueError(f"FAQ {row['seq_num']} has an unmapped category")
                records.append((row["seq_num"], row["ci_question"], row["ci_answer0"],
                                row["q_type"], categories.get(row["q_type"]), row["qa_cnt"]))
            if apply and records:
                cursor.executemany("""
                    INSERT INTO gold_faq
                        (source_seq_num, question, answer, keywords, q_type, category,
                         qa_cnt, is_active, created_at, updated_at)
                    VALUES (%s, %s, %s, '[]', %s, %s, %s, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, records)
                cursor.execute("SELECT COUNT(*) AS n FROM gold_faq f "
                               "JOIN bronze_counselling_info b ON b.seq_num = f.source_seq_num "
                               "WHERE b._ingest_batch_id = %s", (batch_id,))
                if cursor.fetchone()["n"] != len(rows):
                    raise ValueError("Seed verification count mismatch")
            if apply:
                conn.commit()
            else:
                conn.rollback()
            return {"source": len(rows), "existing": len(rows) - len(records),
                    "inserted" if apply else "would_insert": len(records)}
    except Exception:
        conn.rollback()
        raise
    finally:
        if locked:
            with conn.cursor() as cursor:
                cursor.execute("SELECT RELEASE_LOCK('ttobagi_seed_gold_faq')")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", type=Path)
    parser.add_argument("--batch-id", required=True)
    parser.add_argument("--category-batch-id", required=True)
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    conn = connect_db(args.env_file)
    try:
        print(seed(conn, args.batch_id, args.category_batch_id, args.apply))
    finally:
        conn.close()


if __name__ == "__main__":
    main()
