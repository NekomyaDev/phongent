import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawn,execFileSync} from 'node:child_process';
import {once} from 'node:events';

test('TLS relay rejects strangers and browser origins, forwards only authenticated commands',async()=>{
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'mika-relay-test-'));
  execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-keyout',dir+'/key.pem','-out',dir+'/cert.pem','-days','1','-subj','/CN=localhost'],{stdio:'ignore'});
  let commands=0;
  const upstream=http.createServer((req,res)=>{assert.equal(req.headers.authorization,'Bearer android-test');if(req.url==='/command')commands++;res.setHeader('Content-Type','application/json');res.end(JSON.stringify({ok:true}));});
  upstream.listen(0,'127.0.0.1');await once(upstream,'listening');
  const socket=http.createServer();socket.listen(0,'127.0.0.1');await once(socket,'listening');const port=socket.address().port;await new Promise(r=>socket.close(r));
  fs.writeFileSync(dir+'/config.json',JSON.stringify({token:'ios-test',androidToken:'android-test',androidPort:upstream.address().port,port,bind:'127.0.0.1'}));
  const relay=spawn(process.execPath,[new URL('./server.mjs',import.meta.url).pathname],{env:{...process.env,MIKA_STATE_DIR:dir},stdio:['ignore','pipe','pipe']});
  try {
    await once(relay.stdout,'data');
    const request=(route,{auth='Bearer ios-test',origin,method='GET',body}={})=>new Promise((resolve,reject)=>{
      const req=https.request({host:'localhost',port,path:route,method,ca:fs.readFileSync(dir+'/cert.pem'),headers:{authorization:auth,...(origin?{origin}:{}),'Content-Type':'application/json'}},res=>{let data='';res.on('data',d=>data+=d);res.on('end',()=>resolve({code:res.statusCode,body:JSON.parse(data)}));});req.on('error',reject);req.end(body);
    });
    assert.equal((await request('/state',{auth:'Bearer wrong'})).code,401);
    assert.equal((await request('/command',{method:'POST',origin:'https://untrusted.example',body:'{}'})).code,403);
    assert.equal((await request('/arbitrary')).code,404);
    assert.equal((await request('/state')).code,200);
    assert.equal((await request('/command',{method:'POST',body:JSON.stringify({id:'test',action:'task',text:'fixture only'})})).code,200);
    assert.equal(commands,1);
    assert.equal((await request('/capabilities')).body.apns,false);
    assert.equal((await request('/activity',{method:'POST',body:'{"token":"invalid","runId":"x"}'})).code,400);
  } finally {relay.kill();upstream.close();fs.rmSync(dir,{recursive:true,force:true});}
});
