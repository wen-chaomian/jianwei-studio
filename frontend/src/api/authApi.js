import http, { refreshCsrf, beginLogin } from '../utils/http'
import { sessionHeaders, rememberSession, forgetSession } from '../utils/portalSession'
import { getPortal } from '../utils/portal'
export const getCurrentUser = (portal = getPortal()) =>
  http.get('/users/me', { headers: sessionHeaders(portal) }).then((r) => r.data.data)
export const registerAccount = (payload) =>
  http.post('/auth/register', payload).then((r) => r.data.data)
export const loginAccount = (payload, portal = getPortal()) => {
  beginLogin(portal)
  const headers = sessionHeaders(portal)
  return http.post('/auth/login', payload, { headers }).then((r) => {
    rememberSession(portal, headers['X-Studio-Session-Scope'])
    return r.data.data
  })
}
export async function logoutAccount(portal = getPortal()) {
  const headers = sessionHeaders(portal)
  await http.post('/auth/logout', null, { headers })
  forgetSession(portal, headers['X-Studio-Session-Scope'])
  await refreshCsrf(portal, headers['X-Studio-Session-Scope'])
}
