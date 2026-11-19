export const requestLabels = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已办理',
  CLOSED: '已结束',
  CANCEL: '取消申请',
  RESCHEDULE: '改期申请',
  OTHER: '其他问题',
}
export const notificationTitle = (value) =>
  ({
    reschedule: '预约已改期',
    cancel: '预约已取消',
    'check-in': '已登记到店',
    start: '拍摄已开始',
    complete: '拍摄已完成',
    payment: '已登记收款',
    refund: '已登记退款',
    'mark-no-show': '已标记爽约',
    'service-notes': '服务备注已更新',
  })[value] || value
export const readableContent = (value) =>
  (value || '').replace(/(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)?/g, '$1 $2')
export function refundablePayments(payments = []) {
  return payments
    .filter((p) => p.type === 'PAYMENT')
    .map((p) => ({
      ...p,
      remainingCents:
        Math.round(Number(p.amount) * 100) -
        payments
          .filter((r) => r.type === 'REFUND' && Number(r.originalId) === Number(p.id))
          .reduce((n, r) => n + Math.round(Number(r.amount) * 100), 0),
    }))
    .filter((p) => p.remainingCents > 0)
}
