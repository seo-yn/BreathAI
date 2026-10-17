import json,unittest
from evaluation.constrained_choices import style_completions,prefix_constraint
class FakeTokenizer:
    eos_token_id=0
    def encode(self,text,add_special_tokens=False):return [ord(c) for c in text]
class FakeIds(list):
    def tolist(self):return list(self)
class ChoiceTests(unittest.TestCase):
    def test_all_labels_equal_options_with_real_quotes(self):
        answer='멍청아, 앱으로 신고해 주세요.'
        rows=[json.loads(x) for x in style_completions(answer)]
        sets={k:{r['quote'] for r in rows if r['respectful']==k} for k in ('pass','fail','unknown')}
        self.assertEqual(sets['pass'],sets['fail']);self.assertEqual(sets['fail'],sets['unknown'])
        self.assertTrue(all(r['quote'] in answer for r in rows))
    def test_grammar_cannot_create_other_keys_or_labels(self):
        tok=FakeTokenizer();options=['{"respectful":"pass"}','{"respectful":"fail"}']
        fn=prefix_constraint(tok,options,2)
        prefix=FakeIds([999,999])
        for ch in options[1]:
            self.assertIn(ord(ch),fn(0,prefix));prefix.append(ord(ch))
        self.assertEqual(fn(0,prefix),[0])
        with self.assertRaises(ValueError):fn(0,FakeIds([999,999,123,999]))
if __name__=='__main__':unittest.main()
