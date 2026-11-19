import http from '../utils/http'
const data = (request) => request.then((response) => response.data.data)
export const getPackages = (admin = false) =>
  data(http.get(admin ? '/admin/service-packages' : '/service-packages'))
export const getPackage = (id) => data(http.get('/service-packages/' + id))
export const savePackage = (id, body) =>
  data(
    id
      ? http.patch('/admin/service-packages/' + id, body)
      : http.post('/admin/service-packages', body),
  )
export const getVenues = () => data(http.get('/admin/venues'))
export const saveVenue = (id, body) =>
  data(id ? http.patch('/admin/venues/' + id, body) : http.post('/admin/venues', body))
export const getPhotographers = () => data(http.get('/admin/photographers'))
export const setBookable = (id, bookable) =>
  data(http.patch('/admin/photographers/' + id, { bookable }))
export const getSchedule = (kind, params) => data(http.get('/admin/schedules/' + kind, { params }))
export const saveSchedule = (kind, body) => data(http.post('/admin/schedules/' + kind, body))
export const getAvailability = (params) => data(http.get('/availability', { params }))
export const createAppointment = (body) => data(http.post('/appointments', body))
export const getAppointments = (params) => data(http.get('/appointments', { params }))
export const getAppointment = (id) => data(http.get('/appointments/' + id))
export const getWeeklySchedule = (kind, resourceId) => data(http.get('/admin/weekly-schedules/' + kind, { params: { resourceId } }))
export const saveWeeklySchedule = (kind, body) => data(http.put('/admin/weekly-schedules/' + kind, body))
export const restoreWeeklySchedule = (kind, params) => data(http.delete('/admin/schedules/' + kind, { params }))
