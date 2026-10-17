"""New specialized evaluator using the published Prometheus direct-assessment format."""
import re
SYSTEM='You are a fair judge assistant tasked with providing clear, objective feedback based on specific criteria, ensuring each assessment reflects the absolute standards set for performance.'
def prompt(question,answer,reference,rubric):
    descriptions='\n'.join(f'Score {i}: {text}' for i,text in enumerate(rubric['scores'],1))
    return f'''###Task Description:
An instruction (might include an Input inside it), a response to evaluate, a reference answer that gets a score of 5, and a score rubric representing a evaluation criteria are given.
1. Write a detailed feedback that assess the quality of the response strictly based on the given score rubric, not evaluating in general.
2. After writing a feedback, write a score that is an integer between 1 and 5. You should refer to the score rubric.
3. The output format should look as follows: "Feedback: (write a feedback for criteria) [RESULT] (an integer number between 1 and 5)"
4. Please do not generate any other opening, closing, and explanations.

###The instruction to evaluate:
Answer the following Seoul Metro service question in Korean. Treat the reference FAQ as the factual basis: {question}

###Response to evaluate:
{answer}

###Reference Answer (Score 5):
{reference}

###Score Rubrics:
[{rubric['criteria']}]
{descriptions}

###Feedback:
'''
def parse(text):
    matches=re.findall(r'\[RESULT\]\s*([1-5])(?!\d)',text)
    if len(matches)!=1:raise ValueError('Expected one published [RESULT] score, received '+repr(matches))
    return int(matches[0])
