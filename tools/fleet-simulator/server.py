"""Two explicitly simulated LAN agents. Never operates real Android applications."""
import argparse,json,threading,time,secrets,subprocess,struct,pathlib,uuid
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from urllib.parse import urlsplit
p=argparse.ArgumentParser();p.add_argument('--frames',required=True);args=p.parse_args()
class Device:
 def __init__(self,label,port):
  self.label=label;self.name='设备 '+label;self.token=pathlib.Path(args.frames.replace('{label}',label)).with_suffix('.token').read_text().strip() if pathlib.Path(args.frames.replace('{label}',label)).with_suffix('.token').exists() else secrets.token_hex(32);self.port=port;self.lock=threading.RLock();self.seq=1;self.pairs=set();self.seen=set()
  self.state=dict(phase='idle',detail='设备已就绪',runId='',busy=False,device=self.name,updatedAt=time.time(),messages=[],currentConversationId='mock-current-'+label,conversationId='mock-current-'+label,chatRoutingVersion=3,eventStream=True,exchanges=[])
  data=pathlib.Path(args.frames.replace('{label}',label)).read_bytes();self.frames=[]
  while data:
   n=struct.unpack('>I',data[:4])[0];self.frames.append(data[:4+n]);data=data[4+n:]
 def run(self,text,cid):
  time.sleep(3 if self.label=='A' else 5)
  with self.lock:
   self.state.update(phase='completed',busy=False,detail=self.name+' 已收到：'+text+'。',updatedAt=time.time());ex=self.state['exchanges'][-1];ex.update(phase='done',body=self.state['detail'],progress='',elapsed=5);self.seq+=1
 def handler(self):
  d=self
  class Handler(BaseHTTPRequestHandler):
   protocol_version='HTTP/1.1'
   def log_message(self,*a):pass
   def json(self,value,code=200):
    body=json.dumps(value,ensure_ascii=False).encode();self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
   def do_POST(self):self.handle_request()
   def do_GET(self):self.handle_request()
   def handle_request(self):
    path=urlsplit(self.path).path
    try:
     body=json.loads(self.rfile.read(int(self.headers.get('Content-Length',0))) or b'{}')
     if path=='/pair':d.pairs.add(body['requestId']);return self.json(dict(status='pending'))
     if path.startswith('/pair/'):
      return self.json(dict(status='approved',token=d.token) if path[6:] in d.pairs else dict(status='expired'))
     if self.headers.get('Authorization')!='Bearer '+d.token:return self.json(dict(error='unauthorized'),401)
     if path=='/state':return self.json(d.state)
     if path=='/messages':return self.json(dict(messages=[],revision=0))
     if path=='/virtual/status':return self.json(dict(active=True,width=360,height=780,error=''))
     if path=='/virtual/video':
      self.send_response(200);self.send_header('Content-Type','application/octet-stream');self.send_header('Connection','close');self.end_headers()
      while True:
       for frame in d.frames:
        self.wfile.write(frame);self.wfile.flush()
        if len(frame)>8 and frame[8]&31 in (1,5):time.sleep(.1)
     if path=='/events':
      self.send_response(200);self.send_header('Content-Type','text/event-stream');self.send_header('Connection','close');self.end_headers();last=-1;heartbeat=0;sequence=0
      while True:
       with d.lock:
        if last!=d.seq:
         last=d.seq;sequence+=1;self.wfile.write(('data: '+json.dumps(dict(kind='snapshot' if sequence==1 else 'state',sequence=sequence,snapshot=d.state),ensure_ascii=False)+'\n\n').encode());self.wfile.flush();heartbeat=time.time()
       if time.time()-heartbeat>15:self.wfile.write(b': heartbeat\n\n');self.wfile.flush();heartbeat=time.time()
       time.sleep(.15)
     if path=='/command':
      with d.lock:
       cid=body.get('commandId',body.get('id',''));text=body.get('text','')
       if cid in d.seen:return self.json(dict(status='accepted',conversationId=d.state['currentConversationId']),202)
       if body.get('action')!='task':return self.json(dict(error='unsupported simulation action'),400)
       if d.state['busy']:return self.json(dict(error='busy'),409)
       d.seen.add(cid);now=time.time();d.state.update(phase='running',busy=True,detail='正在处理',updatedAt=now,commandId=cid,goal=text,runId=cid)
       d.state['exchanges'].append(dict(id=cid,goal=text,phase='working',body='',draft='',progress='正在处理',startedAt=now,title='',verification='',evidence='',options=[],steps=[],steers=[],notes=[],files=[]));d.seq+=1
       threading.Thread(target=d.run,args=(text,cid),daemon=True).start()
       return self.json(dict(status='accepted',conversationId=d.state['currentConversationId']),202)
     if path=='/chats':return self.json(dict(currentConversationId=d.state['currentConversationId'],chats=[]))
     return self.json(dict(error='not implemented in simulator'),404)
    except (BrokenPipeError,ConnectionResetError):pass
  return Handler
children=[]
try:
 for label,port in [('A',19301),('B',19302)]:
  device=Device(label,port);server=ThreadingHTTPServer(('0.0.0.0',port),device.handler());threading.Thread(target=server.serve_forever,daemon=True).start()
  children.append(subprocess.Popen(['dns-sd','-R','agent-simulation-'+label,'_mika-agent._tcp','local',str(port),'id='+str(uuid.uuid5(uuid.NAMESPACE_DNS,'mika-fleet-simulation-'+label)),'name='+device.name,'transport=tcp-http-v1','simulation=1'],stdout=subprocess.DEVNULL))
  print(device.name,'listening on',port,flush=True)
 while True:time.sleep(30)
finally:
 for child in children:child.terminate()
