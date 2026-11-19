<script setup>
import { useChangeSync } from '../../composables/useChangeSync'
import { ref, watch, onMounted, computed } from 'vue'
import { auth } from '../../composables/useAuth'
import { get } from '../../api/operationsApi'
import { today } from '../../utils/bookingFormat'
import AppointmentList from '../../components/AppointmentList.vue'
const date = ref(today()),
  data = ref(null),
  week = ref([]),
  error = ref(''),
  photographer = computed(() => auth.user?.roles.includes('PHOTOGRAPHER'))
async function load() {
  if (!photographer.value) return
  try {
    ;[data.value, week.value] = await Promise.all([get('/staff/my-schedule', { date: date.value }),get('/staff/my-weekly-schedule')])
  } catch (e) {
    error.value = e.userMessage
  }
}
watch(date, load)
onMounted(load)
useChangeSync({
  fetchData: async () => ({day:await get('/staff/my-schedule', { date: date.value }),week:await get('/staff/my-weekly-schedule')}),
  current: () => ({day:data.value,week:week.value}),
  apply: (value) => {
    data.value = value.day
    week.value = value.week
    error.value = ''
  },
  key: () => date.value,
  paused: () => !photographer.value,
})
</script>
<template>
  <section class="panel" v-if="photographer"><div class="section-title"><h2>固定周安排</h2><span class="subtle">特殊日期以当天实际安排为准</span></div><div class="my-week"><div v-for="d in week" :key="d.dayOfWeek"><strong>{{ ['周一','周二','周三','周四','周五','周六','周日'][d.dayOfWeek-1] }}</strong><p v-if="!d.enabled" class="subtle">休息</p><p v-for="p in d.periods" v-else>{{p.startTime.slice(0,5)}}–{{p.endTime.slice(0,5)}}</p></div></div></section>
  <section class="panel" v-if="photographer">
    <div class="section-title">
      <h2>当天实际安排</h2>
      <input aria-label="当天安排日期" type="date" v-model="date" />
    </div>
    <p class="error-message" v-if="error">{{ error }}</p>
    <div v-if="data"><p class="subtle">{{ data.source==='DATE'?'特殊日期安排':data.source==='WEEKLY'?'按固定周安排':'尚未安排' }}</p>
      <p v-if="!data.enabled">当天未安排拍摄班次。</p>
      <div class="slot-grid" v-else>
        <span class="phase-badge" v-for="p in data.periods"
          >{{ p.startTime.slice(0, 5) }} — {{ p.endTime.slice(0, 5) }}</span
        >
      </div>
    </div>
  </section>
  <AppointmentList />
</template>

<style scoped>.my-week{display:grid;grid-template-columns:repeat(7,minmax(110px,1fr));gap:14px;overflow:auto}.my-week>div{padding:14px 10px;background:var(--bg,#f3f7f7);border-radius:8px}.my-week p{font-size:14px;margin:8px 0 0}</style>
