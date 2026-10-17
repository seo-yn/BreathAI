"""Owned local llama.cpp server; explicit non-sampling parameters and audit trail."""
import json,os,socket,subprocess,time,urllib.request,urllib.error
from pathlib import Path

class LocalModel:
    def __init__(self,manifest,role,out):
        self.manifest=manifest;self.role=role;self.out=Path(out);self.process=None;self.log=None
        self.file=next(f for f in manifest['files'] if f['role']==role)
    def start(self):
        layers=self.file.get('gpu_layer_candidates',{'judge':[99,28,24],'qwen':[30,26,22],'llama':[20,16,12]}[self.role])
        errors=[]
        for ngl in layers:
            with socket.socket() as sock:sock.bind(('127.0.0.1',0));self.port=sock.getsockname()[1]
            path=self.out/f'server-{self.role}-layers{ngl}.log';self.log=path.open('a',encoding='utf-8')
            args=[self.manifest['server'],'-m',self.file['path'],'--host','127.0.0.1','--port',str(self.port),
                '-ngl',str(ngl),'-c',str(self.file.get('context_length',4096)),'-np','1','-b','128','-ub','128','-t','8','-tb','8','--seed','42','--temp','0','--top-k','1','--top-p','1','--repeat-penalty','1']
            self.process=subprocess.Popen(args,stdout=self.log,stderr=subprocess.STDOUT,cwd=Path(self.manifest['server']).parent,
                creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
            self.out.joinpath('owned-server.json').write_text(json.dumps(dict(pid=self.process.pid,role=self.role,port=self.port,args=args,active=True)),encoding='utf-8')
            self.base=f'http://127.0.0.1:{self.port}';t=time.monotonic()
            while time.monotonic()-t<180 and self.process.poll() is None:
                try:
                    with urllib.request.urlopen(self.base+'/health',timeout=2) as response:
                        if response.status==200:
                            self.ngl=ngl;self.out.joinpath('owned-server.json').write_text(json.dumps(dict(pid=self.process.pid,role=self.role,port=self.port,args=args,active=True)),encoding='utf-8')
                            print('SERVER_READY',self.role,'GPU layers',ngl,flush=True);return self
                except (OSError,urllib.error.URLError) as exc:
                    if getattr(getattr(exc,'reason',exc),'winerror',None)==10013:
                        self.stop();raise PermissionError('Local server access denied by sandbox') from exc
                    time.sleep(0.5)
            errors.append(str(path));self.stop();time.sleep(1)
        raise RuntimeError('Local server failed: '+', '.join(errors))
    def stop(self):
        if self.process and self.process.poll() is None:
            self.process.terminate()
            try:self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:self.process.kill();self.process.wait()
        if self.log:self.log.close()
        ownership=self.out/'owned-server.json'
        if self.process and ownership.exists():
            value=json.loads(ownership.read_text(encoding='utf-8'))
            if value['pid']==self.process.pid:
                value.update(active=False,stopped_at_unix=time.time());ownership.write_text(json.dumps(value),encoding='utf-8')
    def complete(self,prompt,system,max_tokens=256):
        messages=([dict(role='system',content=system)] if system else [])+[dict(role='user',content=prompt)]
        payload=dict(messages=messages,
            max_tokens=max_tokens,temperature=0,top_k=1,top_p=1,min_p=0,repeat_penalty=1,seed=42,stream=False,cache_prompt=False)
        data=json.dumps(payload,ensure_ascii=False).encode();t=time.perf_counter()
        req=urllib.request.Request(self.base+'/v1/chat/completions',data=data,headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=300) as response:raw=json.load(response)
        choice=raw['choices'][0];answer=choice['message']['content'].strip()
        return dict(text=answer,finish_reason=choice['finish_reason'],usage=raw.get('usage',{}),seconds=time.perf_counter()-t,
            role=self.role,gpu_layers=self.ngl,prompt=prompt,system=system,request_parameters={k:v for k,v in payload.items() if k!='messages'})
    def __enter__(self):return self.start()
    def __exit__(self,*args):self.stop()
