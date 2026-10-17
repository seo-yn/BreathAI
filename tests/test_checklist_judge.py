import unittest
from evaluation.checklist_judge import validate_content,validate_style,scores,agreement
class ChecklistTests(unittest.TestCase):
    def test_unknown_is_not_failure(self):
        v=scores(dict(fact='unknown',action='unknown',essential='fail'),dict(respectful='pass'))
        self.assertIsNone(v['accuracy']);self.assertIsNone(v['actionability']);self.assertEqual(v['essential'],1);self.assertEqual(v['politeness'],5)
    def test_quote_must_come_from_correct_source(self):
        payload=dict(answer='앱으로 신고해 주세요.',evidence=[dict(text='앱으로 신고할 수 있습니다.')])
        good=dict(fact='supported',support='앱으로 신고할 수',claim='앱으로 신고',relevant='pass',action='pass',essential='pass')
        validate_content(good,payload)
        with self.assertRaises(ValueError):validate_content({**good,'support':'전화로 신고'},payload)
        with self.assertRaises(ValueError):validate_style(dict(respectful='pass',quote='멍청아'),payload['answer'])
    def test_no_agreement_claim_without_labels(self):
        self.assertIsNone(agreement({}, {}, ['fact'])['exact_agreement'])
        v=agreement({'a':dict(fact='unknown',action='pass')},{'a':dict(fact='supported',action='pass')},['fact','action'])
        self.assertEqual(v['coverage'],0.5);self.assertEqual(v['exact_agreement'],1)
if __name__=='__main__':unittest.main()
