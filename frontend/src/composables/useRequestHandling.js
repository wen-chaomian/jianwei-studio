import { ref, computed, watch, onMounted } from 'vue'

import { useRoute } from 'vue-router'

import { get, patch, command } from '../api/operationsApi'

import { today } from '../utils/bookingFormat'

export function useRequestHandling(props, emit) {
  const route = useRoute()
  const selected = ref(null),
    decision = ref('RESOLVED'),
    result = ref(''),
    photographer = ref(''),
    date = ref(today()),
    slots = ref([]),
    slot = ref(''),
    busy = ref(false),
    loading = ref(false),
    error = ref('')
  watch(selected, (value) => emit('editing', Boolean(value)), { flush: 'sync' })
  let sequence = 0,
    requestId = command()
  const needsExplanation = computed(
    () =>
      decision.value === 'CLOSED' ||
      (decision.value === 'RESOLVED' && selected.value?.type === 'OTHER'),
  )
  const changing = computed(
    () => selected.value?.type === 'RESCHEDULE' && decision.value === 'RESOLVED',
  )
  const approveLabel = computed(() =>
    selected.value?.type === 'CANCEL'
      ? '同意取消预约'
      : selected.value?.type === 'RESCHEDULE'
        ? '同意并确认改期'
        : '完成处理',
  )
  function edit(q) {
    if (busy.value) return
    selected.value = q
    decision.value = 'RESOLVED'
    result.value = ''
    photographer.value = props.activity.photographers?.[0]?.id || ''
    date.value = props.item.startTime.slice(0, 10)
    error.value = ''
    requestId = command()
    loadSlots()
  }
  async function loadSlots() {
    const seq = ++sequence
    slots.value = []
    slot.value = ''
    loading.value = false
    if (!changing.value || !photographer.value || !date.value) return
    loading.value = true
    try {
      const data = await get('/appointments/' + props.item.id + '/reschedule-options', {
        photographerId: photographer.value,
        date: date.value,
      })
      if (seq === sequence) slots.value = data
    } catch (e) {
      if (seq === sequence) error.value = e.userMessage
    } finally {
      if (seq === sequence) loading.value = false
    }
  }
  watch([date, photographer, changing], loadSlots)
  watch([decision, result, photographer, date, slot], () => (requestId = command()))
  async function save() {
    if (busy.value || !selected.value) return
    busy.value = true
    error.value = ''
    try {
      await patch('/customer-requests/' + selected.value.id, {
        status: decision.value,
        result: needsExplanation.value ? result.value.trim() : '',
        version: selected.value.version,
        requestId,
        appointmentVersion: props.activity.version,
        ...(changing.value
          ? { photographerId: Number(photographer.value), startTime: slot.value }
          : {}),
      })
      selected.value = null
      emit('updated')
    } catch (e) {
      error.value = e.userMessage
    } finally {
      busy.value = false
    }
  }
  onMounted(() => {
    if (!props.editable) return
    const pending = props.activity.requests.filter((q) =>
      ['PENDING', 'PROCESSING'].includes(q.status),
    )
    const q =
      pending.find((q) => String(q.id) === String(route.query.request)) ||
      (route.query.action === 'handle-request'
        ? pending.find((q) => q.type !== 'OTHER') || pending[0]
        : null)
    if (q) edit(q)
  })
  return {
    selected,
    decision,
    result,
    photographer,
    date,
    slots,
    slot,
    busy,
    loading,
    error,
    needsExplanation,
    changing,
    approveLabel,
    edit,
    loadSlots,
    save,
  }
}
