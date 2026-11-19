from types import SimpleNamespace
from unittest.mock import AsyncMock
import pytest
from pydantic import SecretStr
from app.core import local_postgres as local


def settings(enabled=True, url='postgresql://localhost:5432/test'):
    return SimpleNamespace(postgres_autostart=enabled, database_url=SecretStr(url))


@pytest.mark.asyncio
async def test_disabled_does_not_spawn(monkeypatch):
    spawn = AsyncMock()
    monkeypatch.setattr(local.asyncio, 'create_subprocess_exec', spawn)
    await local.ensure_local_postgres(settings(False))
    spawn.assert_not_called()


@pytest.mark.asyncio
async def test_remote_rejected():
    with pytest.raises(RuntimeError, match='localhost:5432'):
        await local.ensure_local_postgres(settings(url='postgresql://remote:5432/test'))


@pytest.mark.asyncio
@pytest.mark.parametrize('codes, calls', [([0], 1), ([3, 0], 2), ([3, 1, 0], 3)])
async def test_existing_and_cold_start(monkeypatch, tmp_path, codes, calls):
    monkeypatch.setattr(local, 'ROOT', tmp_path)
    ctl = tmp_path / '.local/Postgres.app/Contents/Versions/18/bin/pg_ctl'
    ctl.parent.mkdir(parents=True)
    ctl.touch()
    data = tmp_path / '.local/postgres-data'
    data.mkdir()
    (data / 'PG_VERSION').write_text('18')
    spawn = AsyncMock(side_effect=[SimpleNamespace(wait=AsyncMock(return_value=c)) for c in codes])
    monkeypatch.setattr(local.asyncio, 'create_subprocess_exec', spawn)
    await local.ensure_local_postgres(settings())
    assert spawn.call_count == calls
    if calls > 1:
        assert 'start' in spawn.call_args_list[1].args
