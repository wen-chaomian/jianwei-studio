import { spawn, spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const data = path.join(root, '.local/postgres-data')
const ctl = path.join(root, '.local/Postgres.app/Contents/Versions/18/bin/pg_ctl')
if (existsSync(ctl) && existsSync(data)) {
  const status = spawnSync(ctl, ['-D', data, 'status'], { stdio: 'ignore' })
  if (status.status !== 0) {
    const started = spawnSync(
      ctl,
      [
        '-D',
        data,
        '-l',
        path.join(root, '.local/postgres.log'),
        '-o',
        '-h 127.0.0.1 -p 5432',
        'start',
      ],
      { stdio: 'inherit' },
    )
    if (started.status !== 0) process.exit(1)
  }
}
const cwd = path.join(root, 'agent-service')
const python =
  process.platform === 'win32'
    ? path.join(cwd, '.venv/Scripts/python.exe')
    : path.join(cwd, '.venv/bin/python')
if (!existsSync(python)) {
  console.error('请先创建 agent-service/.venv 并安装 requirements.lock 中的依赖。')
  process.exit(1)
}
console.log('启动见微 Agent（仅本机 8000）。三端仍通过 Java 的 8080 访问。')
console.log('模型和 API Key 在管理端 → 见微助手 → 模型配置中保存。')
const child = spawn(
  python,
  ['-m', 'uvicorn', 'app.main:app', '--host', '127.0.0.1', '--port', '8000', '--no-access-log'],
  { cwd, stdio: 'inherit' },
)
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal))
child.on('exit', (code) => process.exit(code ?? 0))
