import json,unittest
from pathlib import Path
from evaluation.restart_prometheus import parse,prompt
from evaluation.restart_glider import parse as parse_glider,prompt as prompt_glider
ROOT=Path(__file__).resolve().parents[1]
class RestartTests(unittest.TestCase):
    def test_glider_score_and_style_isolated_from_reference(self):
        self.assertEqual(parse_glider('<reasoning>OK</reasoning><score>5</score>'),5)
        with self.assertRaises(ValueError):parse_glider('<score>10</score>')
        rubric=dict(criteria='Politeness',scores=['a','b','c','d','e'])
        p=prompt_glider('QUESTION','ANSWER','SECRET_REFERENCE','CONTEXT',rubric,'politeness')
        self.assertNotIn('SECRET_REFERENCE',p);self.assertIn('<MODEL OUTPUT>\nANSWER',p)
    def test_official_score_format_and_missing_not_defaulted(self):
        self.assertEqual(parse('Feedback: Correct. [RESULT] 4'),4)
        for value in ('4','[RESULT] 10','[RESULT] 0','[RESULT] 5 [RESULT] 4'):
            with self.assertRaises(ValueError):parse(value)
    @unittest.skipUnless((ROOT/'evaluation/results/restart-10-20261014/benchmark.json').exists(), 'Requires local benchmark artifact (excluded from Git)')
    def test_benchmark_disjointness_and_source_mapping(self):
        b=json.loads((ROOT/'evaluation/results/restart-10-20261014/benchmark.json').read_text(encoding='utf-8'))
        self.assertEqual(len(b['cases']),10);self.assertEqual(len({c['reference_id'] for c in b['cases']}),10)
        for c in b['cases']:
            self.assertFalse(set(c['context_row_indices'])&set(c['heldout_row_indices']))
            self.assertTrue(c['reference_answer'] in c['reference_source_text'])
    def test_axis_and_blinding_in_prompt(self):
        rubric=dict(criteria='Accuracy only',scores=['a','b','c','d','e'])
        p=prompt('QUESTION','ANSWER','REFERENCE',rubric)
        self.assertIn('Score 5: e',p);self.assertIn('###Reference Answer (Score 5):\nREFERENCE',p)
        self.assertNotIn('Llama',p);self.assertNotIn('Ours',p)
if __name__=='__main__':unittest.main()
