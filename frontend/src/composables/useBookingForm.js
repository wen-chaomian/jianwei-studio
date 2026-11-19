import { useChangeSync } from './useChangeSync'

import { ref, computed, onMounted, watch } from 'vue'

import { useRouter } from 'vue-router'

import * as api from '../api/bookingApi'

import { auth } from './useAuth'

import { get } from '../api/operationsApi'

import { today } from '../utils/bookingFormat'

export function useBookingForm(props, emit) {
  const rules = ref(null)
  const packages = ref([]),
    packageId = ref(props.packageId || ''),
    photographerId = ref(''),
    date = ref(today()),
    slots = ref([]),
    selected = ref(''),
    remark = ref(''),
    customerName = ref(''),
    customerPhone = ref(''),
    confirming = ref(false),
    busy = ref(false),
    loading = ref(false),
    error = ref(''),
    result = ref(null)
  const router = useRouter(),
    selectedPackage = computed(() => packages.value.find((p) => p.id === Number(packageId.value)))
  let requestId = crypto.randomUUID(),
    sequence = 0
  watch(packageId, () => {
    photographerId.value = selectedPackage.value?.photographers[0]?.id || ''
  })
  watch([packageId, photographerId, date], loadSlots)
  watch([selected, remark, customerName, customerPhone], () => {
    requestId = crypto.randomUUID()
    confirming.value = false
  })
  async function loadSlots() {
    const seq = ++sequence
    selected.value = ''
    confirming.value = false
    slots.value = []
    error.value = ''
    if (!packageId.value || !photographerId.value || !date.value) return
    loading.value = true
    try {
      const data = await api.getAvailability({
        packageId: packageId.value,
        photographerId: photographerId.value,
        date: date.value,
      })
      if (seq === sequence) slots.value = data
    } catch (e) {
      if (seq === sequence) error.value = e.userMessage
    } finally {
      if (seq === sequence) loading.value = false
    }
  }
  function confirm() {
    if (!auth.user) {
      router.push({
        path: '/customer/login',
        query: { redirect: '/customer/packages/' + packageId.value },
      })
      return
    }
    confirming.value = true
  }
  async function submit() {
    busy.value = true
    error.value = ''
    try {
      result.value = await api.createAppointment({
        packageId: Number(packageId.value),
        photographerId: Number(photographerId.value),
        startTime: selected.value,
        requestId,
        remark: remark.value,
        ...(props.staff
          ? { customerName: customerName.value, customerPhone: customerPhone.value }
          : {}),
      })
      emit('created', result.value)
    } catch (e) {
      error.value = e.userMessage
      confirming.value = false
    } finally {
      busy.value = false
    }
  }
  onMounted(async () => {
    try {
      rules.value = await get('/booking-rule')
      packages.value = await api.getPackages()
      if (!packageId.value) packageId.value = packages.value[0]?.id || ''
      photographerId.value = selectedPackage.value?.photographers[0]?.id || ''
    } catch (e) {
      error.value = e.userMessage
    }
  })
  useChangeSync({
    publicCatalog: true,
    fetchData: () =>
      Promise.all([
        get('/booking-rule'),
        api.getPackages(),
        api.getAvailability({
          packageId: packageId.value,
          photographerId: photographerId.value,
          date: date.value,
        }),
      ]),
    current: () => [rules.value, packages.value, slots.value],
    apply: ([r, p, s]) => {
      rules.value = r
      packages.value = p
      slots.value = s
      if (selected.value && !s.some((slot) => slot.startTime === selected.value)) {
        selected.value = ''
        error.value = '所选时段已不可预约，请重新选择。'
      }
    },
    key: () => [packageId.value, photographerId.value, date.value, sequence],
    paused: () =>
      busy.value ||
      loading.value ||
      confirming.value ||
      Boolean(result.value) ||
      !packageId.value ||
      !photographerId.value ||
      !date.value,
  })
  return {
    rules,
    packages,
    packageId,
    photographerId,
    date,
    slots,
    selected,
    remark,
    customerName,
    customerPhone,
    confirming,
    busy,
    loading,
    error,
    result,
    selectedPackage,
    loadSlots,
    confirm,
    submit,
  }
}
