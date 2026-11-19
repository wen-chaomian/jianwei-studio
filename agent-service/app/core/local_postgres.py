"""Opt-in bootstrap of this project's existing development PostgreSQL cluster."""
import asyncio
from pathlib import Path
from psycopg.conninfo import conninfo_to_dict

ROOT = Path(__file__).resolve().parents[3]


async def ensure_local_postgres(settings):
    if not settings.postgres_autostart:
        return
    target = conninfo_to_dict(settings.database_url.get_secret_value())
    if target.get('host') not in ('127.0.0.1', 'localhost') or target.get('port', '5432') != '5432':
        raise RuntimeError('Local PostgreSQL autostart requires localhost:5432')
    ctl = ROOT / '.local/Postgres.app/Contents/Versions/18/bin/pg_ctl'
    data = ROOT / '.local/postgres-data'
    if not ctl.is_file() or not (data / 'PG_VERSION').is_file():
        raise RuntimeError('Local PostgreSQL installation or existing data directory is missing')

    async def run(*args):
        process = await asyncio.create_subprocess_exec(
            str(ctl), '-D', str(data), *args,
            stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL,
        )
        try:
            return await asyncio.wait_for(process.wait(), 40)
        except TimeoutError:
            process.kill()
            await process.wait()
            raise RuntimeError('Local PostgreSQL startup timed out; inspect .local/postgres.log') from None

    if await run('status') == 0:
        return
    code = await run('-l', str(ROOT / '.local/postgres.log'), '-o',
                     '-h 127.0.0.1 -p 5432', '-w', '-t', '30', 'start')
    # Another simultaneous starter may have won; never remove PID/data files.
    if code and await run('status') != 0:
        raise RuntimeError('Local PostgreSQL startup failed; inspect .local/postgres.log')
