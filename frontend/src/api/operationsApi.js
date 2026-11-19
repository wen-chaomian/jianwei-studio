import http from '../utils/http'
const unwrap = (p) => p.then((r) => r.data.data)
export const get = (url, params) => unwrap(http.get(url, { params }))
export const post = (url, data) => unwrap(http.post(url, data))
export const patch = (url, data) => unwrap(http.patch(url, data))
export const command = () => crypto.randomUUID()
