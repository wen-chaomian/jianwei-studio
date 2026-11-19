import { createSseParser } from './sseStream.js'
// Snapshots replace output; reconnecting never submits another model request.
export function watchAgentRun({ runId, headers, onSnapshot, onState, fetcher = fetch }) {
  let stopped = false,
    controller,
    timer,
    attempt = 0
  async function connect() {
    if (stopped) return
    controller = new AbortController()
    let reader, idle
    const watch = () => {
      clearTimeout(idle)
      idle = setTimeout(() => controller.abort(), 20000)
    }
    let terminal = false
    try {
      onState('connecting')
      watch()
      const response = await fetcher(`/api/v1/agent/runs/${runId}/events`, {
        headers: { Accept: 'text/event-stream', ...headers },
        credentials: 'same-origin',
        signal: controller.signal,
        cache: 'no-store',
      })
      if (stopped) return
      if (response.status === 401 || response.status === 403 || response.status === 404) {
        onState('unauthorized')
        return
      }
      if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream'))
        throw Error('连接失败')
      onState('connected')
      const parse = createSseParser(({ event, data }) => {
        if (stopped) return
        if (event === 'unauthorized') {
          terminal = true
          onState('unauthorized')
          return
        }
        if (event !== 'snapshot') return
        const value = JSON.parse(data)
        if (value.id !== runId) return
        onSnapshot(value)
        terminal = value.status !== 'running'
      })
      reader = response.body.getReader()
      const decoder = new TextDecoder()
      while (!stopped && !terminal) {
        const { value, done } = await reader.read()
        if (done) break
        watch()
        parse(decoder.decode(value, { stream: true }))
      }
    } catch (error) {
      if (stopped) return
    } finally {
      clearTimeout(idle)
      if (reader) {
        try {
          await reader.cancel()
        } catch {}
        reader.releaseLock()
      }
    }
    if (stopped || terminal) return
    if (++attempt > 5) {
      onState('disconnected')
      return
    }
    onState('reconnecting')
    timer = setTimeout(connect, Math.min(8000, 500 * 2 ** attempt))
  }
  void connect()
  return {
    stop() {
      stopped = true
      clearTimeout(timer)
      controller?.abort()
    },
  }
}
