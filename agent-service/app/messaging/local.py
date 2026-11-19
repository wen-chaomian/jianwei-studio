"""Optional local development bootstrap; remote deployments manage MQ separately."""
import asyncio
from pathlib import Path
import shutil


async def ensure_local_mq(settings):
    if not settings.mq_enabled or not settings.mq_autostart:
        return
    if settings.mq_endpoint != '127.0.0.1:8081':
        raise ValueError('Local MQ bootstrap requires the local endpoint')
    try:
        _, writer = await asyncio.open_connection('127.0.0.1', 8081)
        writer.close()
        await writer.wait_closed()
        return
    except OSError:
        pass
    root = Path(__file__).resolve().parents[3]
    node = shutil.which('node') or str(Path.home()/'.local/bin/node')
    process = await asyncio.create_subprocess_exec(node, str(root/'scripts/start-mq.mjs'),
        cwd=root, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.STDOUT)
    try:
        await asyncio.wait_for(process.communicate(), timeout=90)
    except TimeoutError:
        process.kill()
        await process.wait()
        raise RuntimeError('Local RocketMQ startup timed out') from None
    if process.returncode:
        raise RuntimeError('Local RocketMQ startup failed; inspect .local/rocketmq/logs')
