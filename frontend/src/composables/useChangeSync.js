import { onMounted, onUnmounted } from 'vue'
import { get } from '../api/operationsApi'
import { createChangeSync } from '../utils/changeSync'
import { getPortal } from '../utils/portal'
import { getSessionScope } from '../utils/portalSession'
import { subscribeSync } from '../utils/syncStream'
export function useChangeSync(options) {
  const available = () => document.visibilityState === 'visible' && navigator.onLine
  let retryAt = 0,
    failures = 0,
    pending = true,
    eventSequence = 0,
    busy = false,
    last = 0,
    state = 'closed',
    unsubscribe,
    identity = '',
    mounted = false
  const context = () =>
    JSON.stringify([getPortal(), getSessionScope(getPortal()), Boolean(options.publicCatalog)])
  const sync = createChangeSync({
    ...options,
    onSuccess: () => {
      failures = 0
      retryAt = 0
      options.onSuccess?.()
    },
    onError: (error) => {
      retryAt = Date.now() + Math.min(60000, 5000 * 2 ** Math.min(++failures, 4))
      pending = true
      options.onError?.(error)
    },
    readVersion: () => get(options.publicCatalog ? '/sync/catalog' : '/sync/version'),
    key: () => JSON.stringify([context(), options.key?.()]),
    paused: () => !available() || Boolean(options.paused?.()),
  })
  function disconnect() {
    unsubscribe?.()
    unsubscribe = undefined
    state = 'closed'
  }
  function ensureStream() {
    if (!mounted || !available()) {
      disconnect()
      return
    }
    const next = context()
    if (unsubscribe && next === identity) return
    disconnect()
    identity = next
    unsubscribe = subscribeSync(getPortal(), Boolean(options.publicCatalog), (event) => {
      if (!mounted || identity !== next) return
      if (event.type === 'version') {
        eventSequence++
        pending = true
        void check()
      } else {
        state = event.value
        if (state === 'unauthorized') {
          pending = true
          void check()
        }
      }
    })
  }
  async function check() {
    if (!mounted || busy || !available() || options.paused?.() || Date.now() < retryAt) return
    const interval = state === 'connected' ? 120000 : 30000
    if (!pending && Date.now() - last < interval) return
    busy = true
    pending = false
    const observed = eventSequence,
      observedKey = JSON.stringify([context(), options.key?.()])
    last = Date.now()
    try {
      await sync.check()
    } finally {
      busy = false
      if (
        observed !== eventSequence ||
        options.paused?.() ||
        observedKey !== JSON.stringify([context(), options.key?.()])
      )
        pending = true
    }
  }
  const wake = () => {
    eventSequence++
    pending = true
    ensureStream()
    void check()
  }
  const sessionChanged = () => {
    disconnect()
    identity = ''
    wake()
  }
  let timer
  onMounted(() => {
    mounted = true
    ensureStream()
    timer = setInterval(() => {
      ensureStream()
      void check()
    }, 1000)
    window.addEventListener('focus', wake)
    window.addEventListener('online', wake)
    window.addEventListener('offline', wake)
    window.addEventListener('studio-session-change', sessionChanged)
    document.addEventListener('visibilitychange', wake)
  })
  onUnmounted(() => {
    mounted = false
    sync.stop()
    disconnect()
    clearInterval(timer)
    window.removeEventListener('focus', wake)
    window.removeEventListener('online', wake)
    window.removeEventListener('offline', wake)
    window.removeEventListener('studio-session-change', sessionChanged)
    document.removeEventListener('visibilitychange', wake)
  })
  return sync
}
