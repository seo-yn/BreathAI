import unittest
from services.seed_gold_faq import seed


class SeedConnection:
    def __init__(self, existing=(), fail_insert=False):
        self.existing = existing
        self.fail_insert = fail_insert
        self.inserted = []
        self.committed = False
        self.rolled_back = False
        self.released = False
    def cursor(self): return self
    def __enter__(self): return self
    def __exit__(self, *args): return False
    def begin(self): pass
    def commit(self): self.committed = True
    def rollback(self): self.rolled_back = True
    def execute(self, sql, args=None):
        self.sql = sql
        self.args = args
        if "RELEASE_LOCK" in sql: self.released = True
    def fetchone(self):
        if "FROM bronze_ingest_batch" in self.sql:
            return {"source_table": "cate_info" if self.args[0] == "categories" else "counselling_info",
                    "batch_status": "SUCCESS"}
        if "ENGINE" in self.sql: return {"ENGINE": "InnoDB"}
        if "GET_LOCK" in self.sql: return {"acquired": 1}
        return {"n": 1}
    def fetchall(self):
        if "FROM bronze_counselling_info" in self.sql:
            return [{"seq_num": 100, "q_type": 1, "ci_question": "question",
                     "ci_answer0": "answer", "qa_cnt": 5}]
        if "FROM bronze_cate_info" in self.sql:
            return [{"q_type": 1, "q_disp_name": "category"}]
        return [{"source_seq_num": n} for n in self.existing]
    def executemany(self, sql, records):
        if self.fail_insert: raise RuntimeError("insert failed")
        self.inserted.extend(records)


class SeedTest(unittest.TestCase):
    def test_unmapped_category_refused_before_insert(self):
        class Unmapped(SeedConnection):
            def fetchall(self):
                rows = super().fetchall()
                if "FROM bronze_counselling_info" in self.sql:
                    rows[0]["q_type"] = 999
                return rows
        conn = Unmapped()
        with self.assertRaisesRegex(ValueError, "unmapped category"):
            seed(conn, "batch", "categories", True)
        self.assertFalse(conn.inserted)
        self.assertTrue(conn.rolled_back)

    def test_non_successful_batch_refused(self):
        class Failed(SeedConnection):
            def fetchone(self):
                result = super().fetchone()
                if "FROM bronze_ingest_batch" in self.sql:
                    result["batch_status"] = "FAIL"
                return result
        conn = Failed()
        with self.assertRaisesRegex(ValueError, "successful"):
            seed(conn, "batch", "categories", True)
        self.assertFalse(conn.inserted)

    def test_preflight_does_not_write(self):
        conn = SeedConnection()
        self.assertEqual({"source": 1, "existing": 0, "would_insert": 1}, seed(conn, "batch", "categories"))
        self.assertFalse(conn.inserted)
        self.assertFalse(conn.committed)

    def test_existing_source_is_not_overwritten(self):
        conn = SeedConnection(existing=[100])
        self.assertEqual(0, seed(conn, "batch", "categories", True)["inserted"])
        self.assertFalse(conn.inserted)
        self.assertTrue(conn.released)

    def test_new_source_is_inserted_and_verified(self):
        conn = SeedConnection()
        self.assertEqual(1, seed(conn, "batch", "categories", True)["inserted"])
        self.assertEqual(100, conn.inserted[0][0])
        self.assertTrue(conn.committed)
        self.assertTrue(conn.released)

    def test_failure_rolls_back_and_releases_lock(self):
        conn = SeedConnection(fail_insert=True)
        with self.assertRaises(RuntimeError): seed(conn, "batch", "categories", True)
        self.assertTrue(conn.rolled_back)
        self.assertFalse(conn.committed)
        self.assertTrue(conn.released)

    def test_duplicate_existing_source_refused(self):
        conn = SeedConnection(existing=[100, 100])
        with self.assertRaises(ValueError): seed(conn, "batch", "categories", True)
        self.assertFalse(conn.inserted)
