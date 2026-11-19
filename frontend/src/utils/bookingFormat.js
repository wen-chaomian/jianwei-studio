export const dateText = (value) => (value ? value.replace('T', ' ').slice(0, 16) : '—')
export const money = (value) => Number(value).toFixed(2)
export function today() {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date())
}
export const statusText = {
  CONFIRMED: '待到店',
  CHECKED_IN: '已到店',
  IN_PROGRESS: '服务中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  NO_SHOW: '已爽约',
}
