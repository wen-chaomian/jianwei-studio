import http from '../utils/http'
import { sessionHeaders } from '../utils/portalSession'
const get = (portal, path) =>
  http
    .get('/agent' + path, { headers: sessionHeaders(portal), timeout: 20000 })
    .then((r) => r.data.data)
const post = (portal, path, body) =>
  http
    .post('/agent' + path, body, { headers: sessionHeaders(portal), timeout: 20000 })
    .then((r) => r.data.data)
export const agentStatus = (portal) => get(portal, '/status')
export const conversations = (portal, offset = 0) => get(portal, '/conversations?offset=' + offset)
export const newConversation = (portal, request_id) =>
  post(portal, '/conversations', { request_id })
export const conversation = (portal, id, before = '9223372036854775807') =>
  get(portal, `/conversations/${id}?before=${before}`)
export const sendMessage = (portal, id, payload) =>
  post(portal, `/conversations/${id}/messages`, payload)
export const modelConfig = () => get('admin', '/model-config')
export const availableModels = () => get('admin', '/models')
export const saveModelConfig = (body) =>
  http
    .put('/agent/model-config', body, { headers: sessionHeaders('admin'), timeout: 20000 })
    .then((r) => r.data.data)

export const renameConversation = (portal, id, title) => http.patch(`/agent/conversations/${id}`, { title }, { headers: sessionHeaders(portal) }).then(r => r.data.data)
export const deleteConversation = (portal, id) => http.delete(`/agent/conversations/${id}`, { headers: sessionHeaders(portal) }).then(r => r.data.data)
export const stopRun = (portal, id) => post(portal, `/runs/${id}/stop`)

export const copyModelKey = () => post('admin', '/model-config/key')
