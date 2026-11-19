import { reactive } from 'vue'
import { getCurrentUser, loginAccount, logoutAccount } from '../api/authApi'
import { getPortal, setPortal } from '../utils/portal'
const sessions = reactive({
  customer: { user: null, loaded: false },
  admin: { user: null, loaded: false },
  staff: { user: null, loaded: false },
})
const versions = { customer: 0, admin: 0, staff: 0 }
// Keep successful credentials only in this tab's memory, separately from authentication.
// Logout invalidates the server session but leaves the login form available for reuse.
const loginForms = new Map()
export function rememberedLogin(portal) {
  const saved = loginForms.get(portal)
  return saved ? { ...saved } : { phone: '', password: '' }
}
const active = reactive({ portal: getPortal() })
export const auth = {
  get user() {
    return sessions[active.portal].user
  },
  set user(value) {
    sessions[active.portal].user = value
  },
  get loaded() {
    return sessions[active.portal].loaded
  },
  set loaded(value) {
    sessions[active.portal].loaded = value
  },
}
export function selectPortal(portal) {
  active.portal = portal
  setPortal(portal)
}
export async function loadUser() {
  const portal = active.portal,
    version = ++versions[portal],
    session = sessions[portal]
  try {
    const user = await getCurrentUser(portal)
    if (version === versions[portal]) session.user = user
  } catch (e) {
    if (e.response?.status !== 401) throw e
    if (version === versions[portal]) session.user = null
  } finally {
    if (version === versions[portal]) session.loaded = true
  }
}
export async function login(payload) {
  const portal = active.portal,
    version = ++versions[portal]
  const credentials = { phone: payload.phone, password: payload.password }
  const user = await loginAccount(credentials, portal)
  if (version !== versions[portal] || active.portal !== portal)
    throw Object.assign(new Error('登录入口已切换，请重新登录'), {
      userMessage: '登录入口已切换，请重新登录',
    })
  loginForms.set(portal, credentials)
  sessions[portal].user = user
  sessions[portal].loaded = true
}
export async function logout() {
  const portal = active.portal,
    version = ++versions[portal]
  await logoutAccount(portal)
  if (version === versions[portal]) {
    sessions[portal].user = null
    sessions[portal].loaded = true
  }
}
export function homeFor() {
  return '/' + getPortal()
}

export function invalidateSession(portal) {
  if (!Object.hasOwn(sessions, portal)) return
  versions[portal]++
  sessions[portal].user = null
  sessions[portal].loaded = true
}
