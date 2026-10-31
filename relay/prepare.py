#!/usr/bin/env python3
"""Pair a development build over USB; never prints pairing tokens or message content."""
import argparse, hashlib, json, os, pathlib, secrets, subprocess, urllib.parse
p=argparse.ArgumentParser()
p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
p.add_argument('--host',required=True,help='Mac LAN address reachable from iPhone')
p.add_argument('--state-dir',required=True);p.add_argument('--simulator',action='store_true')
a=p.parse_args();d=pathlib.Path(a.state_dir).resolve();d.mkdir(parents=True,exist_ok=True);d.chmod(0o700)
subprocess.run([a.adb,'-s',a.serial,'forward','tcp:18765','tcp:18765'],check=True,capture_output=True)
token=subprocess.check_output([a.adb,'-s',a.serial,'shell','run-as','com.wangzan101.mika','cat','files/remote-token'],text=True).strip()
if len(token)!=64:raise SystemExit('Android bridge not ready')
if not (d/'cert.pem').exists():
 subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(d/'key.pem'),'-out',str(d/'cert.pem'),'-days','365','-subj','/CN=Mika Local Relay'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
config=json.loads((d/'config.json').read_text()) if (d/'config.json').exists() else {'token':secrets.token_hex(32),'port':19443}
config['androidToken']=token
(d/'config.json').write_text(json.dumps(config));(d/'config.json').chmod(0o600);(d/'key.pem').chmod(0o600)
der=subprocess.check_output(['openssl','x509','-in',str(d/'cert.pem'),'-outform','DER'])
url='mikaremote://pair?'+urllib.parse.urlencode({'url':f'https://{a.host}:19443','token':config['token'],'pin':hashlib.sha256(der).hexdigest()})
(d/'pairing-url.txt').write_text(url);(d/'pairing-url.txt').chmod(0o600)
if a.simulator:subprocess.run(['xcrun','simctl','openurl','booted',url],check=True,stdout=subprocess.DEVNULL)
print('USB forwarding and private pairing file ready. Start server.mjs with MIKA_STATE_DIR set to this directory.')
