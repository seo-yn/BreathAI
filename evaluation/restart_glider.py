"""Publisher's GLIDER tag format and 1-5 rubric, without score correction rules."""
import re
def prompt(question,answer,reference,context,rubric,axis):
    if axis=='politeness':data='<MODEL OUTPUT>\n'+answer+'\n</MODEL OUTPUT>'
    else:data=f'<USER INPUT>\n{question}\n</USER INPUT>\n<MODEL OUTPUT>\n{answer}\n</MODEL OUTPUT>\n<GOLD ANSWER>\n{reference}\n</GOLD ANSWER>\n<CONTEXT>\n{context}\n</CONTEXT>'
    criteria='Evaluate only <MODEL OUTPUT>. '+rubric['criteria']
    if axis!='politeness':criteria+=' Use <USER INPUT> for the question, and <GOLD ANSWER> and <CONTEXT> for factual support.'
    scores='\n'.join(f'Score {i}: {s}' for i,s in enumerate(rubric['scores'],1))
    return f'''Analyze the following pass criteria carefully and score the text based on the rubric defined below.

To perform this evaluation, you must:
1. Understand the text tags, pass criteria and rubric thoroughly.
2. Review the finer details of the text and the rubric.
3. Compare the tags to be evaluated to the score descriptions in the rubric.
4. Pay close attention to small details that might impact the final score and form accurate associations between tags and pass criteria.
5. Write a detailed reasoning justifying your evaluation in a bullet point format.
6. The reasoning must summarize the overall strengths and weaknesses of the output while quoting exact phrases from the output wherever required.
7. Output a list of words or phrases that you believe are the most important in determining the score.
8. Assign a final score based on the scoring rubric.

Data to evaluate:
{data}

Pass Criteria:
{criteria}

Rubric:
{scores}

Your output must in the following format:
<reasoning>
[Detailed reasoning justifying your evaluation in a bullet point format according to the specifics defined above]
</reasoning>
<highlight>
[List of words or phrases that you believe are the most important in determining the score]
</highlight>
<score>
[The final integer score assigned based on the scoring rubric]
</score>
'''
def parse(text):
    scores=re.findall(r'<score>\s*([1-5])\s*</score>',text,re.I)
    if len(scores)!=1:raise ValueError('Expected exactly one GLIDER <score> 1..5')
    return int(scores[0])
