const validScope = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/
const portals = new Set(['customer', 'staff', 'admin'])
const scopes = new Map()
function storageKey(portal) {
  if (!portals.has(portal)) throw new Error('无效的登录入口')
  return `shiguang.${portal}.sessionScope`
}
export function getSessionScope(portal) {
  const key = storageKey(portal)
  const scope =
    scopes.get(portal) || window.sessionStorage.getItem(key) || window.localStorage?.getItem(key)
  if (!validScope.test(scope || '')) return renewSessionScope(portal)
  window.sessionStorage.setItem(key, scope)
  scopes.set(portal, scope)
  return scope
}
export function renewSessionScope(portal) {
  const key = storageKey(portal),
    scope = crypto.randomUUID()
  window.sessionStorage.setItem(key, scope)
  scopes.set(portal, scope)
  if (window.dispatchEvent) window.dispatchEvent(new Event('studio-session-change'))
  return scope
}
export function sessionHeaders(portal) {
  return { 'X-Studio-Portal': portal, 'X-Studio-Session-Scope': getSessionScope(portal) }
}

// Only successful logins become the default for newly opened tabs.
// Existing tabs retain their own sessionStorage scope when another account logs in.
export function rememberSession(portal, scope) {
  if (scope === getSessionScope(portal)) window.localStorage?.setItem(storageKey(portal), scope)
}
export function forgetSession(portal, scope) {
  const key = storageKey(portal)
  if (window.localStorage?.getItem(key) === scope) window.localStorage.removeItem(key)
  try { window.localStorage?.setItem(`shiguang.${portal}.logout`, JSON.stringify({ scope, nonce: crypto.randomUUID() })) } catch {}
  if (window.dispatchEvent) window.dispatchEvent(new Event('studio-session-change'))
}

// Tabs restored from the same remembered login share its revocation, not another account's.
export function watchSessionLogout(onLogout) {
  const handle = (event) => {
    const portal = /^shiguang\.(customer|staff|admin)\.logout$/.exec(event.key || '')?.[1]
    if (!portal || !event.newValue) return
    let scope
    try { scope = JSON.parse(event.newValue).scope } catch { return }
    if (!validScope.test(scope || '')) return
    const current = scopes.get(portal) || window.sessionStorage.getItem(storageKey(portal))
    if (current !== scope) return
    renewSessionScope(portal)
    onLogout(portal)
  }
  window.addEventListener('storage', handle)
  return () => window.removeEventListener('storage', handle)
}
