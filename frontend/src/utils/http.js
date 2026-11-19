import axios from 'axios'
import { getPortal } from './portal'
import { getSessionScope, renewSessionScope } from './portalSession'
const http = axios.create({ baseURL: '/api/v1', timeout: 10000, withCredentials: true })
const tokens = new Map()
const scopeHeader = 'X-Studio-Session-Scope'
const tokenKey = (portal, scope) => `${portal}:${scope || ''}`
export function beginLogin(portal) {
  tokens.delete(tokenKey(portal, getSessionScope(portal)))
  renewSessionScope(portal)
}
export async function refreshCsrf(portal = getPortal(), scope = getSessionScope(portal)) {
  const headers = { 'X-Studio-Portal': portal }
  if (scope) headers[scopeHeader] = scope
  const token = (await http.get('/auth/csrf', { headers })).data.data
  tokens.set(tokenKey(portal, scope), token)
  return token
}
http.interceptors.request.use(async (config) => {
  const portal = config.headers['X-Studio-Portal'] || getPortal()
  const scope = config.headers[scopeHeader] || getSessionScope(portal)
  config.headers['X-Studio-Portal'] = portal
  if (scope) config.headers[scopeHeader] = scope
  if (!['get', 'head', 'options'].includes(config.method)) {
    const csrf = tokens.get(tokenKey(portal, scope)) || (await refreshCsrf(portal, scope))
    config.headers[csrf.headerName] = csrf.token
  }
  return config
})
function assertCurrent(config) {
  const portal = config.headers['X-Studio-Portal']
  if (config.headers[scopeHeader] !== getSessionScope(portal)) {
    const error = new axios.CanceledError('登录状态已切换，请重新操作')
    error.userMessage = error.message
    throw error
  }
}
http.interceptors.response.use(
  (response) => {
    assertCurrent(response.config)
    return response
  },
  (error) => {
    if (error.config) assertCurrent(error.config)
    error.userMessage = error.response?.data?.message || '连接失败，请检查服务后重试'
    return Promise.reject(error)
  },
)
export default http
