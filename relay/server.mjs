import https from 'node:https';
import http from 'node:http';
import http2 from 'node:http2';
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';

const dir = path.resolve(process.env.MIKA_STATE_DIR || './.local');
const read = name => fs.readFileSync(path.join(dir, name));
const config = JSON.parse(read('config.json'));
const token = config.token;
const equal = value => {
  const a = Buffer.from(value || ''), b = Buffer.from('Bearer ' + token);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};
function upstream(method, route, body) {
  return new Promise((resolve, reject) => {
    const request = http.request({host:'127.0.0.1', port:config.androidPort||18765, method, path:route,
      headers:{Authorization:'Bearer '+config.androidToken, ...(body ? {'Content-Length':body.length,'Content-Type':'application/json'} : {})}, timeout:6000}, response => {
        const parts=[]; let size=0;
        response.on('data', data => { size+=data.length; if(size>2_000_000) response.destroy(); else parts.push(data); });
        response.on('end', ()=>resolve({code:response.statusCode, data:Buffer.concat(parts)})); response.on('error',reject);
      });
    request.on('error', reject); request.on('timeout',()=>request.destroy(new Error('timeout')));
    request.end(body);
  });
}
const activities = new Map();
let lastSnapshot = null;
function apnsReady() { return !!(process.env.APNS_KEY_PATH && process.env.APNS_KEY_ID && process.env.APNS_TEAM_ID); }
function jwt() {
  const b64 = obj => Buffer.from(JSON.stringify(obj)).toString('base64url');
  const unsigned = b64({alg:'ES256',kid:process.env.APNS_KEY_ID})+'.'+b64({iss:process.env.APNS_TEAM_ID,iat:Math.floor(Date.now()/1000)});
  return unsigned+'.'+crypto.sign('sha256',Buffer.from(unsigned), {key:fs.readFileSync(process.env.APNS_KEY_PATH),dsaEncoding:'ieee-p1363'}).toString('base64url');
}
async function push(activity, snapshot, event='update') {
  if (!apnsReady()) return;
  const phase = snapshot?.phase || 'offline';
  const payload = {aps:{timestamp:Math.floor(Date.now()/1000),event,
    'content-state':{phase,detail:phase,updatedAt:Date.now()/1000-978307200,messageCount:snapshot?.messages?.length || 0},
    'stale-date':Math.floor(Date.now()/1000)+45,...(event==='end'?{'dismissal-date':Math.floor(Date.now()/1000)+60}:{})}};
  await new Promise((resolve,reject)=>{
    const session = http2.connect(process.env.APNS_PRODUCTION==='1'?'https://api.push.apple.com':'https://api.sandbox.push.apple.com');
    session.on('error',reject);
    const req=session.request({':method':'POST',':path':'/3/device/'+activity.token,
      authorization:'bearer '+jwt(),'apns-topic':(process.env.APNS_BUNDLE_ID||'com.kassen.mikaremote')+'.push-type.liveactivity',
      'apns-push-type':'liveactivity','apns-priority':'5','apns-expiration':'0'});
    let status; req.on('response',h=>status=h[':status']); req.on('data',()=>{});
    req.on('end',()=>{session.close(); if(status===200) resolve(); else reject(new Error('APNs HTTP '+status));});
    req.on('error',e=>{session.close();reject(e);}); req.setTimeout(10000,()=>{req.close();session.close();reject(new Error('APNs timeout'));});
    req.end(JSON.stringify(payload));
  });
}
https.createServer({key:read('key.pem'),cert:read('cert.pem'),minVersion:'TLSv1.2'}, async(req,res)=>{
  const respond=(code,value)=>{res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store'});res.end(Buffer.isBuffer(value)?value:JSON.stringify(value));};
  if(!equal(req.headers.authorization)) return respond(401,{error:'unauthorized'});
  if(req.headers.origin) return respond(403,{error:'browser_origin_not_allowed'});
  try {
    if(req.method==='GET'&&req.url==='/capabilities') return respond(200,{apns:apnsReady(),transport:'usb-mac-relay'});
    if(!((req.method==='GET'&&req.url==='/state')||(req.method==='POST'&&['/command','/activity'].includes(req.url)))) return respond(404,{error:'not_found'});
    let body;
    if(req.method==='POST') {
      const parts=[];let size=0;
      for await(const chunk of req){size+=chunk.length;if(size>16384){respond(413,{error:'too_large'});return;}parts.push(chunk);}
      body=Buffer.concat(parts); const value=JSON.parse(body);
      if(req.url==='/activity') {
        if(!/^[a-f0-9]{32,512}$/.test(value.token)||typeof value.runId!=='string'||value.runId.length>100) return respond(400,{error:'invalid_activity'});
        if(activities.size>20) activities.delete(activities.keys().next().value);
        activities.set(value.token,{...value,signature:'',at:Date.now(),lastPush:0});
        return respond(200,{registered:true,apns:apnsReady()});
      }
    }
    const result=await upstream(req.method,req.url,body); respond(result.code,result.data);
  }catch { respond(503,{error:'android_unavailable'}); }
}).listen(config.port||19443,config.bind||'0.0.0.0',()=>console.log('Mika TLS relay ready; APNs '+(apnsReady()?'configured':'not configured')));
let polling=false;
setInterval(async()=>{
  if(polling||!activities.size||!apnsReady()) return;
  polling=true;
  try {
    const response=await upstream('GET','/state');if(response.code!==200)throw Error();
    const s=JSON.parse(response.data);lastSnapshot=s;
    for(const [token,a] of activities){
      if(Date.now()-a.at>8*3600000){activities.delete(token);continue;}
      const ended=s.runId!==a.runId||['completed','failed','cancelled','idle'].includes(s.phase);
      const signature=JSON.stringify([s.phase,s.messages.length]);
      if(a.retryAfter && Date.now()<a.retryAfter)continue;
      if(a.signature===signature&&Date.now()-a.lastPush<30000)continue;
      try {await push(a,s,ended?'end':'update');a.signature=signature;a.lastPush=Date.now();if(ended)activities.delete(token);} catch(e){console.error(e.message);a.retryAfter=Date.now()+60000;}
    }
  }catch{/* The last Activity becomes stale automatically; never fabricate a successful update. */}
  finally{polling=false;}
},3000).unref();
