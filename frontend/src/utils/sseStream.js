// Fetch streaming preserves our portal/tab headers; native EventSource cannot set them.
export function createSseParser(onEvent) {
  let buffer = '',
    event = 'message',
    data = []
  function line(text) {
    if (text === '') {
      if (data.length) onEvent({ event, data: data.join('\n') })
      event = 'message'
      data = []
      return
    }
    if (text.startsWith(':')) return
    const colon = text.indexOf(':'),
      field = colon < 0 ? text : text.slice(0, colon)
    let value = colon < 0 ? '' : text.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    if (field === 'data') data.push(value)
  }
  return (chunk) => {
    buffer += chunk
    if (buffer.length > 65536) throw new Error('推送数据过长')
    let n
    while ((n = buffer.indexOf('\n')) >= 0) {
      line(buffer.slice(0, n).replace(/\r$/, ''))
      buffer = buffer.slice(n + 1)
    }
  }
}
export function createSseStream({
  url,
  headers,
  onVersion,
  onState = () => {},
  fetcher = fetch,
  schedule = setTimeout,
  cancel = clearTimeout,
  random = Math.random,
}) {
  let stopped = false,
    controller,
    timer,
    idleTimer,
    attempt = 0,
    sequence = 0
  async function connect() {
    const run = ++sequence
    controller = new AbortController()
    onState('connecting')
    const watch = () => {
      cancel(idleTimer)
      idleTimer = schedule(() => controller.abort(), 45000)
    }
    watch()
    let reader
    try {
      const response = await fetcher(url, {
        headers: { Accept: 'text/event-stream', ...headers },
        credentials: 'same-origin',
        cache: 'no-store',
        signal: controller.signal,
      })
      if (stopped || run !== sequence) return
      if (response.status === 401 || response.status === 403) {
        onState('unauthorized')
        return
      }
      if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream'))
        throw new Error('实时连接不可用')
      onState('connected')
      reader = response.body.getReader()
      const decoder = new TextDecoder()
      const parse = createSseParser(({ event, data }) => {
        if (event !== 'version' || stopped || run !== sequence) return
        const snapshot = JSON.parse(data)
        if (snapshot.available && typeof snapshot.version === 'string') {
          attempt = 0
          onVersion(snapshot)
        }
      })
      while (!stopped && run === sequence) {
        const result = await reader.read()
        if (result.done) break
        watch()
        parse(decoder.decode(result.value, { stream: true }))
      }
    } catch (error) {
      if (stopped || run !== sequence) return
    } finally {
      cancel(idleTimer)
      if (reader) {
        try {
          await reader.cancel()
        } catch {}
        reader.releaseLock()
      }
    }
    if (stopped || run !== sequence) return
    onState('disconnected')
    const delay = Math.min(30000, 1000 * 2 ** Math.min(attempt++, 5)) + Math.floor(random() * 500)
    timer = schedule(connect, delay)
  }
  void connect()
  return {
    stop() {
      stopped = true
      sequence++
      cancel(timer)
      cancel(idleTimer)
      controller?.abort()
      onState('closed')
    },
  }
}
