import { useChangeSync } from './useChangeSync'

import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

import { auth } from './useAuth'

import { getAppointments } from '../api/bookingApi'

import { today } from '../utils/bookingFormat'

export function useStaffDay() {
  const selectedDate = ref(today()),
    clock = ref(new Date()),
    items = ref([])
  const loading = ref(false),
    error = ref(''),
    creating = ref(false)
  const receptionist = computed(() => auth.user?.roles.includes('RECEPTIONIST'))
  const photographer = computed(() => auth.user?.roles.includes('PHOTOGRAPHER'))
  const dayLabel = computed(() => (selectedDate.value === today() ? '今天' : '当天'))
  const greeting = computed(() => {
    const hour = Number(
      new Intl.DateTimeFormat('en-GB', {
        timeZone: 'Asia/Shanghai',
        hour: '2-digit',
        hourCycle: 'h23',
      }).format(clock.value),
    )
    return hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
  })
  const dateLabel = computed(() => {
    const date = new Date(selectedDate.value + 'T12:00:00+08:00')
    return (
      new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', month: 'long' }).format(date) +
      ' / ' +
      new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', weekday: 'short' }).format(date)
    )
  })
  const appointments = computed(() =>
    [...items.value].sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id),
  )
  const activeCount = computed(
    () => items.value.filter((a) => !['CANCELLED', 'NO_SHOW'].includes(a.status)).length,
  )
  const focusId = computed(() => {
    for (const status of ['IN_PROGRESS', 'CHECKED_IN', 'CONFIRMED']) {
      const appointment = appointments.value.find((a) => a.status === status)
      if (appointment) return appointment.id
    }
    return null
  })
  const timeText = (value) => value?.slice(11, 16) || '—'
  const duration = (appointment) =>
    Math.round((new Date(appointment.shootEndTime) - new Date(appointment.startTime)) / 60000)
  function actionFor(appointment) {
    if (photographer.value && appointment.status === 'CHECKED_IN')
      return { label: '开始拍摄', action: 'start' }
    if (photographer.value && appointment.status === 'IN_PROGRESS')
      return { label: '完成拍摄', action: 'complete' }
    if (receptionist.value && appointment.pendingRequestType)
      return { label: '处理申请', action: 'handle-request' }
    if (receptionist.value && appointment.status === 'CONFIRMED')
      return { label: '登记到店', action: 'check-in' }
    return { label: '查看详情', action: '' }
  }
  function destination(appointment) {
    const action = actionFor(appointment).action
    return { path: '/staff/appointments/' + appointment.id, query: action ? { action } : {} }
  }

  let sequence = 0,
    timer
  async function load() {
    const current = ++sequence,
      date = selectedDate.value
    loading.value = true
    error.value = ''
    items.value = []
    try {
      const first = await getAppointments({ page: 1, size: 100, date })
      const results = [...first.items]
      for (let page = 2; page <= Math.ceil(first.total / 100); page++) {
        if (current !== sequence) return
        const next = await getAppointments({ page, size: 100, date })
        results.push(...next.items)
      }
      if (current === sequence) items.value = [...new Map(results.map((a) => [a.id, a])).values()]
    } catch (e) {
      if (current === sequence) error.value = e.userMessage || '加载失败，请重试。'
    } finally {
      if (current === sequence) loading.value = false
    }
  }
  watch(selectedDate, (value) => {
    if (!value) {
      selectedDate.value = today()
      return
    }
    load()
  })
  onMounted(() => {
    load()
    let previousDay = today()
    timer = setInterval(() => {
      clock.value = new Date()
      const currentDay = today()
      if (currentDay !== previousDay && selectedDate.value === previousDay)
        selectedDate.value = currentDay
      previousDay = currentDay
    }, 60000)
  })
  onUnmounted(() => {
    sequence++
    clearInterval(timer)
  })
  useChangeSync({
    fetchData: async () => {
      const date = selectedDate.value
      const first = await getAppointments({ page: 1, size: 100, date })
      const results = [...first.items]
      for (let page = 2; page <= Math.ceil(first.total / 100); page++) {
        const next = await getAppointments({ page, size: 100, date })
        results.push(...next.items)
      }
      return [...new Map(results.map((a) => [a.id, a])).values()]
    },
    current: () => items.value,
    apply: (value) => {
      items.value = value
      error.value = ''
    },
    key: () => [sequence, selectedDate.value],
    paused: () => loading.value || creating.value,
  })
  return {
    selectedDate,
    clock,
    items,
    loading,
    error,
    creating,
    receptionist,
    photographer,
    dayLabel,
    greeting,
    dateLabel,
    appointments,
    activeCount,
    focusId,
    timeText,
    duration,
    actionFor,
    destination,
    load,
  }
}
