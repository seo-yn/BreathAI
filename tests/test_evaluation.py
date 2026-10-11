import copy
import math
import unittest

from evaluation.aggregate_results import evaluate, retrieval_scores


class EvaluationTest(unittest.TestCase):
    def setUp(self):
        self.queries = [{'id': 'q1', 'ground_truth_faq_id': 'a'},
                        {'id': 'q2', 'ground_truth_faq_id': None}]
        self.kb = [{'id': 'a', 'text': 'A'}, {'id': 'b', 'text': 'B'}]
        self.outputs = [dict(test_id=q['id'], system='rag', status='ok',
                             standard_question='Question', answer='Answer',
                             retrieved=[{'id': 'b', 'cross_encoder': .2},
                                        {'id': 'a', 'cross_encoder': .8}],
                             judge=dict(accuracy=4, politeness=5, actionability=3,
                                        essential=4, reason='Evidence')) for q in self.queries]

    def test_rank_cutoff_and_misses(self):
        self.assertEqual(retrieval_scores(['a', 'b'], 'b'), (1, .5))
        self.assertEqual(retrieval_scores(['a', 'b', 'c', 'd', 'e', 'f'], 'f'), (0, 0))
        self.assertEqual(retrieval_scores([], 'a'), (0, 0))
        self.assertEqual(retrieval_scores(['a'], None), (None, None))

    def test_denominator_and_per_query_ce(self):
        raw, summary = evaluate(self.queries, self.kb, self.outputs, ['rag'])
        self.assertEqual(raw[0]['cross_encoder'], .5)
        self.assertEqual(summary[0]['n'], 1)
        self.assertEqual(summary[1]['mean'], .5)
        self.assertIsNone(summary[1]['std'])
        self.assertEqual(summary[2]['std'], 0)

    def test_failures_are_counted_not_scored(self):
        self.outputs[1] = dict(test_id='q2', system='rag', status='failed', error='timeout')
        _, summary = evaluate(self.queries, self.kb, self.outputs, ['rag'])
        self.assertEqual(summary[2]['failure_rate'], .5)
        self.assertEqual(summary[2]['n'], 1)

    def test_invalid_data_rejected(self):
        mutations = [lambda rows: rows.pop(),
                     lambda rows: rows.append(copy.deepcopy(rows[0])),
                     lambda rows: rows[0]['retrieved'].append({'id': 'a', 'cross_encoder': .5}),
                     lambda rows: rows[0]['retrieved'][0].update(id='unknown'),
                     lambda rows: rows[0]['retrieved'][0].update(cross_encoder=math.nan),
                     lambda rows: rows[0]['judge'].update(accuracy=True),
                     lambda rows: rows[0]['judge'].update(accuracy=3.5),
                     lambda rows: rows[0]['judge'].update(accuracy=6)]
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                rows = copy.deepcopy(self.outputs)
                mutate(rows)
                with self.assertRaises(ValueError):
                    evaluate(self.queries, self.kb, rows, ['rag'])


if __name__ == '__main__':
    unittest.main()
