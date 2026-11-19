export const portalRoles = {
  customer: ['CUSTOMER'],
  admin: ['ADMIN'],
  staff: ['RECEPTIONIST', 'PHOTOGRAPHER'],
}
export function portalFor(path) {
  return path.split('/')[1] in portalRoles ? path.split('/')[1] : 'customer'
}
let activePortal = portalFor(window.location.pathname)
export const getPortal = () => activePortal
export function setPortal(portal) {
  if (activePortal === portal) return
  activePortal = portal
  if (window.dispatchEvent) window.dispatchEvent(new Event('studio-session-change'))
}
