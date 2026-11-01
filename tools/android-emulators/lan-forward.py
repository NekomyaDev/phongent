"""Expose a real emulator LAN service; follow its port after app restarts."""
import asyncio
import argparse
import re
import subprocess

p = argparse.ArgumentParser()
p.add_argument('--port', type=int, required=True)
p.add_argument('--target', type=int, required=True)
p.add_argument('--id', required=True)
p.add_argument('--name', required=True)
p.add_argument('--serial', help='ADB device whose changing LAN port should be followed')
p.add_argument('--adb', default='adb')
a = p.parse_args()

async def adb(*args):
    process = await asyncio.create_subprocess_exec(
        a.adb, '-s', a.serial, *args,
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)
    try:
        stdout, _ = await asyncio.wait_for(process.communicate(), timeout=8)
    except asyncio.TimeoutError:
        process.kill()
        await process.communicate()
        raise ConnectionError('ADB timed out')
    if process.returncode:
        raise ConnectionError('ADB unavailable')
    return stdout.decode()

async def follow_port():
    previous = None
    while True:
        try:
            state = await adb('shell', 'ss', '-ltn')
            ports = {int(port) for port in re.findall(r'\*:(\d+)\s', state)} - {5555}
            # Do not guess if another externally bound service is present.
            if len(ports) != 1:
                raise ConnectionError('LAN service not ready or ambiguous')
            port = ports.pop()
            # Preserve active connections; repair only missing or changed mappings.
            mappings = await adb('forward', '--list')
            expected = f'{a.serial} tcp:{a.target} tcp:{port}'
            if expected not in {line.strip() for line in mappings.splitlines()}:
                await adb('forward', f'tcp:{a.target}', f'tcp:{port}')
            if previous != port:
                print(f'{a.name}: LAN forwarding updated to {port}', flush=True)
                previous = port
        except (OSError, ConnectionError):
            previous = None
        await asyncio.sleep(3)

async def pipe(reader, writer):
    try:
        while data := await reader.read(65536):
            writer.write(data)
            await writer.drain()
    except (ConnectionError, asyncio.CancelledError):
        pass
    finally:
        writer.close()

async def client(reader, writer):
    try:
        r, w = await asyncio.open_connection('127.0.0.1', a.target)
    except OSError:
        writer.close()
        return
    await asyncio.gather(pipe(reader, w), pipe(r, writer))

async def main():
    watcher = asyncio.create_task(follow_port()) if a.serial else None
    try:
        server = await asyncio.start_server(client, '0.0.0.0', a.port)
        async with server:
            await server.serve_forever()
    finally:
        if watcher:
            watcher.cancel()
            await asyncio.gather(watcher, return_exceptions=True)

dns = subprocess.Popen([
    'dns-sd', '-R', a.name, '_mika-agent._tcp', 'local', str(a.port),
    'id=' + a.id, 'name=' + a.name, 'transport=tcp-http-v1'], stdout=subprocess.DEVNULL)
try:
    asyncio.run(main())
finally:
    dns.terminate()
