"""Expose an ADB-forwarded real emulator LAN service to the same Wi-Fi network."""
import asyncio,argparse,subprocess
p=argparse.ArgumentParser();p.add_argument('--port',type=int,required=True);p.add_argument('--target',type=int,required=True);p.add_argument('--id',required=True);p.add_argument('--name',required=True);a=p.parse_args()
async def pipe(reader,writer):
 try:
  while data:=await reader.read(65536):writer.write(data);await writer.drain()
 except (ConnectionError,asyncio.CancelledError):pass
 finally:writer.close()
async def client(reader,writer):
 try:r,w=await asyncio.open_connection('127.0.0.1',a.target)
 except OSError:writer.close();return
 await asyncio.gather(pipe(reader,w),pipe(r,writer))
async def main():
 server=await asyncio.start_server(client,'0.0.0.0',a.port)
 async with server:await server.serve_forever()
dns=subprocess.Popen(['dns-sd','-R',a.name,'_mika-agent._tcp','local',str(a.port),'id='+a.id,'name='+a.name,'transport=tcp-http-v1'],stdout=subprocess.DEVNULL)
try:asyncio.run(main())
finally:dns.terminate()
