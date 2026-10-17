import unittest
from evaluation.stable_judge import canonical_input,cache_key,enforce,numbers

class StableJudgeTests(unittest.TestCase):
    def payload(self,answer='앱으로 신고해 주세요.'):
        return canonical_input('신고 방법?', [{'id':'a','text':'앱으로 신고해 주세요. 전화 1577-1234.'}],answer)
    def rating(self,**changes):
        x=dict(accuracy=5,politeness=5,actionability=5,essential=5,support_quote='앱으로 신고해 주세요.',unsupported_claim='',reason='Supported actionable answer')
        x.update(changes);return x
    def test_layout_invariance_but_numbers_preserved(self):
        a=self.payload();b=self.payload('앱으로\n\n신고해  주세요.');self.assertEqual(cache_key(a,'rev','prompt'),cache_key(b,'rev','prompt'))
        self.assertNotEqual(cache_key(a,'rev','prompt'),cache_key(self.payload('앱으로 신고해 주세요. 1350'),'rev','prompt'))
    def test_context_and_rubric_version_separate_cache(self):
        a=self.payload();self.assertNotEqual(cache_key(a,'a','p'),cache_key(a,'b','p'))
        self.assertNotEqual(cache_key(a,'a','p'),cache_key(a,'a','p2'))
        b=canonical_input('다른 질문',[{'id':'a','text':'앱으로 신고해 주세요.'}],a['answer']);self.assertNotEqual(cache_key(a,'a','p'),cache_key(b,'a','p'))
    def test_invalid_evidence_quote_caps_accuracy_only(self):
        x=enforce(self.rating(support_quote='존재하지 않는 인용'),self.payload());self.assertEqual(x['scores']['accuracy'],2);self.assertEqual(x['scores']['politeness'],5)
    def test_unverified_number_caps_actionability(self):
        x=enforce(self.rating(),self.payload('1350으로 전화해 주세요.'));self.assertEqual(x['scores']['accuracy'],2);self.assertEqual(x['scores']['actionability'],2);self.assertIn('1350',x['unverified_numbers'])
    def test_supported_phone_not_capped(self):
        x=enforce(self.rating(),self.payload('1577-1234로 전화해 주세요.'));self.assertNotIn('numbers_absent_from_reference',x['policy_flags'])
    def test_service_name_digits_are_not_hours(self):
        self.assertEqual(numbers('또타24 콜센터 1577-1234'),{'15771234'})
        self.assertEqual(numbers('또타 24는 24시간 운영합니다.'),{'24'})
        self.assertEqual(numbers('24시간 운영, 24호선, 1350'),{'24','1350'})
    def test_placeholders_and_leaks(self):
        x=enforce(self.rating(),self.payload('[연락처번호] 예비실험 길이 제한'));self.assertEqual(x['scores']['essential'],2);self.assertEqual(x['scores']['actionability'],2)
    def test_list_numbers_not_factual_claims(self):
        self.assertEqual(numbers('1. 앱 신고\n2) 전화 1577-1234'),{'15771234'})
        self.assertEqual(numbers('1. 앱 신고 2) 전화 1577-1234'),{'15771234'})
    def test_absent_evidence_not_scored_high(self):
        x=enforce(self.rating(),canonical_input('질문',[],'답변'));self.assertEqual(x['scores']['accuracy'],2)
    def test_unsupported_claim_cannot_be_copied_from_question(self):
        with self.assertRaises(ValueError):enforce(self.rating(unsupported_claim='신고 방법?'),self.payload())

if __name__=='__main__':unittest.main()
