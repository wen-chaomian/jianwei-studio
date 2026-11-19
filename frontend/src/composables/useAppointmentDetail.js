import { useChangeSync } from './useChangeSync'

import { refundablePayments } from '../utils/workflow'

import { ref, computed, onMounted, watch } from 'vue'

import { useRoute } from 'vue-router'

import { get, post, patch, command } from '../api/operationsApi'

import { auth } from './useAuth'

import { today } from '../utils/bookingFormat'

export function useAppointmentDetail() {
  const requestEditing = ref(false)
  const route = useRoute(),
    item = ref(null),
    activity = ref(null),
    error = ref(''),
    notice = ref(''),
    busy = ref(false),
    mode = ref(''),
    reason = ref(''),
    amount = ref(''),
    method = ref('WECHAT'),
    original = ref(null),
    requestType = ref('RESCHEDULE'),
    date = ref(today()),
    photographer = ref(''),
    slots = ref([]),
    selected = ref(''),
    notes = ref(''),
    slotBusy = ref(false)
  let token = command(),
    slotSequence = 0,
    loadSequence = 0
  const customer = computed(() => auth.user?.roles.includes('CUSTOMER')),
    desk = computed(() => auth.user?.roles.some((r) => ['ADMIN', 'RECEPTIONIST'].includes(r))),
    shooter = computed(() => auth.user?.roles.some((r) => ['ADMIN', 'PHOTOGRAPHER'].includes(r))),
    paid = computed(() => refundablePayments(activity.value?.payments || []))
  const actions = {
    cancel: '取消预约',
    'check-in': '登记到店',
    start: '开始服务',
    complete: '完成服务',
    'mark-no-show': '标记爽约',
    payment: '登记收款',
    refund: '登记退款',
    reschedule: '修改拍摄安排',
    request: '提交人工申请',
  }
  const methods = { CASH: '现金', WECHAT: '微信', ALIPAY: '支付宝', BANK: '银行转账' }
  async function load() {
    const seq = ++loadSequence,
      id = route.params.id
    error.value = ''
    item.value = null
    activity.value = null
    try {
      const [a, b] = await Promise.all([
        get('/appointments/' + id),
        get('/appointments/' + id + '/activity'),
      ])
      if (seq !== loadSequence || id !== route.params.id) return
      item.value = a
      activity.value = b
      notes.value = b.service?.notes || ''
    } catch (e) {
      if (seq === loadSequence && id === route.params.id) error.value = e.userMessage
    }
  }
  function open(action, p = null) {
    if (busy.value) return
    slotSequence++
    slotBusy.value = false
    mode.value = action
    original.value = p || (action === 'refund' ? paid.value[0] || null : null)
    reason.value = ''
    amount.value = ''
    selected.value = ''
    notice.value = ''
    error.value = ''
    token = command()
    if (action === 'request')
      requestType.value = item.value.status === 'CONFIRMED' ? 'RESCHEDULE' : 'OTHER'
    if (action === 'reschedule') {
      photographer.value = activity.value.photographers?.[0]?.id || ''
      date.value = item.value.startTime.slice(0, 10)
      loadSlots()
    }
  }
  watch(
    [reason, amount, method, original, selected, photographer, date, requestType],
    () => (token = command()),
  )
  async function loadSlots() {
    const seq = ++slotSequence
    selected.value = ''
    slots.value = []
    if (mode.value !== 'reschedule' || !photographer.value || !date.value) return
    slotBusy.value = true
    try {
      const data = await get('/appointments/' + route.params.id + '/reschedule-options', {
        photographerId: photographer.value,
        date: date.value,
      })
      if (seq === slotSequence) slots.value = data
    } catch (e) {
      if (seq === slotSequence) error.value = e.userMessage
    } finally {
      if (seq === slotSequence) slotBusy.value = false
    }
  }
  watch([date, photographer], loadSlots)
  watch(
    () => route.params.id,
    () => {
      requestEditing.value = false
      slotSequence++
      slotBusy.value = false
      mode.value = ''
      notice.value = ''
      load()
    },
  )
  onMounted(async () => {
    await load()
    if (!item.value || !activity.value) return
    const action = route.query.action
    const allowed =
      (action === 'check-in' &&
        desk.value &&
        !activity.value.pendingChange &&
        item.value.status === 'CONFIRMED') ||
      (action === 'start' && shooter.value && item.value.status === 'CHECKED_IN') ||
      (action === 'complete' && shooter.value && item.value.status === 'IN_PROGRESS')
    if (allowed) open(action)
  })
  async function submit() {
    if (busy.value || !item.value || !activity.value) return
    const pageId = route.params.id
    busy.value = true
    error.value = ''
    try {
      const id = item.value.id,
        base = {
          requestId: token,
          version: activity.value.version,
          reason: ['check-in', 'start', 'complete'].includes(mode.value) ? '' : reason.value,
        }
      if (mode.value === 'reschedule')
        await post('/appointments/' + id + '/reschedule', {
          ...base,
          photographerId: Number(photographer.value),
          startTime: selected.value,
        })
      else if (mode.value === 'payment' || mode.value === 'refund')
        await post(
          mode.value === 'payment'
            ? '/appointments/' + id + '/payments'
            : '/payments/' + original.value.id + '/refunds',
          {
            requestId: token,
            amount: Number(amount.value),
            method: method.value,
            reason: mode.value === 'payment' ? '' : reason.value,
          },
        )
      else if (mode.value === 'request')
        await post('/customer-requests', {
          appointmentId: id,
          type: requestType.value,
          content: reason.value,
          requestId: token,
        })
      else await post('/appointments/' + id + '/' + mode.value, base)
      if (pageId === route.params.id) {
        mode.value = ''
        await load()
        notice.value = '办理成功，相关记录已更新。'
      }
    } catch (e) {
      if (pageId === route.params.id) error.value = e.userMessage
    } finally {
      busy.value = false
    }
  }
  async function saveNotes() {
    if (busy.value || !item.value) return
    const pageId = route.params.id
    busy.value = true
    try {
      await patch('/appointments/' + item.value.id + '/service-record', { notes: notes.value })
      if (pageId === route.params.id) {
        await load()
        notice.value = '服务备注已保存'
      }
    } catch (e) {
      if (pageId === route.params.id) error.value = e.userMessage
    } finally {
      busy.value = false
    }
  }
  useChangeSync({
    fetchData: () =>
      Promise.all([
        get('/appointments/' + route.params.id),
        get('/appointments/' + route.params.id + '/activity'),
      ]),
    current: () => [item.value, activity.value],
    apply: ([a, b]) => {
      item.value = a
      activity.value = b
      notes.value = b.service?.notes || ''
      error.value = ''
    },
    key: () => [route.params.id, loadSequence],
    paused: () =>
      !item.value ||
      busy.value ||
      Boolean(mode.value) ||
      requestEditing.value ||
      notes.value !== (activity.value?.service?.notes || ''),
  })
  return {
    requestEditing,
    item,
    activity,
    error,
    notice,
    busy,
    mode,
    reason,
    amount,
    method,
    original,
    requestType,
    date,
    photographer,
    slots,
    selected,
    notes,
    slotBusy,
    customer,
    desk,
    shooter,
    paid,
    actions,
    methods,
    load,
    open,
    loadSlots,
    submit,
    saveNotes,
  }
}
