import unittest
from services.bronze_faq_migrate import fk_rename_sql


class MigrationTest(unittest.TestCase):
    def row(self, **overrides):
        return dict(CONSTRAINT_NAME="1", COLUMN_NAME="_ingest_batch_id",
                    REFERENCED_TABLE_SCHEMA="ttobagi_db", REFERENCED_TABLE_NAME="bronze_ingest_batch",
                    REFERENCED_COLUMN_NAME="ingest_batch_id", UPDATE_RULE="CASCADE",
                    DELETE_RULE="RESTRICT", **overrides)

    def test_numeric_constraint_name_and_rules_are_preserved(self):
        sql = fk_rename_sql([self.row()])
        self.assertIn("DROP FOREIGN KEY `1`", sql)
        self.assertIn("ADD CONSTRAINT `fk_unanswered_learning_batch`", sql)
        self.assertIn("ON UPDATE CASCADE ON DELETE RESTRICT", sql)

    def test_already_named_is_noop(self):
        row = self.row()
        row["CONSTRAINT_NAME"] = "fk_unanswered_learning_batch"
        self.assertIsNone(fk_rename_sql([row]))

    def test_unknown_or_composite_constraint_fails(self):
        with self.assertRaises(ValueError):
            fk_rename_sql([])
        with self.assertRaises(ValueError):
            fk_rename_sql([self.row(), self.row()])
