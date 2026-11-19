import { createSseStream } from './sseStream'
import { sessionHeaders } from './portalSession'
const streams = new Map()
export function subscribeSync(portal, catalog, listener) {
  const headers = sessionHeaders(portal),
    key = JSON.stringify([headers, catalog])
  let entry = streams.get(key)
  if (!entry) {
    entry = { listeners: new Set(), state: 'connecting', version: null }
    streams.set(key, entry)
    entry.stream = createSseStream({
      url: '/api/v1/sync/' + (catalog ? 'catalog-events' : 'events'),
      headers,
      onVersion: (value) => {
        entry.version = value
        for (const fn of entry.listeners) fn({ type: 'version', value })
      },
      onState: (value) => {
        entry.state = value
        for (const fn of entry.listeners) fn({ type: 'state', value })
      },
    })
  }
  entry.listeners.add(listener)
  listener({ type: 'state', value: entry.state })
  if (entry.version) listener({ type: 'version', value: entry.version })
  return () => {
    entry.listeners.delete(listener)
    if (!entry.listeners.size) {
      entry.stream.stop()
      streams.delete(key)
    }
  }
}
