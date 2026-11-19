<script setup>
import { useChangeSync } from '../composables/useChangeSync'

import { ref, onMounted, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getAppointments } from '../api/bookingApi'
import { getPortal } from '../utils/portal'
import { auth } from '../composables/useAuth'
import { today, dateText, money, statusText } from '../utils/bookingFormat'
import BookingForm from './BookingForm.vue'
const props = defineProps({ todayOnly: Boolean }),
  route = useRoute(),
  data = ref({ items: [], total: 0 }),
  page = ref(1),
  date = ref(props.todayOnly ? today() : ''),
  error = ref(''),
  loading = ref(false),
  creating = ref(false)
const area = computed(() => getPortal()),
  canCreate = computed(
    () =>
      area.value !== 'customer' &&
      auth.user?.roles.some((x) => ['ADMIN', 'RECEPTIONIST'].includes(x)),
  )
let sequence = 0
async function load() {
  const seq = ++sequence
  loading.value = true
  error.value = ''
  try {
    const result = await getAppointments({
      page: page.value,
      size: 10,
      ...(date.value ? { date: date.value } : {}),
    })
    if (seq === sequence) data.value = result
  } catch (e) {
    if (seq === sequence) error.value = e.userMessage
  } finally {
    if (seq === sequence) loading.value = false
  }
}
watch(
  () => route.path,
  () => {
    date.value = props.todayOnly ? today() : ''
    page.value = 1
    creating.value = false
    load()
  },
)
watch(date, () => {
  page.value = 1
  load()
})
watch(page, load)
onMounted(load)
useChangeSync({
  fetchData: () =>
    getAppointments({ page: page.value, size: 10, ...(date.value ? { date: date.value } : {}) }),
  current: () => data.value,
  apply: (value) => {
    data.value = value
    error.value = ''
  },
  key: () => [sequence, route.path, page.value, date.value],
  paused: () => loading.value || creating.value,
})
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">APPOINTMENTS</span>
      <h1>
        {{
          todayOnly
            ? '今日预约'
            : area === 'customer'
              ? '我的预约'
              : area === 'staff'
                ? '我的工作安排'
                : '预约管理'
        }}
      </h1>
      <p>
        {{
          area === 'customer'
            ? '查看已确认的拍摄安排。'
            : '预约信息实时同步，摄影师仅可查看本人任务。'
        }}
      </p>
    </div>
    <button v-if="canCreate" class="primary-button" @click="creating = !creating">
      {{ creating ? '收起' : '＋ 代客预约' }}
    </button>
  </div>
  <BookingForm v-if="creating" staff @created="load" />
  <section class="panel">
    <div class="list-toolbar">
      <label>预约日期 <input type="date" v-model="date" /></label
      ><button class="text-button" @click="date = ''">全部日期</button>
    </div>
    <p role="alert" class="error-message" v-if="error">{{ error }}</p>
    <p v-if="loading">正在加载…</p>
    <div v-else-if="!data.items.length" class="empty-state">暂无预约记录。</div>
    <div class="appointment-cards">
      <RouterLink
        v-for="a in data.items"
        :key="a.id"
        :to="'/' + area + '/appointments/' + a.id"
        class="appointment-card"
        ><div>
          <small>{{ a.appointmentNo }}</small>
          <h3>{{ a.packageName }}</h3>
          <p>{{ dateText(a.startTime) }} · {{ a.photographerName }} · {{ a.venueName }}</p>
          <small v-if="area !== 'customer'">{{ a.customerName }} · {{ a.customerPhone }}</small>
        </div>
        <div>
          <span class="phase-badge">{{
            a.pendingRequestType
              ? a.pendingRequestType === 'CANCEL'
                ? '取消申请待处理'
                : '改期申请待处理'
              : statusText[a.status] || a.status
          }}</span>
          <p>¥{{ money(a.amount) }} · 到店付款</p>
          <small>查看详情 ↗</small>
        </div></RouterLink
      >
    </div>
    <div class="pagination">
      <span>共 {{ data.total }} 条</span
      ><button class="outline-button" :disabled="page === 1 || loading" @click="page--">
        上一页</button
      ><span>{{ page }}</span
      ><button
        class="outline-button"
        :disabled="page * 10 >= data.total || loading"
        @click="page++"
      >
        下一页
      </button>
    </div>
  </section>
</template>
