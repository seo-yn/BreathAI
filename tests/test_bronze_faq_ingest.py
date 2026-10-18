import csv
import tempfile
import unittest
from pathlib import Path

from services.bronze_faq_ingest import batch_id_for, read_source, ingest


class BronzeSourceTest(unittest.TestCase):
    def test_multiple_source_tables_cannot_share_a_batch(self):
        with self.assertRaisesRegex(ValueError, "one source table"):
            ingest(None, {"bronze_cate_info": [], "bronze_counselling_info": []}, {}, {})

    def test_existing_failed_batch_is_not_reported_as_loaded(self):
        class Connection:
            def cursor(self): return self
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def execute(self, sql, args=None): self.sql = sql
            def fetchone(self):
                return {"ENGINE": "InnoDB"} if "ENGINE" in self.sql else {
                    "batch_status": "FAIL", "row_count": 1, "source_table": "cate_info"}
            def fetchall(self):
                return [{"Field": name, "Null": "YES", "Default": None, "Extra": ""}
                        for name in ["ingest_batch_id", "batch_status", "row_count", "source_table"]]
            def begin(self): pass
            def rollback(self): pass
        with self.assertRaisesRegex(ValueError, "not SUCCESS"):
            ingest(Connection(), {"bronze_cate_info": [{"seq_num": 1, "_row_hash": "x"}]},
                   {"bronze_cate_info": "cate.csv"}, {"source_table": "cate_info"})

    def write_cate(self, path, rows):
        with path.open("w", encoding="cp949", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["seq_num", "q_type", "q_disp_name", "state", "regdate"])
            writer.writerows(rows)

    def test_cp949_hash_and_batch_are_stable_across_row_order(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cate.csv"
            rows = [[1, 204, "일반상담", 1, 123], [2, 205, "유실물", 0, 456]]
            self.write_cate(path, rows)
            first = read_source(path, "bronze_cate_info")
            self.write_cate(path, list(reversed(rows)))
            second = read_source(path, "bronze_cate_info")
            self.assertEqual(first, second)
            self.assertEqual("일반상담", first[0]["q_disp_name"])
            self.assertEqual(batch_id_for({"bronze_cate_info": first}),
                             batch_id_for({"bronze_cate_info": second}))
            rows[0][2] = "수정"
            self.write_cate(path, rows)
            self.assertNotEqual(first[0]["_row_hash"], read_source(path, "bronze_cate_info")[0]["_row_hash"])

    def test_invalid_or_duplicate_source_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cate.csv"
            for rows in ([[1, 1, "표시명", 1, 1]] * 2,
                         [[1, 1, "", 1, 1]], [[1, 1, "표시명", 128, 1]],
                         [["broken", 1, "표시명", 1, 1]]):
                with self.subTest(rows=rows):
                    self.write_cate(path, rows)
                    with self.assertRaises(ValueError):
                        read_source(path, "bronze_cate_info")

    def test_insert_failure_rolls_back_batch_and_rows(self):
        class Cursor:
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def execute(self, sql, args=None):
                self.sql = sql
                if sql.startswith("INSERT INTO bronze_ingest_batch"):
                    conn.batch_inserted = True
            def fetchone(self):
                return {"ENGINE": "InnoDB"} if "ENGINE" in self.sql else None
            def fetchall(self):
                return [{"Field": "ingest_batch_id", "Null": "NO", "Default": None, "Extra": ""}]
            def executemany(self, sql, rows):
                self.assert_batch_first = conn.batch_inserted
                if not self.assert_batch_first: raise AssertionError("Missing batch")
                raise RuntimeError("insert failed")
        class Connection:
            batch_inserted = False
            rolled_back = False
            committed = False
            def cursor(self): return Cursor()
            def begin(self): pass
            def rollback(self): self.rolled_back = True
            def commit(self): self.committed = True
        conn = Connection()
        with self.assertRaisesRegex(RuntimeError, "insert failed"):
            ingest(conn, {"bronze_cate_info": [{"seq_num": 1, "_row_hash": "hash"}]},
                   {"bronze_cate_info": "cate.csv"}, {})
        self.assertTrue(conn.batch_inserted)
        self.assertTrue(conn.rolled_back)
        self.assertFalse(conn.committed)

    def test_identical_existing_batch_is_noop_and_partial_batch_is_refused(self):
        class Connection:
            def __init__(self, stored): self.stored = stored
            def cursor(self): return self
            def __enter__(self): return self
            def __exit__(self, *args): return False
            def begin(self): pass
            def rollback(self): pass
            def execute(self, sql, args=None):
                if sql.startswith("INSERT"): raise AssertionError("Must not write existing batch")
                self.sql = sql
            def fetchone(self):
                if "ENGINE" in self.sql: return {"ENGINE": "InnoDB"}
                return {"ingest_batch_id": "existing"}
            def fetchall(self):
                if self.sql.startswith("SHOW"):
                    return [{"Field": "ingest_batch_id", "Null": "NO", "Default": None, "Extra": ""}]
                return self.stored
        row = {"seq_num": 1, "_row_hash": "hash"}
        sources, files = {"bronze_cate_info": [row]}, {"bronze_cate_info": "cate.csv"}
        self.assertEqual("already loaded", ingest(Connection([row]), sources, files, {})[1])
        with self.assertRaisesRegex(ValueError, "existing batch differs"):
            ingest(Connection([]), sources, files, {})
